package io.github.Calvariaa.oshwhub.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.Calvariaa.oshwhub.util.Url

private sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class ImageBlock(val alt: String, val url: String) : MdBlock
    data class CodeBlock(val lang: String, val code: String) : MdBlock
    data class Quote(val lines: List<String>) : MdBlock
    data class Bullets(val items: List<String>, val ordered: Boolean) : MdBlock
    data object Divider : MdBlock
}

private val imageOnly = Regex("""^!\[([^\]]*)]\(([^)\s]+)(?:\s+"[^"]*")?\)$""")
private val headingRe = Regex("""^(#{1,6})\s+(.*)$""")
private val bulletRe = Regex("""^[-*+]\s+(.*)$""")
private val orderedRe = Regex("""^(\d+)[.)]\s+(.*)$""")

private fun parseBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = markdown.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        val trimmed = line.trim()

        when {
            trimmed.isEmpty() -> i++

            trimmed.startsWith("```") -> {
                val lang = trimmed.removePrefix("```").trim()
                val buf = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    buf.appendLine(lines[i])
                    i++
                }
                i++
                blocks += MdBlock.CodeBlock(lang, buf.toString().trimEnd())
            }

            headingRe.matches(trimmed) -> {
                val m = headingRe.find(trimmed)!!
                blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2])
                i++
            }

            trimmed == "---" || trimmed == "***" || trimmed == "___" -> {
                blocks += MdBlock.Divider
                i++
            }

            imageOnly.matches(trimmed) -> {
                val m = imageOnly.find(trimmed)!!
                blocks += MdBlock.ImageBlock(m.groupValues[1], m.groupValues[2])
                i++
            }

            trimmed.startsWith(">") -> {
                val buf = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    buf += lines[i].trim().removePrefix(">").trim()
                    i++
                }
                blocks += MdBlock.Quote(buf)
            }

            bulletRe.matches(trimmed) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && bulletRe.matches(lines[i].trim())) {
                    items += bulletRe.find(lines[i].trim())!!.groupValues[1]
                    i++
                }
                blocks += MdBlock.Bullets(items, ordered = false)
            }

            orderedRe.matches(trimmed) -> {
                val items = mutableListOf<String>()
                while (i < lines.size && orderedRe.matches(lines[i].trim())) {
                    items += orderedRe.find(lines[i].trim())!!.groupValues[2]
                    i++
                }
                blocks += MdBlock.Bullets(items, ordered = true)
            }

            else -> {
                val buf = StringBuilder(line)
                i++
                while (i < lines.size) {
                    val next = lines[i].trim()
                    if (next.isEmpty() || next.startsWith("#") || next.startsWith("```") ||
                        next.startsWith(">") || bulletRe.matches(next) || orderedRe.matches(next) ||
                        next == "---" || imageOnly.matches(next)
                    ) break
                    buf.append('\n').append(lines[i].trimEnd())
                    i++
                }
                blocks += MdBlock.Paragraph(buf.toString())
            }
        }
    }
    return blocks
}

/** 极简 inline 渲染：**粗体** *斜体* `代码` [文本](链接) ~~删除线~~ */
@Composable
private fun inline(text: String, baseColor: Color = Color.Unspecified): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    return remember(text, linkColor, codeBg) {
        buildAnnotatedString {
            var i = 0
            val sb = StringBuilder()
            fun flush() {
                if (sb.isNotEmpty()) {
                    append(sb.toString()); sb.clear()
                }
            }
            while (i < text.length) {
                when {
                    text.startsWith("![", i) -> {
                        val close = text.indexOf(')', i)
                        if (close > 0) {
                            flush()
                            val label = text.substring(i + 2, text.indexOf(']', i).coerceAtLeast(i + 2))
                            withStyle(SpanStyle(color = linkColor)) { append(label) }
                            i = close + 1
                        } else { sb.append(text[i]); i++ }
                    }
                    text.startsWith("**", i) || text.startsWith("__", i) -> {
                        val marker = text.substring(i, i + 2)
                        val end = text.indexOf(marker, i + 2)
                        if (end > 0) {
                            flush()
                            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                            append(text.substring(i + 2, end))
                            pop()
                            i = end + 2
                        } else { sb.append(text[i]); i++ }
                    }
                    text.startsWith("~~", i) -> {
                        val end = text.indexOf("~~", i + 2)
                        if (end > 0) {
                            flush()
                            pushStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
                            append(text.substring(i + 2, end))
                            pop()
                            i = end + 2
                        } else { sb.append(text[i]); i++ }
                    }
                    text[i] == '`' -> {
                        val end = text.indexOf('`', i + 1)
                        if (end > 0) {
                            flush()
                            pushStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = codeBg,
                                )
                            )
                            append(text.substring(i + 1, end))
                            pop()
                            i = end + 1
                        } else { sb.append(text[i]); i++ }
                    }
                    text.startsWith("[", i) -> {
                        val closeBracket = text.indexOf(']', i)
                        if (closeBracket > 0 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                            val closeParen = text.indexOf(')', closeBracket)
                            if (closeParen > 0) {
                                flush()
                                val label = text.substring(i + 1, closeBracket)
                                val href = text.substring(closeBracket + 2, closeParen)
                                    .trim()
                                    .substringBefore(' ')
                                // LinkAnnotation 而不是自己画下划线：点击会被 LocalUriHandler 接住
                                if (href.isNotBlank()) {
                                    withLink(
                                        LinkAnnotation.Url(
                                            url = href,
                                            styles = TextLinkStyles(
                                                style = SpanStyle(
                                                    color = linkColor,
                                                    textDecoration = TextDecoration.Underline,
                                                ),
                                            ),
                                        )
                                    ) { append(label) }
                                } else {
                                    withStyle(
                                        SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
                                    ) { append(label) }
                                }
                                i = closeParen + 1
                            } else { sb.append(text[i]); i++ }
                        } else { sb.append(text[i]); i++ }
                    }
                    text[i] == '*' || text[i] == '_' -> {
                        val end = text.indexOf(text[i], i + 1)
                        if (end > 0 && end > i + 1) {
                            flush()
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(text.substring(i + 1, end))
                            pop()
                            i = end + 1
                        } else { sb.append(text[i]); i++ }
                    }
                    else -> { sb.append(text[i]); i++ }
                }
            }
            flush()
        }
    }
}

/**
 * oshwhub 工程正文（markdown）+ 文章正文渲染。
 * 图片用 AsyncImage 加载，代码块等宽字体，标题按层级排版。
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    onImageClick: ((String) -> Unit)? = null,
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Heading -> {
                    Spacer(Modifier.height(if (block.level <= 2) 14.dp else 8.dp))
                    Text(
                        text = inline(block.text),
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

                is MdBlock.Paragraph -> {
                    Text(
                        text = inline(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                is MdBlock.ImageBlock -> {
                    val src = Url.image(block.url)
                    if (src != null) {
                        Spacer(Modifier.height(4.dp))
                        AsyncImage(
                            model = src,
                            contentDescription = block.alt,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
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

                is MdBlock.CodeBlock -> {
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

                is MdBlock.Quote -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(if (block.lines.size > 2) 60.dp else 34.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            block.lines.joinToString("\n"),
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                is MdBlock.Bullets -> {
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
                                    inline(item),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                MdBlock.Divider -> {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
