package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/**
 * 调试用：打印文章正文里第一个 <table> 的原始 HTML，用来对照 HtmlText 的表格渲染。
 * 手动运行：./gradlew :app:testDebugUnitTest --tests '*DebugArticleProbe*'
 */
class DebugArticleProbe {

    private fun api(): OshApi {
        val dir = File(System.getProperty("java.io.tmpdir"), "oshwhub-coverage").apply { mkdirs() }
        return OshApi(ApiClient(CookieStore(File(dir, "cookies.txt")), dir))
    }

    @Test
    fun dumpFirstArticleTable() = runBlocking {
        val api = api()
        val candidates = (1..5).flatMap { page ->
            runCatching { api.articles(page = page, pageSize = 20).lists }
                .onFailure { println("page $page failed: ${it::class.simpleName}: ${it.message}") }
                .getOrElse { emptyList() }
        }
        println("candidates=${candidates.size}")
        for (article in candidates) {
            val detail = runCatching { api.article(article.uuid) }.getOrNull() ?: continue
            val html = detail.content
            val start = html.indexOf("<table", ignoreCase = true)
            if (start < 0) continue
            val end = html.indexOf("</table>", ignoreCase = true)
            println("=== ${detail.title}")
            println(html.substring(start, if (end > start) end + 8 else start + 3000))
            return@runBlocking
        }
        println("no table article found")
    }
}
