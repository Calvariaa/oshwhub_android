package io.github.Calvariaa.oshwhub.util

/**
 * 站内链接 → 原生路由的映射。
 *
 * oshwhub 的正文、通知、私信里全是 `https://oshwhub.com/...` 或 `/<owner>/<path>`
 * 这样的相对地址。点开时不该甩给系统浏览器：能识别的走原生页面，识别不了的
 * 落到内置 WebView，只有站外域名才交给浏览器。
 */
object DeepLink {

    /** 站内地址拆出来的目标（还没换成 uuid 的那一步，交给调用方解析） */
    sealed interface Target {
        data class Project(val path: String) : Target
        data class Article(val path: String) : Target
        data class Collection(val key: String) : Target
        data class User(val username: String) : Target
        data class Activity(val path: String) : Target
        data class Tag(val tag: String) : Target
        data class Search(val keyword: String) : Target
        data class Web(val url: String) : Target
    }

    private val HOSTS = setOf("oshwhub.com", "www.oshwhub.com", "m.oshwhub.com")

    /** 站内地址的路径片段（不含 scheme/host/query/fragment），站外返回 null */
    fun segments(raw: String?): List<String>? {
        val url = normalize(raw) ?: return null
        val afterScheme = url.substringAfter("://", url)
        val hostAndPath = afterScheme.substringBefore('#').substringBefore('?')
        val host = hostAndPath.substringBefore('/')
        if (host.isNotBlank() && host.contains('.') && host !in HOSTS) return null
        val path = hostAndPath.substringAfter('/', "")
        return path.split('/').filter { it.isNotBlank() }
    }

    fun isSiteUrl(raw: String?): Boolean = segments(raw) != null

    /** 补全成绝对地址：正文里大量出现 `//host/x`、`/x`、`host/x` 这类写法 */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") -> trimmed.replaceFirst("http://", "https://")
            trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("/") -> Url.WEB + trimmed
            trimmed.contains('.') && trimmed.substringBefore('/').contains('.') -> "https://$trimmed"
            else -> Url.WEB + "/" + trimmed
        }
    }

    private val FIRST_SEGMENT_KEYWORDS = setOf(
        "article", "articles", "post", "posts", "collection", "collections",
        "activities", "activity", "tag", "tags", "search", "explore", "project",
        "projects", "user", "users", "login", "message", "messages",
    )

    /** 纯解析，不做网络请求；uuid 那一层由调用方补 */
    fun parse(raw: String?): Target? {
        val parts = segments(raw) ?: return null
        if (parts.isEmpty()) return Target.Web(Url.WEB)
        val head = parts[0]
        return when {
            head == "article" && parts.size >= 2 -> Target.Article(parts[1])
            head == "post" && parts.size >= 2 -> Target.Web(normalize(raw)!!)
            head == "collection" && parts.size >= 2 -> Target.Collection(parts[1])
            head == "activities" -> if (parts.size >= 2) Target.Activity(parts[1]) else Target.Web(Url.WEB + "/activities")
            head == "tag" && parts.size >= 2 -> Target.Tag(parts[1])
            head == "explore" -> Target.Tag(parts.getOrElse(1) { "" })
            head == "search" -> Target.Search("")
            head == "project" && parts.size >= 2 -> Target.Project(parts[1])
            parts.size >= 2 && head !in FIRST_SEGMENT_KEYWORDS -> Target.Project("${parts[0]}/${parts[1]}")
            parts.size == 1 && head !in FIRST_SEGMENT_KEYWORDS -> Target.User(head)
            else -> Target.Web(normalize(raw)!!)
        }
    }

    /**
     * 从一段（可能是 HTML / markdown 的）文本里挑出第一个站内链接。
     * 通知正文就是这么拿到跳转目标的 —— 接口本身只给 content，不给 target_uuid。
     */
    fun firstLink(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val href = Regex("""<a\b[^>]*\bhref\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            .find(raw)?.groupValues?.get(1)
        if (!href.isNullOrBlank() && href != "#") return href.trim()
        val md = Regex("""]\((https?://[^)\s]+|/[^)\s]+)\)""").find(raw)?.groupValues?.get(1)
        if (!md.isNullOrBlank()) return md.trim()
        val plain = Regex("""https?://[^\s"'<>)]+""").find(raw)?.value
        return plain?.trim()
    }
}
