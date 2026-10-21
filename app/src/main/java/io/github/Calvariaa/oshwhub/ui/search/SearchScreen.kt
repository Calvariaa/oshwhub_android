package io.github.Calvariaa.oshwhub.ui.search

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ListItem
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.SettingsStore
import io.github.Calvariaa.oshwhub.data.model.Article
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.model.UserSummary
import io.github.Calvariaa.oshwhub.ui.components.ArticleRowCard
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.ProjectRowCard
import io.github.Calvariaa.oshwhub.ui.components.UserRow
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

data class SearchState(
    val query: String = "",
    val submitted: String = "",
    val tab: Int = 0,
    val loading: Boolean = false,
    val hotWords: List<String> = emptyList(),
    val history: List<String> = emptyList(),
    val projects: List<ProjectSummary> = emptyList(),
    val users: List<UserSummary> = emptyList(),
    val articles: List<Article> = emptyList(),
    val posts: List<io.github.Calvariaa.oshwhub.data.model.ForumPost> = emptyList(),
    val projectTotal: Int = 0,
    val userTotal: Int = 0,
    val articleTotal: Int = 0,
    val postTotal: Int = 0,
    val error: String? = null,
)

class SearchViewModel(
    private val api: OshApi,
    private val settings: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val hot = runCatching { api.hotWords() }.getOrDefault(emptyList())
            val history = runCatching { settings.searchHistory.first() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(hotWords = hot, history = history)
        }
    }

    fun updateQuery(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun setTab(index: Int) {
        _state.value = _state.value.copy(tab = index)
        val q = _state.value.submitted
        if (q.isBlank()) return
        when (index) {
            0 -> if (_state.value.projects.isEmpty()) search("project")
            1 -> if (_state.value.users.isEmpty()) search("user")
            2 -> if (_state.value.articles.isEmpty()) search("article")
            3 -> if (_state.value.posts.isEmpty()) search("post")
        }
    }

    fun submit(query: String = _state.value.query) {
        val q = query.trim()
        if (q.isEmpty()) return
        _state.value = _state.value.copy(query = q, submitted = q, loading = true, error = null)
        viewModelScope.launch {
            settings.pushSearchHistory(q)
            _state.value = _state.value.copy(history = settings.searchHistory.first())
            search("project")
            search("user")
            search("article")
            search("post")
        }
    }

    private fun search(type: String) {
        viewModelScope.launch {
            val q = _state.value.submitted
            runCatching { api.search(type, q, page = 1, pageSize = 20) }
                .onSuccess { page ->
                    val json = api.json
                    when (type) {
                        "project" -> _state.value = _state.value.copy(
                            projects = page.lists.mapNotNull {
                                runCatching { json.decodeFromJsonElement<ProjectSummary>(it) }.getOrNull()
                            },
                            projectTotal = page.total,
                            loading = false,
                        )
                        "user" -> _state.value = _state.value.copy(
                            users = page.lists.mapNotNull {
                                runCatching { json.decodeFromJsonElement<UserSummary>(it) }.getOrNull()
                            },
                            userTotal = page.total,
                            loading = false,
                        )
                        "article" -> _state.value = _state.value.copy(
                            articles = page.lists.mapNotNull {
                                runCatching { json.decodeFromJsonElement<Article>(it) }.getOrNull()
                            },
                            articleTotal = page.total,
                            loading = false,
                        )
                        else -> _state.value = _state.value.copy(
                            posts = page.lists.mapNotNull {
                                runCatching {
                                    json.decodeFromJsonElement<io.github.Calvariaa.oshwhub.data.model.ForumPost>(it)
                                }.getOrNull()
                            },
                            postTotal = page.total,
                            loading = false,
                        )
                    }
                }                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message)
                }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            settings.clearSearchHistory()
            _state.value = _state.value.copy(history = emptyList())
        }
    }
}

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    initialQuery: String?,
    onBack: () -> Unit,
    onProject: (String) -> Unit,
    onUser: (String) -> Unit,
    onArticle: (String) -> Unit,
    onPost: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && initialQuery != state.submitted) {
            vm.updateQuery(initialQuery)
            vm.submit(initialQuery)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f),
            ) {
                TextField(
                    value = state.query,
                    onValueChange = vm::updateQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    placeholder = { Text("搜索工程、用户、文章") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        keyboard?.hide()
                        vm.submit()
                    }),
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { vm.updateQuery("") }) {
                                Icon(Icons.Filled.Close, "清空", Modifier.size(18.dp))
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                )
            }
            Text(
                "搜索",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .then(Modifier),
            )
        }

        if (state.submitted.isBlank()) {
            Suggestions(state, onPick = { vm.submit(it) }, onClearHistory = vm::clearHistory)
            return@Column
        }

        SecondaryScrollableTabRow(selectedTabIndex = state.tab, edgePadding = 8.dp) {
            listOf(
                "工程 ${if (state.projectTotal > 0) state.projectTotal else ""}",
                "用户 ${if (state.userTotal > 0) state.userTotal else ""}",
                "文章 ${if (state.articleTotal > 0) state.articleTotal else ""}",
                "帖子 ${if (state.postTotal > 0) state.postTotal else ""}",
            ).forEachIndexed { index, label ->
                Tab(
                    selected = state.tab == index,
                    onClick = { vm.setTab(index) },
                    text = { Text(label.trim(), style = MaterialTheme.typography.labelLarge) },
                )
            }
        }

        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.tab == 0 -> {
                if (state.projects.isEmpty()) EmptyState("没有找到相关工程", "换个关键词试试") else
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.projects, key = { it.uuid }) { project ->
                            ProjectRowCard(project, onClick = { onProject(project.uuid) })
                        }
                    }
            }
            state.tab == 1 -> {
                if (state.users.isEmpty()) EmptyState("没有找到相关用户") else
                    LazyColumn {
                        items(state.users, key = { it.uuid }) { user ->
                            UserRow(
                                user = io.github.Calvariaa.oshwhub.data.model.UserBrief(
                                    uuid = user.uuid,
                                    username = user.username,
                                    nickname = user.nickname,
                                    avatar = user.avatar,
                                    team = user.team,
                                ),
                                onClick = { onUser(user.uuid) },
                            )
                        }
                    }
            }
            state.tab == 2 -> {
                if (state.articles.isEmpty()) EmptyState("没有找到相关文章") else
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.articles, key = { it.uuid }) { article ->
                            ArticleRowCard(article, onClick = { onArticle(article.uuid) })
                        }
                    }
            }
            else -> {
                if (state.posts.isEmpty()) EmptyState("没有找到相关帖子") else
                    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(state.posts, key = { it.uuid }) { post ->
                            ListItem(
                                headlineContent = { Text(post.title, maxLines = 2) },
                                supportingContent = {
                                    Text(
                                        Html.toPlain(post.summary.ifBlank { post.content }).take(80),
                                        maxLines = 2,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                modifier = Modifier.clickable { onPost(post.uuid) },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun Suggestions(
    state: SearchState,
    onPick: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        if (state.history.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("搜索历史", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        "清空",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(4.dp)
                            .then(Modifier),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.history) { keyword ->
                        AssistChip(onClick = { onPick(keyword) }, label = { Text(keyword) })
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            Text("热门搜索", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }
        items(state.hotWords.chunked(2)) { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEach { word ->
                    AssistChip(
                        onClick = { onPick(word) },
                        label = { Text(word) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
