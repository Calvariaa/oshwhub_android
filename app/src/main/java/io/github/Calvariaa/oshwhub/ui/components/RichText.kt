package io.github.Calvariaa.oshwhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.Calvariaa.oshwhub.util.Html
import io.github.Calvariaa.oshwhub.util.Url

/**
 * oshwhub 的正文有三种形态：
 *  - 编辑器产出的 markdown（工程详情大多是这样）
 *  - 后台富文本 HTML（技术文章、公告、活动规则）
 *  - 纯文本
 * [RichText] 负责判定并派发到对应渲染器。
 */
@Composable
fun RichText(
    raw: String,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null,
) {
    if (raw.isBlank()) return
    if (Html.looksLikeHtml(raw)) {
        HtmlText(raw, modifier, onImageClick)
    } else {
        MarkdownText(raw, modifier, onImageClick)
    }
}

// ---------------------------------------------------------------------------
// HTML 块解析
// ---------------------------------------------------------------------------

private sealed interface HtmlBlock {
    data class Para(val html: String) : HtmlBlock
    data class Heading(val level: Int, val html: String) : HtmlBlock
    data class Image(val url: String, val alt: String) : HtmlBlock
    data class Code(val code: String) : HtmlBlock
    data class Quote(val html: String) : HtmlBlock
    data class ListBlock(val items: List<String>, val ordered: Boolean) : HtmlBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : HtmlBlock
    data object Divider : HtmlBlock
}

private val SCRIPT_STYLE = Regex("""<(script|style)\b[\s\S]*?</\1\s*>""", RegexOption.IGNORE_CASE)
private val TABLE_BLOCK = Regex("""<table\b[^>]*>([\s\S]*?)</table\s*>""", RegexOption.IGNORE_CASE)
private val TABLE_ROW = Regex("""<tr\b[^>]*>([\s\S]*?)</tr\s*>""", RegexOption.IGNORE_CASE)
private val TABLE_CELL = Regex("""<(t[dh])\b[^>]*>([\s\S]*?)</t[dh]\s*>""", RegexOption.IGNORE_CASE)
private val PRE_BLOCK = Regex("""<pre\b[^>]*>([\s\S]*?)</pre\s*>""", RegexOption.IGNORE_CASE)
private val TOKEN = Regex("""<\s*(/?)\s*([a-zA-Z][a-zA-Z0-9]*)((?:"[^"]*"|'[^']*'|[^>"'])*)>""")
private val ATTR = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*=\s*(?:"([^"]*)"|'([^']*)')""")
private val WS = Regex("""[ \t\r\n\u00a0]+""")

private const val PRE_MARK = '\u0001'
private const val TABLE_MARK = '\u0003'
private const val BR_MARK = '\u0002'

private fun attrsOf(raw: String): Map<String, String> =
    ATTR.findAll(raw).associate { m ->
        m.groupValues[1].lowercase() to (m.groupValues[2].ifEmpty { m.groupValues[3] })
    }

