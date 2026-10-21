package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.Test
import java.io.File

private const val UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0"

/**
 * 调试用：找出工程附件真正可下载的 URL 形态。
 * 已知 /api/common/project/{uuid}/attachments/{att} 返回 404。
 */
class DebugAttachmentProbe {

    private fun api(dir: File): OshApi =
        OshApi(ApiClient(CookieStore(File(dir, "cookies.txt")), dir))

    private fun probe(api: OshApi, url: String) {
        val cookie = api.client.cookieStore.headerFor(url.toHttpUrl())
        val req = Request.Builder().url(url)
            .header("User-Agent", UA)
            .header("Referer", "https://oshwhub.com/")
            .header("Range", "bytes=0-64")
            .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
            .build()
        runCatching {
            api.client.okHttp.newCall(req).execute().use { r ->
                println("  ${r.code} len=${r.header("Content-Length")} ct=${r.header("Content-Type")} <- $url")
            }
        }.onFailure { println("  ERR ${it.message} <- $url") }
    }

    @Test
    fun findAttachmentUrl() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "oshwhub-coverage").apply { mkdirs() }
        val api = api(dir)
        val summary = api.projects(page = 1, pageSize = 20).lists
            .firstOrNull { it.name.contains("EDA-Pager") } ?: api.projects(pageSize = 20).lists.first()
        val detail = api.project(summary.uuid)
        val att = detail.attachments.firstOrNull() ?: run { println("no attachment"); return@runBlocking }
        println("project=${detail.uuid} att=${att.uuid} src=${att.src}")

        val bases = listOf(
            "https://oshwhub.com",
            "https://static.oshwhub.com",
            "https://image.lceda.cn",
            "https://oshwhub-pro.oss-cn-shenzhen.aliyuncs.com",
        )
        val paths = buildList {
            add(att.src)
            add("/api/common/projects/${detail.uuid}/attachments/${att.uuid}")
            add("/api/common/project/${detail.uuid}/attachments/${att.uuid}")
            add("/api/common/project/${detail.uuid}/attachment/${att.uuid}")
            add("/api/project/${detail.uuid}/attachments/${att.uuid}")
            add("/api/projects/${detail.uuid}/attachments/${att.uuid}")
        }
        runCatching {
            val jsonUrl = "https://oshwhub.com/api/project/${detail.uuid}/attachments/${att.uuid}"
            val req = Request.Builder().url(jsonUrl).header("User-Agent", UA)
                .header("Referer", "https://oshwhub.com/").build()
            api.client.okHttp.newCall(req).execute().use { r ->
                println("JSON ${r.code}: " + r.body?.string()?.take(600))
            }
        }.onFailure { println("json probe failed: ${it.message}") }

        for (base in bases) {
            for (path in paths) {
                if (path.isBlank()) continue
                probe(api, base + path)
            }
        }
    }
}
