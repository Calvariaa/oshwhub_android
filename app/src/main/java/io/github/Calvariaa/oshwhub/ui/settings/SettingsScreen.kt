package io.github.Calvariaa.oshwhub.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.Calvariaa.oshwhub.data.SettingsStore
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val themeMode by settings.themeMode.collectAsState(initial = "system")
    val dynamic by settings.dynamicColor.collectAsState(initial = true)
    val pureBlack by settings.pureBlack.collectAsState(initial = false)
    val columns by settings.gridColumns.collectAsState(initial = 2)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("设置", style = MaterialTheme.typography.titleMedium)
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("外观")
            Text("主题", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEachIndexed { index, (key, label) ->
                    SegmentedButton(
                        selected = themeMode == key,
                        onClick = { scope.launch { settings.setThemeMode(key) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = 3),
                    ) { Text(label, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(12.dp))
            SwitchRow("动态取色 (Material You)", dynamic) { scope.launch { settings.setDynamicColor(it) } }
            SwitchRow("深色模式使用纯黑", pureBlack) { scope.launch { settings.setPureBlack(it) } }

            Spacer(Modifier.height(8.dp))
            Text("列表列数：${columns}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = columns.toFloat(),
                onValueChange = { scope.launch { settings.setGridColumns(it.roundToInt()) } },
                valueRange = 1f..3f,
                steps = 1,
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle("数据")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("清除本地浏览历史", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearHistory) { Text("清除") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("清除搜索历史", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { settings.clearSearchHistory() } }) { Text("清除") }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
            SectionTitle("关于")
            Text(
                "立创开源 (oshwhub) 第三方客户端\n" +
                    "· 数据全部来自 https://oshwhub.com 的 web 接口\n" +
                    "· 登录走 passport.jlc.com 的 CAS，密码经 SM2 加密后提交\n" +
                    "· 本应用不收集任何个人数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
