package io.github.Calvariaa.oshwhub.ui.project

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import okhttp3.HttpUrl.Companion.toHttpUrl
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.SessionManager
import io.github.Calvariaa.oshwhub.data.model.Attachment
import io.github.Calvariaa.oshwhub.data.model.BomRow
import io.github.Calvariaa.oshwhub.data.model.BoardInfo
import io.github.Calvariaa.oshwhub.data.model.Comment
import io.github.Calvariaa.oshwhub.data.model.ContentStatus
import io.github.Calvariaa.oshwhub.data.model.ProImages
import io.github.Calvariaa.oshwhub.data.model.ProjectDetail
import io.github.Calvariaa.oshwhub.data.model.ProjectSummary
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.ApiException
import io.github.Calvariaa.oshwhub.ui.components.Avatar
import io.github.Calvariaa.oshwhub.ui.components.CommentComposer
import io.github.Calvariaa.oshwhub.ui.components.CommentItem
import io.github.Calvariaa.oshwhub.ui.components.EmptyState
import io.github.Calvariaa.oshwhub.ui.components.ErrorState
import io.github.Calvariaa.oshwhub.ui.components.LoadingBox
import io.github.Calvariaa.oshwhub.ui.components.MarkdownText
import io.github.Calvariaa.oshwhub.ui.components.RichText
import io.github.Calvariaa.oshwhub.ui.components.ProjectRowCard
import io.github.Calvariaa.oshwhub.ui.components.ProjectThumb
import io.github.Calvariaa.oshwhub.ui.components.SectionHeader
import io.github.Calvariaa.oshwhub.ui.components.StatChip
import io.github.Calvariaa.oshwhub.util.Fmt
import io.github.Calvariaa.oshwhub.util.Html
import io.github.Calvariaa.oshwhub.util.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement

data class ProjectState(
    val loading: Boolean = true,
    val error: String? = null,
    val project: ProjectDetail? = null,
    val proImages: ProImages = ProImages(),
    val status: ContentStatus = ContentStatus(),
    val comments: List<Comment> = emptyList(),
    val commentTotal: Int = 0,
    val commentsLoading: Boolean = false,
    val related: List<ProjectSummary> = emptyList(),
    val tagNames: Map<String, String> = emptyMap(),
    val sending: Boolean = false,
)

