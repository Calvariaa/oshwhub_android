package io.github.Calvariaa.oshwhub

import io.github.Calvariaa.oshwhub.data.OshApi
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

/** 调试用：打印合集详情里计数字段的真实 key。 */
class DebugCollectionProbe {

    @Test
    fun dumpCollectionKeys() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "oshwhub-coverage").apply { mkdirs() }
        val api = OshApi(ApiClient(CookieStore(File(dir, "cookies.txt")), dir))
        val list = api.collections(page = 1, pageSize = 3)
        println("collections=${list.lists.size}")
        val first = list.lists.firstOrNull() ?: return@runBlocking
        val raw: JsonObject? = api.client.getOrNull("/api/collections/${first.uuid}")
        if (raw == null) {
            println("detail null")
            return@runBlocking
        }
        runCatching {
            val proj = raw["projects"]
            println("projects raw: " + proj.toString().take(400))
        }
        val listRaw: JsonObject? = api.client.getOrNull("/api/collections?page=1&pageSize=1")
        runCatching {
            val lists = listRaw?.get("lists")
            println("list lists[0]: " + (lists as? kotlinx.serialization.json.JsonArray)?.firstOrNull().toString().take(800))
        }
        raw.forEach { (k, v) ->
            val text = runCatching { v.jsonPrimitive.content }.getOrElse { "<obj>" }
            println("  $k = ${text.take(60)}")
        }
    }
}
