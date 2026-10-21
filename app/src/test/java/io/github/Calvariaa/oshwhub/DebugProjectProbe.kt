package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test
import java.io.File

/**
 * 调试用：确认工程详情的 attachments 字段到底有没有数据，
 * 以及附件下载 URL 的形态是否正确。
 */
private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0"

class DebugProjectProbe {

    private fun api(dir: File): OshApi =
        OshApi(ApiClient(CookieStore(File(dir, "cookies.txt")), dir))

    @Test
    fun dumpAttachments() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "oshwhub-coverage").apply { mkdirs() }
        val api = api(dir)
        val page = api.projects(page = 1, pageSize = 20)
        println("projects=${page.lists.size}")
        for (summary in page.lists) {
            val detail = runCatching { api.project(summary.uuid) }.getOrNull() ?: continue
            if (detail.attachments.isEmpty() && detail.attachments_3d.isEmpty()) continue
            println("=== ${detail.name} uuid=${detail.uuid}")
            println("attachments=${detail.attachments.size} models=${detail.attachments_3d.size}")
            detail.attachments.take(3).forEach { a ->
                println("  att ${a.name} size=${a.size} ext=${a.ext} src=${a.src.take(60)}")
                val url = "${ApiClient.WEB_BASE}/api/common/project/${detail.uuid}/attachments/${a.uuid}"
                val cookie = api.client.cookieStore.headerFor(url.toHttpUrl())
                val req = Request.Builder().url(url)
                    .header("User-Agent", UA)
                    .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
                    .build()
                runCatching {
                    api.client.okHttp.newCall(req).execute().use { resp ->
                        println("  GET $url -> ${resp.code} ${resp.header("Content-Type")} len=${resp.header("Content-Length")}")
                    }
                }.onFailure { println("  GET failed: ${it.message}") }
            }
            return@runBlocking
        }
        println("no project with attachments on page 1")
    }
}