private fun stripTags(raw: String): String =
    raw.replace(Regex("""<[^>]+>"""), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")

private fun normalize(text: String): String =
    text.replace(WS, " ").replace(BR_MARK, '\n').trim()

/** 表格转成「表头 + 行」结构；单元格保留内联 HTML 以便加粗/链接生效 */
private fun parseTable(inner: String): HtmlBlock.Table {
    val rows = mutableListOf<List<Pair<Boolean, String>>>()
    for (rowMatch in TABLE_ROW.findAll(inner)) {
        val cells = TABLE_CELL.findAll(rowMatch.groupValues[1]).map { cell ->
            val isHeader = cell.groupValues[1].equals("th", ignoreCase = true)
            isHeader to normalize(cell.groupValues[2].trim())
        }.toList()
        if (cells.isNotEmpty()) rows += cells
    }
    if (rows.isEmpty()) return HtmlBlock.Table(emptyList(), emptyList())
    val first = rows.first()
    val header = if (first.all { it.first }) first.map { it.second } else emptyList()
    val body = if (header.isEmpty()) rows.map { r -> r.map { it.second } } else rows.drop(1).map { r -> r.map { it.second } }
    return HtmlBlock.Table(header, body)
}

/** 把 HTML 切成「段落 / 标题 / 图片 / 代码 / 引用 / 列表 / 表格 / 分割线」块 */
private fun parseHtmlBlocks(input: String): List<HtmlBlock> {
    val preBlocks = mutableListOf<String>()
    val tables = mutableListOf<HtmlBlock.Table>()
    var s = SCRIPT_STYLE.replace(input, "")
    s = TABLE_BLOCK.replace(s) { m ->
        tables += parseTable(m.groupValues[1])
        "$TABLE_MARK${tables.lastIndex}$TABLE_MARK"
    }
    s = PRE_BLOCK.replace(s) { m ->
        preBlocks += stripTags(m.groupValues[1]).trim('\n', '\r', ' ')
        "$PRE_MARK${preBlocks.lastIndex}$PRE_MARK"
    }

    val blocks = mutableListOf<HtmlBlock>()
    val buf = StringBuilder()
    var kind = 0 // 0=段落 1=标题 2=引用 3=列表项
    var level = 3
    var ordered = false
    var olDepth = 0
    var last = 0

    fun flush() {
        val text = normalize(buf.toString())
        buf.setLength(0)
        if (text.isEmpty()) return
        when (kind) {
            1 -> blocks += HtmlBlock.Heading(level, text)
            2 -> blocks += HtmlBlock.Quote(text)
            3 -> blocks += HtmlBlock.ListBlock(listOf(text), ordered)
            else -> blocks += HtmlBlock.Para(text)
        }
        kind = 0
    }

    fun flushPending(from: Int, to: Int) {
        // 文本区间里可能夹着预抽出的代码块 / 表格占位符
        var cursor = from
        while (cursor < to) {
            val preAt = s.indexOf(PRE_MARK, cursor).let { if (it in cursor until to) it else -1 }
            val tableAt = s.indexOf(TABLE_MARK, cursor).let { if (it in cursor until to) it else -1 }
            val mark = when {
                preAt < 0 -> tableAt
                tableAt < 0 -> preAt
                else -> minOf(preAt, tableAt)
            }
            if (mark < 0) break
            val isTable = mark == tableAt
            val markChar = if (isTable) TABLE_MARK else PRE_MARK
            val close = s.indexOf(markChar, mark + 1)
            if (close < 0 || close >= to) break
            buf.append(s, cursor, mark)
            flush()
            val idx = s.substring(mark + 1, close).toIntOrNull()
            if (isTable) {
                if (idx != null && idx in tables.indices) blocks += tables[idx]
            } else {
                if (idx != null && idx in preBlocks.indices) blocks += HtmlBlock.Code(preBlocks[idx])
            }
            cursor = close + 1
        }
        buf.append(s, cursor, to)
    }

    for (m in TOKEN.findAll(s)) {
        flushPending(last, m.range.first)
        last = m.range.last + 1
        val closing = m.groupValues[1] == "/"
        val name = m.groupValues[2].lowercase()
        val attrs = attrsOf(m.groupValues[3])

        if (name == "br") {
            buf.append(BR_MARK)
            continue
        }
        if (name == "img") {
            flush()
            val src = attrs["src"] ?: attrs["data-src"] ?: attrs["data-original"]
            if (!src.isNullOrBlank()) blocks += HtmlBlock.Image(src, attrs["alt"].orEmpty())
            continue
        }
        if (name == "hr") {
            flush()
            blocks += HtmlBlock.Divider
            continue
        }

        val isBlockTag = name in setOf(
            "p", "div", "section", "article", "figure", "figcaption", "header", "footer",
            "table", "thead", "tbody", "tr", "td", "th", "ul", "ol", "li",
            "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "dl", "dt", "dd",
        )

        if (closing) {
            if (isBlockTag) flush()
            continue
        }

        when (name) {
            "ol" -> { flush(); ordered = true; olDepth++ }
            "ul" -> { flush(); ordered = false }
            "li" -> { flush(); kind = 3 }
            "blockquote" -> { flush(); kind = 2 }
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                flush()
                kind = 1
                level = name[1].digitToInt()
            }
            "p", "div", "section", "article", "figure", "figcaption",
            "table", "tr", "td", "th", "dl", "dt", "dd",
            -> flush()
            else -> buf.append(m.value) // 内联标签原样保留，交给 fromHtml 处理样式
        }
    }
    flushPending(last, s.length)
    flush()
    if (ordered) olDepth = 0

    // 相邻的列表项合并成一个列表（服务端 HTML 里通常是一串独立 <li>）
    val merged = mutableListOf<HtmlBlock>()
    var i = 0
    while (i < blocks.size) {
        val b = blocks[i]
        if (b is HtmlBlock.ListBlock) {
            val items = mutableListOf<String>()
            var ord = b.ordered
            var j = i
            while (j < blocks.size && blocks[j] is HtmlBlock.ListBlock) {
                val item = blocks[j] as HtmlBlock.ListBlock
                items += item.items
                ord = item.ordered
                j++
            }
            merged += HtmlBlock.ListBlock(items, ord)
            i = j
        } else {
            merged += b
            i++
        }
    }
    return merged
}

