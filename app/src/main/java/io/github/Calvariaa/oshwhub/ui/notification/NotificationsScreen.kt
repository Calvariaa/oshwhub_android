package io.github.Calvariaa.oshwhub.ui.notification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.model.Paged
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

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
    val error: String? = null,
    val tab: Int = 0,
    val items: List<NotifyItem> = emptyList(),
    val needLogin: Boolean = false,
)

class NotificationViewModel(private val api: OshApi) : ViewModel() {

    private val _state = MutableStateFlow(NotifyState())
    val state: StateFlow<NotifyState> = _state.asStateFlow()

    private val tabs = listOf(
        "allNotifications" to "全部",
        "message" to "私信",
        "system" to "系统",
    )

    fun tabOptions() = tabs

    fun load() {
        viewModelScope.launch { loadTab(_state.value.tab) }
    }

    fun setTab(index: Int) {
        _state.value = _state.value.copy(tab = index, loading = true)
        viewModelScope.launch { loadTab(index) }
    }

    private suspend fun loadTab(index: Int) {
        val type = tabs.getOrNull(index)?.first ?: "allNotifications"
        runCatching { api.notifications(type = type, read = false, page = 1, pageSize = 40) }
            .onSuccess { paged ->
                _state.value = _state.value.copy(
                    loading = false,
                    items = paged.lists.map { toItem(api, it) },
                    needLogin = false,
                )
                runCatching { api.markNotificationsRead(paged.lists.map { it.uuid }) }
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message,
                    needLogin = api.isAuthError(e),
                )
            }
    }

    private fun toItem(api: OshApi, notification: io.github.Calvariaa.oshwhub.data.model.Notification): NotifyItem {
        return NotifyItem(
            uuid = notification.uuid,
            title = notification.title.ifBlank { titleFromType(notification.type) },
            content = notification.content,
            time = Fmt.relative(notification.created_at),
            read = notification.read,
            type = notification.type,
        )
    }

    private fun titleFromType(type: String): String = when {
        type.contains("like", true) -> "收到点赞"
        type.contains("comment", true) -> "收到评论"
        type.contains("follow", true) -> "新增关注"
        type.contains("message", true) -> "私信"
        else -> "系统通知"
    }
}

@Composable
fun NotificationsScreen(
    vm: NotificationViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("消息中心", style = MaterialTheme.typography.titleMedium)
        }
        SecondaryTabRow(selectedTabIndex = state.tab) {
            vm.tabOptions().forEachIndexed { index, (_, label) ->
                Tab(
                    selected = state.tab == index,
                    onClick = { vm.setTab(index) },
                    text = { Text(label) },
                )
            }
        }
        when {
            state.needLogin -> EmptyState("登录后查看消息", "立创开源硬件平台通知")
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.items.isEmpty() -> EmptyState("暂时没有新消息")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.items, key = { it.uuid }) { item -> NotifyRow(item) }
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
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