class ProjectViewModel(
    private val api: OshApi,
    private val session: SessionManager,
    private val uuid: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectState())
    val state: StateFlow<ProjectState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            runCatching { api.project(uuid) }
                .onSuccess { project ->
                    _state.value = _state.value.copy(loading = false, project = project)
                    api.addViewCount(uuid)
                    if (session.isLoggedIn) api.addBrowseHistory(uuid)
                    loadExtras(project)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(loading = false, error = (e as? ApiException)?.message ?: e.message)
                }
        }
    }

    private fun loadExtras(project: ProjectDetail) {
        viewModelScope.launch {
            val tags = runCatching { api.projectTags().flatMap { listOf(it) + it.children } }.getOrDefault(emptyList())
            _state.value = _state.value.copy(tagNames = tags.associate { it.uuid to it.name })
        }
        viewModelScope.launch {
            val images = runCatching { api.proImages(uuid) }.getOrNull() ?: ProImages()
            _state.value = _state.value.copy(proImages = images)
        }
        viewModelScope.launch {
            val status = runCatching { api.contentStatus("project", uuid) }.getOrNull() ?: ContentStatus()
            _state.value = _state.value.copy(status = status)
        }
        viewModelScope.launch {
            val related = runCatching {
                api.relatedProjects(uuid, project.project_tags.take(3), pageSize = 6).lists
            }.getOrDefault(emptyList())
            _state.value = _state.value.copy(related = related)
        }
        loadComments()
    }

    fun loadComments() {
        viewModelScope.launch {
            _state.value = _state.value.copy(commentsLoading = true)
            runCatching { api.comments("project", uuid, page = 1, pageSize = 30) }
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

    fun toggleLike() {
        if (!session.isLoggedIn) { session.notify("请先登录"); return }
        viewModelScope.launch {
            val current = _state.value.status.like
            val project = _state.value.project ?: return@launch
            runCatching { if (current) api.unlike("project", uuid) else api.like("project", uuid) }
                .onSuccess {
                    val delta = if (current) -1 else 1
                    _state.value = _state.value.copy(
                        status = _state.value.status.copy(like = !current),
                        project = project.copy(count = project.count.copy(like = (project.count.like + delta).coerceAtLeast(0))),
                    )
                }
                .onFailure { e -> session.notify((e as? ApiException)?.message ?: "操作失败") }
        }
    }

    fun toggleCollect() {
        if (!session.isLoggedIn) { session.notify("请先登录"); return }
        viewModelScope.launch {
            val current = _state.value.status.collect
            runCatching { if (current) api.uncollect("project", uuid) else api.collect("project", uuid) }
                .onSuccess {
                    _state.value = _state.value.copy(status = _state.value.status.copy(collect = !current))
                    session.notify(if (current) "已取消收藏" else "已收藏")
                }
                .onFailure { e -> session.notify((e as? ApiException)?.message ?: "操作失败") }
        }
    }

    fun sendComment(content: String, onDone: () -> Unit) {
        if (!session.isLoggedIn) { session.notify("请先登录"); return }
        if (content.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true)
            runCatching { api.createComment("project", uuid, content.trim()) }
                .onSuccess {
                    _state.value = _state.value.copy(sending = false)
                    onDone()
                    loadComments()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(sending = false)
                    session.notify((e as? ApiException)?.message ?: "评论失败")
                }
        }
    }

    fun parseBom(board: BoardInfo): List<BomRow> {
        val raw = board.pcb_info?.boms?.boms?.takeIf { it.isNotBlank() }
            ?: board.schematic?.boms?.boms?.takeIf { it.isNotBlank() }
            ?: return emptyList()
        return runCatching { api.json.decodeFromJsonElement<List<BomRow>>(api.json.parseToJsonElement(raw)) }
            .getOrDefault(emptyList())
    }

    /**
     * 附件下载地址。`Attachment.src` 只在 CDN 域名下有效
     * （`/api/common/project/{uuid}/attachments/{att}` 是 404 陷阱），
     * src 缺失时才退回站点路径。
     */
    fun attachmentUrl(att: Attachment): String =
        Url.file(att.src) ?: "${ApiClient.WEB_BASE}/api/common/project/$uuid/attachments/${att.uuid}"

    fun attachmentCookie(url: String): String = api.client.cookieStore.headerFor(url.toHttpUrl())
}

