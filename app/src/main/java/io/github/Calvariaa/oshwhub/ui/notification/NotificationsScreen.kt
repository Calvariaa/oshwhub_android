package io.github.Calvariaa.oshwhub.ui.notification

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.model.Notification
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 通知/消息（字段在不同 type 下略有差异，这里做宽松映射） */
data class NotifyItem(
    val uuid: String,
    val title: String,
    val content: String,
    val time: String,
    val read: Boolean,
    val type: String,
)

data class NotifyState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val tab: Int = 0,
    val items: List<NotifyItem> = emptyList(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val needLogin: Boolean = false,
)

class NotificationViewModel(private val api: OshApi) : ViewModel() {

    private val _state = MutableStateFlow(NotifyState())
    val state: StateFlow<NotifyState> = _state.asStateFlow()

    /** 站点的消息只有两个 type：allNotifications(通知) / message(私信)，read 只有未读才传。 */
    private data class TabSpec(val type: String, val label: String, val unreadOnly: Boolean)

    private val tabs = listOf(
        TabSpec("allNotifications", "全部", unreadOnly = false),
        TabSpec("allNotifications", "未读", unreadOnly = true),
        TabSpec("message", "私信", unreadOnly = false),
    )

    fun tabOptions() = tabs.map { it.label }

    /** 有未读时才值得显示"全部已读"。 */
    fun hasUnread(): Boolean = _state.value.items.any { !it.read }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch { fetch(page = 1, append = false) }
    }

    fun setTab(index: Int) {
        if (index == _state.value.tab) return
        _state.value = _state.value.copy(tab = index, loading = true, items = emptyList(), page = 1)
        viewModelScope.launch { fetch(page = 1, append = false) }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || !s.hasMore) return
        _state.value = s.copy(loadingMore = true)
        viewModelScope.launch { fetch(page = s.page + 1, append = true) }
    }

    /** 手动标记已读：进页面自动标记会让消息在列表里"消失"，改由用户决定。 */
    fun markAllRead() {
        val uuids = _state.value.items.filter { !it.read }.map { it.uuid }
        if (uuids.isEmpty()) return
        viewModelScope.launch {
            runCatching { api.markNotificationsRead(uuids) }
            _state.value = _state.value.copy(items = _state.value.items.map { it.copy(read = true) })
            runCatching { api.unreadCount() }
        }
    }

    private suspend fun fetch(page: Int, append: Boolean) {
        val spec = tabs.getOrNull(_state.value.tab) ?: tabs.first()
        runCatching {
            api.notifications(
                type = spec.type,
                read = if (spec.unreadOnly) false else null,
                page = page,
                pageSize = PAGE_SIZE,
            )
        }
            .onSuccess { paged ->
                val incoming = paged.lists.map { toItem(it) }
                val merged = if (append) _state.value.items + incoming else incoming
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = null,
                    needLogin = false,
                    items = merged.distinctBy { it.uuid },
                    page = page,
                    hasMore = page < paged.totalPage && incoming.isNotEmpty(),
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    loadingMore = false,
                    error = e.message,
                    needLogin = api.isAuthError(e),
                )
            }
    }

    private fun toItem(notification: Notification): NotifyItem = NotifyItem(
        uuid = notification.uuid,
        title = notification.title.ifBlank { titleFromType(notification.type) },
        content = notification.content,
        time = Fmt.relative(notification.created_at),
        read = !notification.unread,
        type = notification.type,
    )

    private fun titleFromType(type: String): String = when {
        type.contains("like", true) -> "收到点赞"
        type.contains("collect", true) -> "收到收藏"
        type.contains("comment", true) -> "收到评论"
        type.contains("follow", true) -> "新增关注"
        type.contains("message", true) -> "私信"
        type.contains("coupon", true) -> "优惠券通知"
        else -> "系统通知"
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

@Composable
fun NotificationsScreen(
    vm: NotificationViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.load() }

    // 滚到底自动续页
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (last >= state.items.size - 3) vm.loadMore()
            }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("消息中心", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            if (vm.hasUnread()) {
                TextButton(onClick = vm::markAllRead) {
                    Icon(Icons.Filled.DoneAll, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("全部已读")
                }
            }
        }
        SecondaryTabRow(selectedTabIndex = state.tab) {
            vm.tabOptions().forEachIndexed { index, label ->
                Tab(
                    selected = state.tab == index,
                    onClick = { vm.setTab(index) },
                    text = { Text(label) },
                )
            }
        }
        when {
            state.needLogin -> EmptyState(
                "登录后查看消息",
                "立创开源硬件平台通知",
                actionLabel = "去登录",
                onAction = onLogin,
            )
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.items.isEmpty() -> EmptyState(
                if (state.tab == 1) "没有未读消息" else "暂时没有消息",
                "别人给你的点赞、评论、关注和私信都会出现在这里",
            )
            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(state.items, key = { it.uuid }) { item -> NotifyRow(item) }
                if (state.loadingMore) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotifyRow(item: NotifyItem) {
    Surface(
        color = if (item.read) MaterialTheme.colorScheme.surface
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.read) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 6.dp),
                    ) {
                        Spacer(Modifier.width(6.dp).height(6.dp))
                    }
                }
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    item.time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    Html.toPlain(item.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
