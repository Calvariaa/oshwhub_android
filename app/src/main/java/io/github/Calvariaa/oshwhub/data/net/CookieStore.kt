package io.github.Calvariaa.oshwhub.data.net

import okhttp3.Cookie
import okhttp3.HttpUrl
import java.io.File

/**
 * 极简 Cookie 仓库：支持会话型与持久型 cookie，落地为纯文本文件，
 * 保证 App 重启后仍是登录态。
 */
class CookieStore(private val file: File) {

    private val lock = Any()
    private val cookies = LinkedHashMap<String, Cookie>()

    init {
        load()
    }

    private fun key(c: Cookie) = "${c.domain}|${c.path}|${c.name}"

    fun get(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val expired = cookies.values.filter { it.expiresAt < now }
            if (expired.isNotEmpty()) {
                expired.forEach { cookies.remove(key(it)) }
                persistLocked()
            }
            return cookies.values.filter { it.matches(url) }
        }
    }

    fun put(url: HttpUrl, incoming: List<Cookie>) {
        if (incoming.isEmpty()) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            for (c in incoming) {
                if (c.expiresAt < now) {
                    cookies.remove(key(c))
                } else {
                    cookies[key(c)] = c
                }
            }
            persistLocked()
        }
    }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            runCatching { file.delete() }
        }
    }

    /** DownloadManager 等外部组件需要的 Cookie 头 */
    fun headerFor(url: HttpUrl): String = get(url).joinToString("; ") { "${it.name}=${it.value}" }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            file.forEachLine { line ->
                val parts = line.split('\t')
                if (parts.size >= 4) {
                    val domain = parts[0]
                    val path = parts[1]
                    val name = parts[2]
                    val value = parts[3]
                    val builder = Cookie.Builder()
                        .domain(domain.removePrefix("."))
                        .path(path)
                        .name(name)
                        .value(value)
                    cookies["$domain|$path|$name"] = builder.build()
                }
            }
        }
    }

    private fun persistLocked() {
        runCatching {
            file.parentFile?.mkdirs()
            val text = cookies.values.joinToString("\n") { c ->
                "${if (c.hostOnly) c.domain else "." + c.domain}\t${c.path}\t${c.name}\t${c.value}"
            }
            file.writeText(text)
        }
    }
}
