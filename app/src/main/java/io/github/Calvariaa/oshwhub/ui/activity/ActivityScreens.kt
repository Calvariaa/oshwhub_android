package io.github.Calvariaa.oshwhub.ui.activity

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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.model.Activity
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.MarkdownText
import io.github.Calvariaa.oshwhub.ui.components.RichText
import io.github.Calvariaa.oshwhub.ui.components.ProjectThumb
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivityListState(
    val loading: Boolean = true,
    val error: String? = null,
    val activities: List<Activity> = emptyList(),
)

class ActivityViewModel(private val api: OshApi, private val uuid: String? = null) : ViewModel() {
    private val _state = MutableStateFlow(ActivityListState())
    val state: StateFlow<ActivityListState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.competitions(pageSize = 30).lists }
                .onSuccess { list ->
                    _state.value = ActivityListState(loading = false, activities = list)
                }
                .onFailure { e -> _state.value = _state.value.copy(loading = false, error = e.message) }
        }
    }

    fun find(uuid: String): Activity? = _state.value.activities.firstOrNull { it.uuid == uuid }
}

@Composable
fun ActivityListScreen(vm: ActivityViewModel, onBack: () -> Unit, onActivity: (Activity) -> Unit) {
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { if (state.activities.isEmpty()) vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("活动与赛事", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            state.activities.isEmpty() -> EmptyState("暂无活动")
            else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.activities, key = { it.uuid }) { activity ->
                    ActivityCard(activity) { onActivity(activity) }
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(activity: Activity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        onClick = onClick,
    ) {
        Column {
            ProjectThumb(
                url = activity.project_default_image,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
            )
            Column(Modifier.padding(14.dp)) {
                Text(activity.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    Html.toPlain(activity.introduction),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Fmt.date(activity.competing_start_time)} ~ ${Fmt.date(activity.competing_end_time)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun ActivityDetailScreen(vm: ActivityViewModel, uuid: String, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { if (state.activities.isEmpty()) vm.load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("活动详情", style = MaterialTheme.typography.titleMedium)
        }
        when {
            state.loading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
            else -> {
                val activity = state.activities.firstOrNull { it.uuid == uuid }
                if (activity == null) {
                    EmptyState("活动不存在")
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                    ) {
                        if (activity.project_default_image.isNotBlank()) {
                            ProjectThumb(
                                url = activity.project_default_image,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .then(Modifier),
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Text(activity.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "${Fmt.time(activity.competing_start_time)} ~ ${Fmt.time(activity.competing_end_time)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        RichText(activity.introduction)
                        if (activity.activityRules.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text("活动规则", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            RichText(activity.activityRules)
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val url = if (activity.path.isNotBlank())
                                    "https://oshwhub.com/activities/${activity.path}" else "https://oshwhub.com/activities"
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, url.toUri())) }
                            },
                            shape = RoundedCornerShape(50),
                        ) {
                            Icon(Icons.Filled.OpenInNew, null, Modifier.height(18.dp))
                            Spacer(Modifier.height(0.dp))
                            Text(" 在浏览器查看活动主页")
                        }
                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

private fun Any.height(value: androidx.compose.ui.unit.Dp): Modifier = Modifier.height(value)
