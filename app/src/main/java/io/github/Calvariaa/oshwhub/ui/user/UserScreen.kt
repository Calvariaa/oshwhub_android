package io.github.Calvariaa.oshwhub.ui.user

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.SessionManager
import io.github.Calvariaa.oshwhub.data.model.Article
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.model.UserBrief
import io.github.Calvariaa.oshwhub.data.model.UserInfo
import io.github.Calvariaa.oshwhub.data.net.ApiException
import io.github.Calvariaa.oshwhub.ui.components.ArticleRowCard
import io.github.Calvariaa.oshwhub.ui.components.Avatar
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadMoreFooter
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.ProjectRowCard
import io.github.Calvariaa.oshwhub.ui.components.StatChip
import io.github.Calvariaa.oshwhub.ui.components.UserRow
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserState(
    val loading: Boolean = true,
    val error: String? = null,
    val user: UserInfo? = null,
    val likeCount: Int = 0,
    val tab: Int = 0,
    val projects: List<ProjectSummary> = emptyList(),
    val star: List<ProjectSummary> = emptyList(),
    val follows: List<UserBrief> = emptyList(),
    val followers: List<UserBrief> = emptyList(),
    val articles: List<Article> = emptyList(),
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val totalPage: Int = 1,
    val following: Boolean = false,
    val isSelf: Boolean = false,
)

class UserViewModel(
    private val api: OshApi,
    private val session: SessionManager,
    private val uuid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(UserState())
    val state: StateFlow<UserState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.userInfo(uuid) }
                .onSuccess { info ->
                    if (info == null) {
                        _state.value = _state.value.copy(loading = false, error = "用户不存在")
                        return@onSuccess
                    }
                    val me = session.user.value
                    val self = me != null && me.uuid == uuid
                    _state.value = _state.value.copy(
                        loading = false,
                        user = info,
                        isSelf = self,
                        likeCount = runCatching { api.userLikeCount(uuid) }.getOrDefault(0),
                        following = info.follower.any { it.uuid == me?.uuid },
                    )
                    loadTab(0, reset = true)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = (e as? ApiException)?.message ?: e.message)
                }
        }
    }

    fun setTab(index: Int) {
        if (_state.value.tab == index) return
        _state.value = _state.value.copy(tab = index, page = 1)
        loadTab(index, reset = true)
    }

    private fun loadTab(index: Int, reset: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loadingMore = true)
            when (index) {
                0 -> runCatching { api.userProjects(uuid, page = 1, pageSize = 20) }.onSuccess { paged ->
                    _state.value = _state.value.copy(projects = paged.lists, page = paged.page, totalPage = paged.totalPage, loadingMore = false)
                }.onFailure { _state.value = _state.value.copy(loadingMore = false) }
                1 -> runCatching { api.userStar(uuid, page = 1, pageSize = 20) }.onSuccess { paged ->
                    _state.value = _state.value.copy(star = paged.lists, page = paged.page, totalPage = paged.totalPage, loadingMore = false)
                }.onFailure { _state.value = _state.value.copy(loadingMore = false) }
                2 -> runCatching { api.userFollows(uuid, pageSize = 50) }.onSuccess { paged ->
                    _state.value = _state.value.copy(follows = paged.lists, loadingMore = false)
                }.onFailure { _state.value = _state.value.copy(loadingMore = false) }
                3 -> runCatching { api.userFollowers(uuid, pageSize = 50) }.onSuccess { paged ->
                    _state.value = _state.value.copy(followers = paged.lists, loadingMore = false)
                }.onFailure { _state.value = _state.value.copy(loadingMore = false) }
                else -> runCatching { api.userArticles(uuid, page = 1, pageSize = 20) }.onSuccess { paged ->
                    _state.value = _state.value.copy(articles = paged.lists, loadingMore = false)
                }.onFailure { _state.value = _state.value.copy(loadingMore = false) }
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.tab > 1 || s.page >= s.totalPage) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            val next = s.page + 1
            when (s.tab) {
                0 -> runCatching { api.userProjects(uuid, page = next, pageSize = 20) }.onSuccess { paged ->
                    _state.value = _state.value.copy(
                        projects = s.projects + paged.lists,
                        page = paged.page,
                        totalPage = paged.totalPage,
                        loadingMore = false,
                    )
                }.onFailure { _state.value = _state.value.copy(loadingMore = false) }
                1 -> runCatching { api.userStar(uuid, page = next, pageSize = 20) }.onSuccess { paged ->
                    _state.value = _state.value.copy(
                        star = s.star + paged.lists,
                        page = paged.page,
                        totalPage = paged.totalPage,
                        loadingMore = false,
                    )
                }.onFailure { _state.value = _state.value.copy(loadingMore = false) }
                else -> _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }

    fun toggleFollow() {
        if (!session.isLoggedIn) { session.notify("请先登录"); return }
        viewModelScope.launch {
            val following = _state.value.following
            runCatching { if (following) api.unfollow(uuid) else api.follow(uuid) }
                .onSuccess {
                    _state.value = _state.value.copy(following = !following)
                    session.notify(if (following) "已取消关注" else "已关注")
                }
                .onFailure { e -> session.notify((e as? ApiException)?.message ?: "操作失败") }
        }
    }
}

