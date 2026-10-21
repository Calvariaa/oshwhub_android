package io.github.Calvariaa.oshwhub.ui.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import io.github.Calvariaa.oshwhub.data.model.Collection
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.ui.components.CollectionRowCard
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadMoreFooter
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.ProjectGridCard
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CollectionListState(
    val loading: Boolean = true,
    val error: String? = null,
    val collections: List<Collection> = emptyList(),
    val page: Int = 1,
    val totalPage: Int = 1,
    val loadingMore: Boolean = false,
)

class CollectionListViewModel(private val api: OshApi) : ViewModel() {
    private val _state = MutableStateFlow(CollectionListState())
    val state: StateFlow<CollectionListState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.collections(page = 1, pageSize = 20) }
                .onSuccess { paged ->
                    _state.value = _state.value.copy(
                        loading = false,
                        collections = paged.lists,
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
            runCatching { api.collections(page = s.page + 1, pageSize = 20) }
                .onSuccess { paged ->
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        collections = s.collections + paged.lists,
                        page = paged.page,
                        totalPage = paged.totalPage,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loadingMore = false) }
        }
    }
}

@Composable
fun CollectionListScreen(vm: CollectionListViewModel, onBack: () -> Unit, onCollection: (Collection) -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { if (state.collections.isEmpty()) vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("工程合集", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.collections.isEmpty() -> EmptyState("暂无合集")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.collections, key = { it.uuid }) { collection ->
                    CollectionRowCard(collection, onClick = { onCollection(collection) })
                }
                item { LoadMoreFooter(state.loadingMore, state.page >= state.totalPage) }
            }
        }
    }
}

data class CollectionState(
    val loading: Boolean = true,
    val error: String? = null,
    val collection: Collection? = null,
    val projects: List<ProjectSummary> = emptyList(),
    val page: Int = 1,
    val totalPage: Int = 1,
    val loadingMore: Boolean = false,
)

class CollectionViewModel(private val api: OshApi, private val key: String) : ViewModel() {
    private val _state = MutableStateFlow(CollectionState())
    val state: StateFlow<CollectionState> = _state.asStateFlow()

    /** 路由里的 key 可能是 path（officeRecommend），接口只认 uuid，先解析一次。 */
    private var resolved: String? = null

    private suspend fun target(): String {
        resolved?.let { return it }
        val collection = runCatching { api.resolveCollectionKey(key) }.getOrNull()
        val uuid = collection?.uuid?.takeIf { it.isNotBlank() } ?: key
        resolved = uuid
        return uuid
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val uuid = target()
            val info = runCatching { api.collection(uuid) }.getOrNull()
            val projects = runCatching { api.collectionProjects(uuid, page = 1, pageSize = 24) }.getOrNull()
            if (info == null && projects == null) {
                _state.value = _state.value.copy(loading = false, error = "合集不存在")
                return@launch
            }
            _state.value = CollectionState(
                loading = false,
                collection = info,
                projects = projects?.lists ?: emptyList(),
                page = projects?.page ?: 1,
                totalPage = projects?.totalPage ?: 1,
            )
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loadingMore || s.page >= s.totalPage) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            runCatching { api.collectionProjects(target(), page = s.page + 1, pageSize = 24) }
                .onSuccess { paged ->
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        projects = s.projects + paged.lists,
                        page = paged.page,
                        totalPage = paged.totalPage,
                    )
                }
                .onFailure { _state.value = _state.value.copy(loadingMore = false) }
        }
    }
}

@Composable
fun CollectionDetailScreen(
    vm: CollectionViewModel,
    onBack: () -> Unit,
    onProject: (String) -> Unit,
    columns: Int = 2,
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(vm) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text(state.collection?.name ?: "合集", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.collection?.let { collection ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(Modifier.padding(bottom = 8.dp)) {
                            Text(collection.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (collection.introduction.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Html.toPlain(collection.introduction),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${collection.totalProjects} 个工程 · ${collection.views} 次浏览",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
                items(state.projects, key = { it.uuid }) { p ->
                    ProjectGridCard(p, onClick = { onProject(p.uuid) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreFooter(state.loadingMore, state.page >= state.totalPage)
                }
            }
        }
    }
}