// ---------------------------------------------------------------------------
// 渲染
// ---------------------------------------------------------------------------

@Composable
private fun htmlInline(html: String): AnnotatedString {
    val link = MaterialTheme.colorScheme.primary
    val bold = MaterialTheme.colorScheme.onSurface
    val linkStyles = remember(link) {
        TextLinkStyles(style = SpanStyle(color = link, fontWeight = FontWeight.Medium))
    }
    return remember(html, linkStyles, bold) {
        runCatching {
            AnnotatedString.fromHtml(html, linkStyles = linkStyles)
        }.getOrElse { AnnotatedString(Html.toPlain(html)) }
    }
}

/**
 * 立创后台富文本 HTML 渲染器：段落、标题、图片、代码块、引用、列表、分割线。
 * 行内 `<b>/<i>/<a>/<code>/<span style>` 交给 `AnnotatedString.fromHtml`。
 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null,
) {
    val blocks = remember(html) { parseHtmlBlocks(html) }
    Column(modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is HtmlBlock.Heading -> {
                    Spacer(Modifier.height(if (block.level <= 2) 14.dp else 8.dp))
                    Text(
                        text = htmlInline(block.html),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                is HtmlBlock.Para -> {
                    Text(
                        text = htmlInline(block.html),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                is HtmlBlock.Image -> {
                    val src = Url.image(block.url)
                    if (src != null) {
                        val shape = RoundedCornerShape(14.dp)
                        Spacer(Modifier.height(4.dp))
                        AsyncImage(
                            model = src,
                            contentDescription = block.alt,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        )
                        if (block.alt.isNotBlank()) {
                            Text(
                                block.alt,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }

                is HtmlBlock.Code -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp)
                    ) {
                        SelectionContainer {
                            Text(
                                block.code,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is HtmlBlock.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = htmlInline(block.html),
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is HtmlBlock.ListBlock -> {
                    Column {
                        block.items.forEachIndexed { index, item ->
                            Row(
                                Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    if (block.ordered) "${index + 1}." else "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(20.dp),
                                )
                                Text(
                                    text = htmlInline(item),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                HtmlBlock.Divider -> HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                is HtmlBlock.Table -> TableBlock(block)
            }
        }
    }
}

/** 富文本里的 `<table>`：按列均分，表头加底色，行间细线 */
@Composable
private fun TableBlock(table: HtmlBlock.Table) {
    val columns = remember(table) {
        maxOf(
            table.header.size,
            table.rows.maxOfOrNull { it.size } ?: 0,
        ).coerceAtLeast(1)
    }
    if (columns == 1 && table.header.isEmpty() && table.rows.isEmpty()) return
    val border = MaterialTheme.colorScheme.outlineVariant
    val headBg = MaterialTheme.colorScheme.surfaceContainerHigh

    @Composable
    fun row(cells: List<String>, header: Boolean) {
        Row(Modifier.fillMaxWidth()) {
            for (c in 0 until columns) {
                val cell = cells.getOrNull(c).orEmpty()
                Box(
                    Modifier
                        .weight(1f)
                        .background(if (header) headBg else MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = htmlInline(cell),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
                if (c < columns - 1) {
                    Box(Modifier.width(1.dp).heightIn(min = 36.dp).background(border))
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(border)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(1.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (table.header.isNotEmpty()) {
                row(table.header, header = true)
                Box(Modifier.fillMaxWidth().height(1.dp).background(border))
            }
            table.rows.forEachIndexed { index, cells ->
                row(cells, header = false)
                if (index < table.rows.lastIndex) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(border))
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}
