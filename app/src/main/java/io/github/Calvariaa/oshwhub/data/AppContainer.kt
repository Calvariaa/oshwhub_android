package io.github.Calvariaa.oshwhub.data

import android.content.Context
import io.github.Calvariaa.oshwhub.BuildConfig
import io.github.Calvariaa.oshwhub.data.model.Paged
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.model.UserInfo
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class AppContainer(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val root = File(context.filesDir, "oshwhub").apply { mkdirs() }
    private val cache = File(context.cacheDir, "oshwhub").apply { mkdirs() }

    val cookieStore = CookieStore(File(root, "cookies.txt"))
    val apiClient = ApiClient(cookieStore, cache, BuildConfig.DEBUG)
    val api = OshApi(apiClient)
    val auth = AuthRepository(apiClient, api)
    val settings = SettingsStore(context)
    val session = SessionManager(api, auth, scope)
    val history = LocalHistoryStore(File(root, "history.txt"))
    val updates = UpdateChecker()
}

/** 登录态 + 未读计数 + 收藏/点赞的跨页面通知 */
class SessionManager(
    private val api: OshApi,
    private val auth: AuthRepository,
    private val scope: CoroutineScope,
) {
    private val _user = MutableStateFlow<UserInfo?>(null)
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events

    val isLoggedIn: Boolean get() = _user.value != null

    suspend fun refresh() {
        val me = auth.currentUser()
        _user.value = me
        _loaded.value = true
        if (me != null) {
            _unread.value = runCatching { api.unreadCount() }.getOrDefault(0)
        } else {
            _unread.value = 0
        }
    }

    suspend fun loginByPassword(account: String, password: String): UserInfo {
        val me = auth.loginByPassword(account, password)
        _user.value = me
        _loaded.value = true
        _unread.value = runCatching { api.unreadCount() }.getOrDefault(0)
        _events.tryEmit("已登录：${me.nickname.ifBlank { me.username }}")
        return me
    }

    suspend fun loginBySms(phone: String, code: String): UserInfo {
        val me = auth.loginBySmsCode(phone, code)
        _user.value = me
        _loaded.value = true
        _unread.value = runCatching { api.unreadCount() }.getOrDefault(0)
        _events.tryEmit("已登录：${me.nickname.ifBlank { me.username }}")
        return me
    }

    suspend fun sendSmsCode(phone: String): String = auth.sendSmsCode(phone)

    /** 网页登录回来带票据时用：OkHttp 自己换会话 */
    suspend fun loginWithCasCode(code: String): UserInfo {
        val me = auth.loginByCasTicket(code)
        _user.value = me
        _loaded.value = true
        _unread.value = runCatching { api.unreadCount() }.getOrDefault(0)
        _events.tryEmit("已登录：${me.nickname.ifBlank { me.username }}")
        return me
    }

    suspend fun logout() {
        auth.logout()
        _user.value = null
        _unread.value = 0
        _events.tryEmit("已退出登录")
    }

    fun notify(message: String) {
        _events.tryEmit(message)
    }

    /**
     * 消息被读掉一条/一批之后同步角标。
     * 直接减本地计数立刻见效，再拉一次接口对齐（失败就保留本地值）。
     */
    fun onNotificationsRead(count: Int = 1) {
        if (count <= 0) return
        _unread.value = (_unread.value - count).coerceAtLeast(0)
        scope.launch { runCatching { _unread.value = api.unreadCount() } }
    }

    /** 全部已读：角标清零，再与服务端对齐 */
    fun onAllNotificationsRead() {
        _unread.value = 0
        scope.launch { runCatching { _unread.value = api.unreadCount() } }
    }

    fun refreshUnread() {
        if (!isLoggedIn) return
        scope.launch { runCatching { _unread.value = api.unreadCount() } }
    }

    fun requireLogin(): Boolean {
        if (isLoggedIn) return true
        _events.tryEmit("请先登录")
        return false
    }

    fun warmUp() {
        scope.launch { runCatching { refresh() } }
    }
}

/** 本地浏览历史（只记 uuid/path/name/thumb，纯离线） */
class LocalHistoryStore(private val file: File) {

    data class Entry(val uuid: String, val path: String, val name: String, val thumb: String, val at: Long)

    private val lock = Any()

    fun list(limit: Int = 60): List<Entry> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        return file.readLines()
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size >= 5) Entry(parts[0], parts[1], parts[2], parts[3], parts[4].toLongOrNull() ?: 0L) else null
            }
            .sortedByDescending { it.at }
            .take(limit)
    }

    fun add(entry: Entry) = synchronized(lock) {
        val next = (listOf(entry) + list(200).filterNot { it.uuid == entry.uuid }).take(200)
        file.parentFile?.mkdirs()
        file.writeText(next.joinToString("\n") { "${it.uuid}\t${it.path}\t${it.name}\t${it.thumb}\t${it.at}" })
    }

    fun clear() = synchronized(lock) { runCatching { file.delete() } }
}

/** 简单的分页累加器，供各列表 ViewModel 复用 */
class PagingState<T>(
    val items: List<T> = emptyList(),
    val page: Int = 0,
    val total: Int = 0,
    val endReached: Boolean = false,
) {
    fun append(pageResult: Paged<T>, incoming: List<T>): PagingState<T> = PagingState(
        items = items + incoming,
        page = pageResult.page,
        total = pageResult.total,
        endReached = pageResult.page >= pageResult.totalPage || incoming.isEmpty(),
    )
}

fun List<ProjectSummary>.dedup(): List<ProjectSummary> = distinctBy { it.uuid }
