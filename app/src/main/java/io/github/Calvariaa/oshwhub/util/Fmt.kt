package io.github.Calvariaa.oshwhub.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Fmt {

    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val localOut = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val dateOut = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    /** 把后端的 ISO / 'yyyy-MM-dd HH:mm:ss' 统一成可读时间 */
    fun time(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw.removeSuffix("Z")
        val candidates = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd")
        for (pattern in candidates) {
            val parser = SimpleDateFormat(pattern, Locale.CHINA)
            if (pattern.contains("'T'")) parser.timeZone = TimeZone.getTimeZone("UTC")
            val date: Date = runCatching { parser.parse(cleaned) }.getOrNull() ?: continue
            return localOut.format(date)
        }
        return raw
    }

    fun date(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw.removeSuffix("Z")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = runCatching { parser.parse(cleaned) }.getOrNull() ?: return raw
        return dateOut.format(date)
    }

    /** 相对时间：刚刚 / 3分钟前 / 2小时前 / 5天前 */
    fun relative(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val cleaned = raw.removeSuffix("Z")
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = runCatching { parser.parse(cleaned) }.getOrNull() ?: return raw
        val diff = System.currentTimeMillis() - date.time
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        return when {
            diff < minute -> "刚刚"
            diff < hour -> "${diff / minute} 分钟前"
            diff < day -> "${diff / hour} 小时前"
            diff < 30 * day -> "${diff / day} 天前"
            else -> dateOut.format(date)
        }
    }

    fun count(n: Int): String = when {
        n >= 100_000 -> String.format(Locale.US, "%.1fw", n / 10_000.0)
        n >= 10_000 -> String.format(Locale.US, "%.1fw", n / 10_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
        else -> n.toString()
    }

    fun money(value: Double): String {
        return if (value <= 0) "" else "¥" + String.format(Locale.US, "%,.2f", value).trimEnd('0').trimEnd('.')
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

object Url {
    const val WEB = "https://oshwhub.com"

    /** 附件（zip/mp4/pdf…）不在 oshwhub.com 上，走图床/静态资源域名 */
    const val CDN = "https://image.lceda.cn"

    /**
     * 工程附件的真实下载地址。
     * `Attachment.src` 形如 `/oshwhub/project/attachments/<hash>.zip`，
     * 拼在 oshwhub.com 上是 404，拼在 image.lceda.cn 上才是文件本体。
     */
    fun file(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "https://")
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> CDN + trimmed
            else -> "$CDN/$trimmed"
        }
    }

    /** 后端大量使用 //cdn 或 /image/xxx 相对路径，这里统一成可加载的绝对地址 */
    fun image(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "https://")
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> WEB + trimmed
            else -> trimmed
        }
    }

    fun attachThumb(url: String, width: Int = 640): String {        val base = image(url) ?: return ""
        val sep = if (base.contains('?')) "&" else "?"
        return "$base${sep}x-oss-process=image/resize,w_$width,m_lfit/format,webp"
    }

    fun project(path: String?): String = if (path.isNullOrBlank()) WEB else "$WEB/$path"
}

object Html {
    /** markdown/富文本混合内容里剥掉标签，用于简介、通知正文 */
    fun toPlain(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\[([^]]*)]\\([^)]*\\)"), "$1")
            .trim()
    }

    /** 高亮结果里带 <em> 标签，保留标记但去掉其它标签 */
    fun keepHighlight(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.replace(Regex("</?(?!em\\b|/em\\b)[a-zA-Z][^>]*>"), "")
    }

    private val HTML_HINT = Regex(
        "</?(p|div|br|img|h[1-6]|ul|ol|li|blockquote|pre|span|strong|b|em|table|figure)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** 正文是后台富文本 HTML 还是编辑器 markdown */
    fun looksLikeHtml(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return HTML_HINT.containsMatchIn(raw)
    }
}