@Composable
fun ProjectDetailScreen(
    vm: ProjectViewModel,
    onBack: () -> Unit,
    onUser: (String) -> Unit,
    onProject: (String) -> Unit,
    onLogin: () -> Unit,
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(vm) { vm.load() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
                Text(
                    state.project?.name ?: "工程详情",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    val link = Url.project(state.project?.path)
                    share(context, link)
                }) { Icon(Icons.Filled.Share, "分享") }
                Box {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("浏览器打开") },
                            onClick = {
                                menuOpen = false
                                openUrl(context, Url.project(state.project?.path))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("复制链接") },
                            onClick = {
                                menuOpen = false
                                copy(context, Url.project(state.project?.path))
                            },
                        )
                        if (state.project?.text_editor_type == "markdown") {
                            DropdownMenuItem(
                                text = { Text("复制正文 Markdown") },
                                onClick = {
                                    menuOpen = false
                                    copy(context, state.project?.content ?: "")
                                },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        },
        bottomBar = {
            val project = state.project
            if (project != null) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(onClick = { vm.toggleLike() }, shape = RoundedCornerShape(50)) {
                            Icon(
                                if (state.status.like) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("${project.count.like}")
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = { vm.toggleCollect() }, shape = RoundedCornerShape(50)) {
                            Icon(
                                if (state.status.collect) Icons.Outlined.Star else Icons.Outlined.StarOutline,
                                null,
                                Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("收藏")
                        }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { tab = 3 },
                            shape = RoundedCornerShape(50),
                        ) { Text("评论 ${project.comments_count}") }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> LoadingBox(Modifier.fillMaxSize())
                state.error != null -> ErrorState(state.error!!, onRetry = vm::load)
                state.project == null -> EmptyState("工程不存在")
                else -> {
                    val project = state.project!!
                    Column(Modifier.fillMaxSize()) {
                        LazyColumn(Modifier.weight(1f)) {
                            item { ProjectHeader(project, state, onUser, onProject) }
                            item {
                                SecondaryTabRow(selectedTabIndex = tab) {
                                    listOf("简介", "电路/BOM", "附件", "评论", "推荐").forEachIndexed { index, label ->
                                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
                                    }
                                }
                            }
                            when (tab) {
                                0 -> item {
                                    Column(Modifier.padding(16.dp)) {
                                        if (project.content.isBlank()) {
                                            Text("作者还没有填写正文", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        } else {
                                            RichText(project.content)
                                        }
                                    }
                                }
                                1 -> items(state.proImages.boards) { board ->
                                    BomSection(
                                        board = board,
                                        rows = vm.parseBom(board),
                                        tagNames = state.tagNames,
                                    )
                                }
                                2 -> {
                                    if (project.attachments.isEmpty() && project.attachments_3d.isEmpty()) {
                                        item { EmptyState("该工程没有附件") }
                                    } else {
                                        items(project.attachments) { att ->
                                            AttachmentRow(att) {
                                                val url = vm.attachmentUrl(att)
                                                downloadWithCookies(
                                                    context = context,
                                                    url = url,
                                                    fileName = att.name.ifBlank { "attachment_${att.uuid}" },
                                                    cookie = vm.attachmentCookie(url),
                                                    folder = project.name,
                                                )
                                            }
                                        }
                                        if (project.attachments_3d.isNotEmpty()) {
                                            item { SectionHeader("3D 模型 / 结构文件") }
                                            items(project.attachments_3d) { att ->
                                                AttachmentRow(att) {
                                                    val url = vm.attachmentUrl(att)
                                                    downloadWithCookies(
                                                        context = context,
                                                        url = url,
                                                        fileName = att.name.ifBlank { "model_${att.uuid}" },
                                                        cookie = vm.attachmentCookie(url),
                                                        folder = project.name,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                3 -> {
                                    item {
                                        CommentComposer(
                                            sending = state.sending,
                                            onSend = { text, done -> vm.sendComment(text, done) },
                                        )
                                    }
                                    if (state.commentsLoading) item { LoadingBox() }
                                    if (state.comments.isEmpty() && !state.commentsLoading) {
                                        item { EmptyState("还没有评论", "来说两句吧") }
                                    }
                                    items(state.comments) { comment -> CommentItem(comment, onUser) }
                                }
                                else -> {
                                    if (state.related.isEmpty()) {
                                        item { EmptyState("暂无相关推荐") }
                                    } else {
                                        items(state.related, key = { it.uuid }) { p ->
                                            ProjectRowCard(p, onClick = { onProject(p.uuid) })
                                        }
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectHeader(
    project: ProjectDetail,
    state: ProjectState,
    onUser: (String) -> Unit,
    onProject: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                if (project.introduction.isNotBlank()) {
                    Text(
                        Html.toPlain(project.introduction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(project.license.ifBlank { "开源许可：未标注" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(project.origin, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(Fmt.date(project.oshwhub_publish_at ?: project.created_at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            ProjectThumb(
                url = project.thumb,
                modifier = Modifier
                    .size(width = 128.dp, height = 96.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            StatChip(Fmt.count(project.count.views), "浏览")
            StatChip(Fmt.count(project.count.like), "点赞")
            StatChip(Fmt.count(project.count.star), "收藏")
            StatChip(Fmt.count(project.count.fork), "复刻")
            StatChip("${project.comments_count}", "评论")
            project.reproduction_cost?.let {
                if (it.total_cost > 0) StatChip(Fmt.money(it.total_cost), "成本")
            }
        }

        Spacer(Modifier.height(10.dp))
        Surface(
            onClick = { onUser(project.owner.uuid) },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(50),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(project.owner.avatar, size = 28)
                Spacer(Modifier.width(8.dp))
                Text(
                    project.owner.nickname.ifBlank { project.owner.username },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (project.owner.team) {
                    Spacer(Modifier.width(6.dp))
                    Text("团队", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        if (project.project_tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                project.project_tags.forEach { uuid ->
                    val name = state.tagNames[uuid] ?: return@forEach
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Text(
                            name,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun BomSection(board: BoardInfo, rows: List<BomRow>, tagNames: Map<String, String>) {
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(board.name.ifBlank { board.schematic?.display_name ?: "电路板" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text("${rows.sumOf { it.quantity }} 个元件", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        val thumb = board.pcb_info?.thumb ?: board.schematic?.documents?.firstOrNull()?.thumb
        if (!thumb.isNullOrBlank()) {
            ProjectThumb(
                url = thumb,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Spacer(Modifier.height(8.dp))
        }
        if (rows.isEmpty()) {
            Text("该板没有 BOM 数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            BomTable(rows)
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun BomTable(rows: List<BomRow>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(8.dp)) {
            BomCell("编号", 0.6f, true)
            BomCell("名称/规格", 1.4f, true)
            BomCell("位号", 1.4f, true)
            BomCell("封装", 1.3f, true)
            BomCell("数量", 0.5f, true)
            BomCell("立创编号", 1.0f, true)
        }
        rows.forEachIndexed { index, row ->
            if (index % 2 == 0) {
                Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(8.dp)) {
                    BomRowCells(row)
                }
            } else {
                Row(Modifier.padding(8.dp)) { BomRowCells(row) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BomRowCells(row: BomRow) {
    BomCell(row.id.toString(), 0.6f, false)
    BomCell(row.name, 1.4f, false)
    BomCell(row.designator, 1.4f, false)
    BomCell(row.footprint, 1.3f, false)
    BomCell(row.quantity.toString(), 0.5f, false)
    BomCell(row.supplierPart.ifBlank { "-" }, 1.0f, false)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BomCell(text: String, weight: Float, header: Boolean) {
    Text(
        text,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 2.dp),
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun AttachmentRow(att: Attachment, onDownload: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onDownload)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                (att.ext.ifBlank { "file" }).take(4).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(att.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${Fmt.fileSize(att.size)} · ${att.downloadCount} 次下载",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Filled.Download, "下载", tint = MaterialTheme.colorScheme.primary)
    }
}

// ------------------------------------------------------------------ helpers --

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("oshwhub", text))
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "分享"))
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** 附件下载：带上当前登录 Cookie，交给系统下载器，落到 Download/oshwhub/<工程名>/ */
private fun downloadWithCookies(context: Context, url: String, fileName: String, cookie: String, folder: String) {
    val safeFolder = folder.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "oshwhub" }
    val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_").take(120).ifBlank { "attachment" }
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(safeName)
        .setDescription("来自 立创开源硬件平台")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "oshwhub/$safeFolder/$safeName")
        .addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/155.0.0.0 Safari/537.36")
        .addRequestHeader("Referer", "https://oshwhub.com/")
    if (cookie.isNotBlank()) request.addRequestHeader("Cookie", cookie)
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    runCatching { dm.enqueue(request) }
        .onFailure {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        }
}
