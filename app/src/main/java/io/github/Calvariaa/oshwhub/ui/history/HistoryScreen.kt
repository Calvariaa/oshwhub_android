package io.github.Calvariaa.oshwhub.ui.history

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.Calvariaa.oshwhub.data.LocalHistoryStore
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ProjectThumb
import io.github.Calvariaa.oshwhub.util.Fmt

@Composable
fun HistoryScreen(
    history: LocalHistoryStore,
    onBack: () -> Unit,
    onProject: (String) -> Unit,
) {
    var version by remember { mutableIntStateOf(0) }
    val entries = remember(version) { history.list() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("浏览历史", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = {
                    history.clear()
                    version++
                }) { Text("清空") }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (entries.isEmpty()) {
            EmptyState("还没有浏览记录", "看过的工程会出现在这里")
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                items(entries, key = { it.uuid }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onProject(entry.uuid) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProjectThumb(
                            url = entry.thumb,
                            modifier = Modifier
                                .width(96.dp)
                                .height(66.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Fmt.time(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.CHINA)
                                    .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                                    .format(java.util.Date(entry.at))),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
