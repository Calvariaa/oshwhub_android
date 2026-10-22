package io.github.Calvariaa.oshwhub.ui

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.util.DeepLink

/** 站内链接解析后的原生目标 */
sealed interface NavTarget {
    data class Project(val uuid: String) : NavTarget
    data class Article(val uuid: String) : NavTarget
    data class User(val uuid: String) : NavTarget
    data class Collection(val key: String) : NavTarget
    data class Activity(val uuid: String) : NavTarget
    data class Search(val keyword: String) : NavTarget
    data class Web(val url: String) : NavTarget
}

/**
 * 把站内链接换成能直接导航的目标。
 *
 * 顺序：正文里的 `owner/project` / `article_xxx` 先换 uuid；换不出来就落到
 * 内置 WebView —— 无论如何都不弹系统浏览器。
 */
suspend fun resolveNavTarget(api: OshApi, link: String?): NavTarget? {
    if (link.isNullOrBlank()) return null
    val normalized = DeepLink.normalize(link) ?: return null
    return when (val parsed = DeepLink.parse(normalized)) {
        is DeepLink.Target.Project -> when {
            parsed.path.length == 32 -> NavTarget.Project(parsed.path)
            else -> api.projectUuidByPath(parsed.path)?.let { NavTarget.Project(it) }
                ?: NavTarget.Web(normalized)
        }
        is DeepLink.Target.Article -> when {
            parsed.path.length == 32 -> NavTarget.Article(parsed.path)
            else -> api.articleUuidByPath(parsed.path)?.let { NavTarget.Article(it) }
                ?: NavTarget.Web(normalized)
        }
        is DeepLink.Target.User -> api.userIdByName(parsed.username)?.let { NavTarget.User(it) }
            ?: NavTarget.Web(normalized)
        is DeepLink.Target.Collection -> NavTarget.Collection(parsed.key)
        is DeepLink.Target.Activity -> api.activityUuidByPath(parsed.path)?.let { NavTarget.Activity(it) }
            ?: NavTarget.Web(normalized)
        is DeepLink.Target.Tag -> NavTarget.Search(parsed.tag)
        is DeepLink.Target.Search -> NavTarget.Search(parsed.keyword)
        is DeepLink.Target.Web -> NavTarget.Web(parsed.url)
        null -> null
    }
}
