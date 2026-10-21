package io.github.Calvariaa.oshwhub.ui.explore

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.model.Tag
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadMoreFooter
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.ProjectGridCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExploreState(
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val tags: List<Tag> = emptyList(),
    val expandTags: Boolean = false,
    val projects: List<ProjectSummary> = emptyList(),
    val page: Int = 0,
    val totalPage: Int = 1,
    val endReached: Boolean = false,
    val sortType: String = "default",
    val selectedTags: List<String> = emptyList(),
    val origin: String = "all",
)

class ExploreViewModel(private val api: OshApi) : ViewModel() {

    private val _state = MutableStateFlow(ExploreState())
    val state: StateFlow<ExploreState> = _state.asStateFlow()

    private var loadedTags = false

    fun start(tag: String?) {
        if (!loadedTags) {
            loadedTags = true
            viewModelScope.launch {
                val tags = runCatching { api.projectTags() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(tags = tags)
                if (!tag.isNullOrBlank()) {
                    _state.value = _state.value.copy(selectedTags = listOf(tag))
                }
                loadFirstPage()
            }
        } else if (_state.value.projects.isEmpty()) {
            loadFirstPage()
        }
    }

    fun setSort(type: String) {
        if (_state.value.sortType == type) return
        _state.value = _state.value.copy(sortType = type)
        loadFirstPage()
    }

    fun toggleTag(uuid: String) {
        val current = _state.value.selectedTags
        val next = if (current.contains(uuid)) current - uuid else current + uuid
        _state.value = _state.value.copy(selectedTags = next)
        loadFirstPage()
    }

    fun clearTags() {
        _state.value = _state.value.copy(selectedTags = emptyList())
        loadFirstPage()
    }

    fun toggleExpandTags() {
        _state.value = _state.value.copy(expandTags = !_state.value.expandTags)
    }

    fun loadFirstPage() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching {
                api.projects(
                    page = 1,
                    pageSize = 20,
                    type = _state.value.sortType,
                    origin = _state.value.origin,
                    projectTags = _state.value.selectedTags,
                )
            }.onSuccess { paged ->
                _state.value = _state.value.copy(
                    loading = false,
                    projects = paged.lists,
                    page = paged.page,
                    totalPage = paged.totalPage,
                    endReached = paged.page >= paged.totalPage,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(loading = false, error = e.message ?: "加载失败")
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        viewModelScope.launch {
            _state.value = s.copy(loadingMore = true)
            runCatching {
                api.projects(
                    page = s.page + 1,
                    pageSize = 20,
                    type = s.sortType,
                    origin = s.origin,
                    projectTags = s.selectedTags,
                )
            }.onSuccess { paged ->
                val merged = (s.projects + paged.lists).distinctBy { it.uuid }
                _state.value = _state.value.copy(
                    loadingMore = false,
                    projects = merged,
                    page = paged.page,
                    totalPage = paged.totalPage,
                    endReached = paged.page >= paged.totalPage,
                )
            }.onFailure {
                _state.value = _state.value.copy(loadingMore = false)
            }
        }
    }
}

@Composable
fun ExploreScreen(
    vm: ExploreViewModel,
    initialTag: String?,
    columns: Int,
    onProject: (String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) { vm.start(initialTag) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { last ->
                if (last >= state.projects.size - 4) vm.loadMore()
            }
    }

    Column(Modifier.fillMaxSize()) {
        SortBar(
            sortType = state.sortType,
            onSort = vm::setSort,
            tags = state.tags,
            expand = state.expandTags,
            selected = state.selectedTags,
            onToggleExpand = vm::toggleExpandTags,
            onToggleTag = vm::toggleTag,
            onClear = vm::clearTags,
        )

        when {
            state.loading && state.projects.isEmpty() -> LoadingBox(Modifier.fillMaxSize())
            state.error != null && state.projects.isEmpty() -> ErrorState(state.error!!, onRetry = vm::loadFirstPage)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.projects, key = { it.uuid }) { project ->
                    ProjectGridCard(project, onClick = { onProject(project.uuid) })
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadMoreFooter(loading = state.loadingMore, endReached = state.endReached && state.projects.isNotEmpty())
                }
            }
        }
    }
}

@Composable
private fun SortBar(
    sortType: String,
    onSort: (String) -> Unit,
    tags: List<Tag>,
    expand: Boolean,
    selected: List<String>,
    onToggleExpand: () -> Unit,
    onToggleTag: (String) -> Unit,
    onClear: () -> Unit,
) {
    val sorts = listOf(
        "default" to "推荐",
        "new" to "最新",
        "hot" to "最热",
        "collect" to "收藏",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sorts.forEach { (key, label) ->
                FilterChip(
                    selected = sortType == key,
                    onClick = { onSort(key) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        if (tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(tags) { tag ->
                    FilterChip(
                        selected = selected.contains(tag.uuid),
                        onClick = { onToggleExpand() },
                        label = { Text(tag.name, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        }
        if (expand && tags.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Column(Modifier.padding(10.dp)) {
                    tags.forEach { tag ->
                        Text(
                            tag.name,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        tag.children.chunked(3).forEach { line ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                line.forEach { child ->
                                    FilterChip(
                                        selected = selected.contains(child.uuid),
                                        onClick = { onToggleTag(child.uuid) },
                                        label = { Text(child.name, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                    }
                    if (selected.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "清除筛选（${selected.size}）",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .fillMaxWidth(),
                        )
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item {
                    FilterChip(
                        selected = false,
                        onClick = onClear,
                        label = { Text("清除 ${selected.size}", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}
