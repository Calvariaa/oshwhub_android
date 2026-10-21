package io.github.Calvariaa.oshwhub.ui.mine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.LocalHistoryStore
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.SessionManager
import io.github.Calvariaa.oshwhub.data.model.Article
import io.github.Calvariaa.oshwhub.data.model.Collection
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.model.UserBrief
import io.github.Calvariaa.oshwhub.data.model.UserInfo
import io.github.Calvariaa.oshwhub.ui.components.ArticleRowCard
import io.github.Calvariaa.oshwhub.ui.components.Avatar
import io.github.Calvariaa.oshwhub.ui.components.CollectionRowCard
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.ProjectRowCard
import io.github.Calvariaa.oshwhub.ui.components.StatChip
import io.github.Calvariaa.oshwhub.ui.components.UserRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MineState(
    val loading: Boolean = false,
    val projects: List<ProjectSummary> = emptyList(),
    val collections: List<Collection> = emptyList(),
    val follows: List<UserBrief> = emptyList(),
    val articles: List<Article> = emptyList(),
    val tab: Int = 0,
)

class MineViewModel(
    private val api: OshApi,
    private val session: SessionManager,
    private val history: LocalHistoryStore,
) : ViewModel() {

    private val _state = MutableStateFlow(MineState())
    val state: StateFlow<MineState> = _state.asStateFlow()

    val user: StateFlow<UserInfo?> = session.user
    val unread: StateFlow<Int> = session.unread

    fun load() {
        val me = session.user.value ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val projects = runCatching { api.userProjects(me.uuid, pageSize = 20).lists }.getOrDefault(emptyList())
            val collections = runCatching { api.userCollections(me.uuid, pageSize = 20).lists }.getOrDefault(emptyList())
            val follows = runCatching { api.userFollows(me.uuid, pageSize = 50).lists }.getOrDefault(emptyList())
            val articles = runCatching { api.userArticles(me.uuid, pageSize = 20).lists }.getOrDefault(emptyList())
            _state.value = MineState(
                loading = false,
                projects = projects,
                collections = collections,
                follows = follows,
                articles = articles,
                tab = _state.value.tab,
            )
        }
    }

    fun setTab(index: Int) {
        _state.value = _state.value.copy(tab = index)
    }

    fun historyEntries(): List<LocalHistoryStore.Entry> = history.list()

    suspend fun logout() = session.logout()

    fun refreshUnread() {
        viewModelScope.launch { session.refresh() }
    }
}

@Composable
fun MineScreen(
    vm: MineViewModel,
    onLogin: () -> Unit,
    onProject: (String) -> Unit,
    onArticle: (String) -> Unit,
    onCollection: (Collection) -> Unit,
    onUser: (String) -> Unit,
    onNotifications: () -> Unit,
    onSettings: () -> Unit,
    onHistory: () -> Unit,
    onFollowList: () -> Unit,
) {
    val user by vm.user.collectAsState()
    val state by vm.state.collectAsState()
    val unread by vm.unread.collectAsState()
    val scope = rememberCoroutineScope()

    if (user == null) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(60.dp))
            Text("还没有登录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "登录后可以同步你的工程、收藏与消息",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onLogin, shape = RoundedCornerShape(50)) { Text("立即登录") }
            Spacer(Modifier.height(28.dp))
            MineEntry("本地浏览历史", Icons.Outlined.History, onHistory)
            MineEntry("设置", Icons.Filled.Settings, onSettings)
        }
        return
    }

    val me = user!!
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(me.avatar, size = 64)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).clickable { onUser(me.uuid) }) {
                    Text(
                        me.nickname.ifBlank { me.username },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("@${me.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onSettings, shape = RoundedCornerShape(50)) { Text("设置") }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                StatChip("${me.following.size}", "关注")
                StatChip("${me.follower.size}", "粉丝")
                StatChip("${state.projects.size}", "工程")
                StatChip("${state.collections.size}", "合集")
            }
            Spacer(Modifier.height(10.dp))
        }
        item {
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MineTile("消息 ${if (unread > 0) unread else ""}".trim(), Icons.Filled.Notifications, onNotifications, Modifier.weight(1f))
                    MineTile("浏览历史", Icons.Outlined.History, onHistory, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    MineTile("我的关注", Icons.Outlined.People, onFollowList, Modifier.weight(1f))
                    MineTile("我的收藏", Icons.Outlined.CollectionsBookmark, { vm.setTab(1) }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        item {
            SecondaryTabRow(selectedTabIndex = state.tab) {
                listOf("工程", "收藏", "关注", "文章").forEachIndexed { index, label ->
                    Tab(selected = state.tab == index, onClick = { vm.setTab(index) }, text = { Text(label) })
                }
            }
        }
        if (state.loading) {
            item { LoadingBox() }
        } else {
            when (state.tab) {
                0 -> {
                    if (state.projects.isEmpty()) item { EmptyState("你还没有发布工程") }
                    items(state.projects, key = { it.uuid }) { p -> ProjectRowCard(p, onClick = { onProject(p.uuid) }) }
                }
                1 -> {
                    if (state.collections.isEmpty()) item { EmptyState("你还没有创建合集") }
                    items(state.collections, key = { it.uuid }) { c -> CollectionRowCard(c, onClick = { onCollection(c) }) }
                }
                2 -> {
                    if (state.follows.isEmpty()) item { EmptyState("还没有关注任何人") }
                    items(state.follows, key = { it.uuid }) { u -> UserRow(u, onClick = { onUser(u.uuid) }) }
                }
                else -> {
                    if (state.articles.isEmpty()) item { EmptyState("你还没有发布文章") }
                    items(state.articles, key = { it.uuid }) { a -> ArticleRowCard(a, onClick = { onArticle(a.uuid) }) }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { vm.logout() } },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("退出登录")
                }
            }
        }
    }

    LaunchedEffect(me.uuid) { vm.load() }
}

@Composable
private fun MineTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun MineEntry(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
