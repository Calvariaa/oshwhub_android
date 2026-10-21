package io.github.Calvariaa.oshwhub.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import io.github.Calvariaa.oshwhub.data.model.ForumPost
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadMoreFooter
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.MarkdownText
import io.github.Calvariaa.oshwhub.ui.components.RichText
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PostListState(
    val loading: Boolean = true,
    val error: String? = null,
    val posts: List<ForumPost> = emptyList(),
    val page: Int = 1,
    val totalPage: Int = 1,
    val loadingMore: Boolean = false,
)

class PostListViewModel(private val api: OshApi) : ViewModel() {
    private val _state = MutableStateFlow(PostListState())
    val state: StateFlow<PostListState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.posts(page = 1, pageSize = 20) }
                .onSuccess { paged ->
                    _state.value = _state.value.copy(
                        loading = false,
                        posts = paged.lists,
                        page = paged.page,
                        totalPage = paged.totalPage,
                    )
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.page >= s.totalPage) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            runCatching { api.posts(page = s.page + 1, pageSize = 20) }
                .onSuccess { paged ->
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        posts = s.posts + paged.lists,
                        page = paged.page,
                        totalPage = paged.totalPage,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loadingMore = false) }
        }
    }
}

@Composable
fun PostListScreen(vm: PostListViewModel, onBack: () -> Unit, onPost: (String) -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { if (state.posts.isEmpty()) vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("平台公告", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.posts.isEmpty() -> EmptyState("暂无公告")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.posts, key = { it.uuid }) { post ->
                    PostRow(post) { onPost(post.uuid) }
                }
                item { LoadMoreFooter(state.loadingMore, state.page >= state.totalPage) }
            }
        }
    }
}

@Composable
fun PostRow(post: ForumPost, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (post.recommend > 0) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            "置顶",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    Spacer(Modifier.height(0.dp))
                }
                Text(
                    post.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (post.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    Html.toPlain(post.summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${Fmt.date(post.created_at)} · ${post.views} 次阅读",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

data class PostState(val loading: Boolean = true, val error: String? = null, val post: ForumPost? = null)

class PostViewModel(private val api: OshApi, private val uuid: String) : ViewModel() {
    private val _state = MutableStateFlow(PostState())
    val state: StateFlow<PostState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.post(uuid) }
                .onSuccess { _state.value = _state.value.copy(loading = false, post = it) }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
        }
    }
}

@Composable
fun PostDetailScreen(vm: PostViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("公告", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.post == null -> EmptyState("公告不存在")
            else -> {
                val post = state.post!!
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    Text(post.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${Fmt.time(post.created_at)} · ${post.views} 次阅读",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    RichText(post.content.ifBlank { post.summary })
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}
