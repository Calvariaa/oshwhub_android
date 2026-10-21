package io.github.Calvariaa.oshwhub.ui.article

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.model.Article
import io.github.Calvariaa.oshwhub.data.model.Comment
import io.github.Calvariaa.oshwhub.ui.components.ArticleRowCard
import io.github.Calvariaa.oshwhub.ui.components.Avatar
import io.github.Calvariaa.oshwhub.ui.components.CommentComposer
import io.github.Calvariaa.oshwhub.ui.components.CommentItem
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadMoreFooter
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.MarkdownText
import io.github.Calvariaa.oshwhub.ui.components.RichText
import io.github.Calvariaa.oshwhub.ui.components.StatText
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArticleListState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val articles: List<Article> = emptyList(),
    val page: Int = 1,
    val totalPage: Int = 1,
    val catalog: String = "",
)

/**
 * 文章列表。站点 web 的「全部 / 技术 / 高校 / 产品」分类在页面侧（SSR）过滤，
 * JSON 接口会忽略 `catalog` 参数，所以这里按 `catalog` 字段在客户端筛，
 * 逐页扫描后端列表直到凑够一屏。
 */
class ArticleListViewModel(private val api: OshApi) : ViewModel() {
    private val _state = MutableStateFlow(ArticleListState())
    val state: StateFlow<ArticleListState> = _state.asStateFlow()

    private val catalogs = listOf(
        "" to "全部",
        "technique" to "技术",
        "university" to "高校",
        "product" to "产品",
        "activity" to "活动",
        "skill" to "技能",
        "others" to "其他",
    )

    fun catalogOptions(): List<Pair<String, String>> = catalogs

    private var scanPage = 1

    fun selectCatalog(catalog: String) {
        if (_state.value.catalog == catalog) return
        _state.value = _state.value.copy(catalog = catalog)
        load()
    }

    fun load() {
        viewModelScope.launch {
            scanPage = 1
            _state.value = _state.value.copy(loading = true, loadingMore = false, error = null, articles = emptyList())
            scan(need = 20, append = false)
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.page >= s.totalPage) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            scan(need = 20, append = true)
        }
    }

    private suspend fun scan(need: Int, append: Boolean) {
        val catalog = _state.value.catalog
        val collected = mutableListOf<Article>()
        var lastPage = _state.value.page
        var totalPage = _state.value.totalPage
        var failed: String? = null
        while (collected.size < need && scanPage <= 60) {
            val paged = runCatching { api.articles(page = scanPage, pageSize = 50) }
                .getOrElse { e ->
                    failed = e.message ?: "加载失败"
                    break
                }
            lastPage = paged.page
            totalPage = paged.totalPage.coerceAtLeast(1)
            collected += paged.lists.filter { catalog.isBlank() || it.catalog == catalog }
            scanPage = paged.page + 1
            if (paged.lists.isEmpty() || paged.page >= paged.totalPage) break
        }
        val s = _state.value
        val all = if (append) s.articles + collected else collected
        // 扫描到的后端页数作为分页游标：到末尾就没有更多了
        val exhausted = scanPage > totalPage && collected.size < need
        _state.value = s.copy(
            loading = false,
            loadingMore = false,
            error = if (all.isEmpty()) failed else null,
            articles = all,
            page = if (exhausted) totalPage else lastPage,
            totalPage = totalPage,
        )
    }
}

@Composable
fun ArticleListScreen(vm: ArticleListViewModel, onBack: () -> Unit, onArticle: (String) -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { if (state.articles.isEmpty()) vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("技术文章", style = MaterialTheme.typography.titleMedium)
        }
        val catalogs = vm.catalogOptions()
        SecondaryScrollableTabRow(
            selectedTabIndex = catalogs.indexOfFirst { it.first == state.catalog }.coerceAtLeast(0),
            edgePadding = 8.dp,
        ) {
            catalogs.forEach { (key, label) ->
                Tab(
                    selected = state.catalog == key,
                    onClick = { vm.selectCatalog(key) },
                    text = { Text(label) },
                )
            }
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.articles.isEmpty() -> EmptyState("暂无文章")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.articles, key = { it.uuid }) { article ->
                    ArticleRowCard(article, onClick = { onArticle(article.uuid) })
                }
                item { LoadMoreFooter(state.loadingMore, state.page >= state.totalPage) }
            }
        }
    }
}

// ----------------------------------------------------------------- 详情 --

data class ArticleState(
    val loading: Boolean = true,
    val error: String? = null,
    val article: Article? = null,
    val comments: List<Comment> = emptyList(),
    val commentTotal: Int = 0,
    val commentsLoading: Boolean = false,
    val sending: Boolean = false,
)

class ArticleViewModel(private val api: OshApi, private val uuid: String) : ViewModel() {
    private val _state = MutableStateFlow(ArticleState())
    val state: StateFlow<ArticleState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.article(uuid) }
                .onSuccess { _state.value = _state.value.copy(loading = false, article = it) }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
            loadComments()
        }
    }

    fun loadComments() {
        viewModelScope.launch {
            _state.value = _state.value.copy(commentsLoading = true)
            runCatching { api.comments("article", uuid, page = 1, pageSize = 30) }
                .onSuccess { page ->
                    _state.value = _state.value.copy(
                        comments = page.lists,
                        commentTotal = page.all.takeIf { it > 0 } ?: page.total,
                        commentsLoading = false,
                    )
                }
                .onFailure { _state.value = _state.value.copy(commentsLoading = false) }
        }
    }

    fun sendComment(content: String, onDone: () -> Unit) {
        if (content.isBlank() || _state.value.sending) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true)
            runCatching { api.createComment("article", uuid, content.trim()) }
                .onSuccess {
                    _state.value = _state.value.copy(sending = false)
                    onDone()
                    loadComments()
                }
                .onFailure { _state.value = _state.value.copy(sending = false) }
        }
    }
}

@Composable
fun ArticleDetailScreen(vm: ArticleViewModel, onBack: () -> Unit, onUser: (String) -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("文章", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.article == null -> EmptyState("文章不存在")
            else -> {
                val article = state.article!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(article.creator.avatar, size = 28)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    article.creator.nickname.ifBlank { article.creator.username },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    Fmt.time(article.oshwhub_publish_at ?: article.created_at),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            StatText(views = article.view_count, likes = article.like_count, stars = article.collect_count)
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        val body = article.content.ifBlank { article.introduction }
                        RichText(body)
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "评论 ${state.commentTotal}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    CommentComposer(sending = state.sending, onSend = { text, done -> vm.sendComment(text, done) })
                    if (state.commentsLoading) LoadingBox()
                    if (state.comments.isEmpty() && !state.commentsLoading) {
                        EmptyState("还没有评论，来抢沙发")
                    }
                    state.comments.forEach { comment ->
                        CommentItem(comment, onUser)
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}