@Composable
fun UserScreen(
    vm: UserViewModel,
    onBack: () -> Unit,
    onProject: (String) -> Unit,
    onArticle: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("用户主页", style = MaterialTheme.typography.titleMedium)
        }

        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.user == null -> EmptyState("用户不存在")
            else -> {
                val user = state.user!!
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item { UserHeader(user, state, vm::toggleFollow) }
                    item {
                        SecondaryTabRow(selectedTabIndex = state.tab) {
                            listOf("工程", "收藏", "关注", "粉丝", "文章").forEachIndexed { index, label ->
                                Tab(
                                    selected = state.tab == index,
                                    onClick = { vm.setTab(index) },
                                    text = { Text(label, style = MaterialTheme.typography.labelLarge) },
                                )
                            }
                        }
                    }
                    when (state.tab) {
                        0 -> {
                            if (state.projects.isEmpty()) item { EmptyState("还没有公开工程") }
                            items(state.projects, key = { it.uuid }) { p ->
                                ProjectRowCard(p, onClick = { onProject(p.uuid) })
                            }
                        }
                        1 -> {
                            if (state.star.isEmpty()) item { EmptyState("还没有收藏工程") }
                            items(state.star, key = { it.uuid }) { p ->
                                ProjectRowCard(p, onClick = { onProject(p.uuid) })
                            }
                        }
                        2 -> {
                            if (state.follows.isEmpty()) item { EmptyState("还没有关注任何人") }
                            items(state.follows, key = { it.uuid }) { u -> UserRow(u) }
                        }
                        3 -> {
                            if (state.followers.isEmpty()) item { EmptyState("还没有粉丝") }
                            items(state.followers, key = { it.uuid }) { u -> UserRow(u) }
                        }
                        else -> {
                            if (state.articles.isEmpty()) item { EmptyState("还没有发布文章") }
                            items(state.articles, key = { it.uuid }) { a ->
                                ArticleRowCard(a, onClick = { onArticle(a.uuid) })
                            }
                        }
                    }
                    item { LoadMoreFooter(loading = state.loadingMore, endReached = state.page >= state.totalPage) }
                }
            }
        }
    }
}

@Composable
private fun UserHeader(user: UserInfo, state: UserState, onFollow: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(user.avatar, size = 64)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        user.nickname.ifBlank { user.username },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    if (user.is_fp_office_account) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text(
                                "官方",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Text("@${user.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!state.isSelf) {
                FilledTonalButton(onClick = onFollow, shape = RoundedCornerShape(50)) {
                    Text(if (state.following) "已关注" else "关注")
                }
            }
        }
        if (user.introduction.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(Html.toPlain(user.introduction), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatChip("${user.following.size}", "关注")
            StatChip("${user.follower.size}", "粉丝")
            StatChip("${state.likeCount}", "获赞")
            Spacer(Modifier.weight(1f))
            Text(
                "加入于 ${Fmt.date(user.created_at)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (user.info.school.isNotBlank() || user.info.company.isNotBlank() || user.info.career.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(
                    user.info.career.takeIf { it.isNotBlank() },
                    user.info.school.takeIf { it.isNotBlank() },
                    user.info.company.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
