package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import io.github.Calvariaa.oshwhub.ui.resolveNavTarget
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * 调试用：确认 `getUuidByPath` / 用户名→uuid 这些解析真能跑通，
 * 以及站内链接最终落到哪个原生页面。
 */
class DebugLinkProbe {

    private fun api(dir: File): OshApi =
        OshApi(ApiClient(CookieStore(File(dir, "cookies.txt")), dir))

    @Test
    fun resolveLinks() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "oshwhub-coverage").apply { mkdirs() }
        val api = api(dir)

        val page = api.projects(page = 1, pageSize = 5)
        for (p in page.lists) {
            println("list project uuid=${p.uuid} name=${p.name} path=${p.path}")
        }

        for (p in page.lists.take(5)) {
            if (p.path.isBlank()) continue
            val byPath = api.projectUuidByPath(p.path)
            println("projectUuidByPath(${p.path}) = $byPath  (expect ${p.uuid})")
            println("  resolve = " + resolveNavTarget(api, "https://oshwhub.com/${p.path}"))
        }

        val article = api.articles(page = 1, pageSize = 3).lists.firstOrNull()
        println("article uuid=${article?.uuid} path=${article?.path}")
        article?.path?.let {
            println("articleUuidByPath($it) = ${api.articleUuidByPath(it)}")
        }

        val user = api.projects(page = 1, pageSize = 5).lists.firstNotNullOfOrNull { it.owner?.username }
        println("owner username=$user")
        if (user != null) {
            println("userIdByName($user) = " + api.userIdByName(user))
            println("  resolve = " + resolveNavTarget(api, "https://oshwhub.com/$user"))
        }

        println("collection resolve = " + resolveNavTarget(api, "https://oshwhub.com/collection/officeRecommend"))
        println("activity resolve = " + resolveNavTarget(api, "https://oshwhub.com/activities/xinghuo2026"))
    }
}
