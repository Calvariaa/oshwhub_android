package io.github.Calvariaa.oshwhub.data.net

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * WebView ↔ OkHttp 的 cookie 搬运。
 *
 * 站点的登录接口（passport.jlc.com）在纯 HTTP 客户端环境下会被风控拦下，返回
 * “检测到当前环境有风险，需要安全验证！”。真实浏览器能过是因为它执行了 JS 挑战
 * （HWWAFSESID 等 cookie）。因此网页登录页在 WebView 里跑，登录成功后把 WebView
 * 拿到的 cookie（尤其是 HWWAF* 风控 cookie）灌进 OkHttp 的 CookieStore，后续
 * 原生接口调用就带着同一套凭据。
 *
 * 注意：HttpOnly 的会话 cookie 拿不到，所以真正的会话靠 CAS 票据（AC-xxx）由
 * OkHttp 自己换取（见 AuthRepository.loginByCasTicket），这里只做风控 cookie 同步。
 */
object WebCookieBridge {

    private val BASES = listOf(
        "https://oshwhub.com/",
        "https://passport.jlc.com/",
        "https://www.jlc.com/",
        "https://jlc.com/",
    )

    /** 把 WebView 当前可见的 cookie 同步进 store，返回同步了哪些 `host:name` */
    fun syncTo(store: CookieStore): List<String> {
        val cm = runCatching { CookieManager.getInstance() }.getOrNull() ?: return emptyList()
        val synced = mutableListOf<String>()
        for (base in BASES) {
            val raw = runCatching { cm.getCookie(base) }.getOrNull().orEmpty()
            if (raw.isBlank()) continue
            val url = runCatching { base.toHttpUrl() }.getOrNull() ?: continue
            val cookies = raw.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) return@mapNotNull null
                val name = part.substring(0, i).trim()
                val value = part.substring(i + 1).trim()
                if (name.isEmpty() || value.isEmpty()) null else runCatching {
                    Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }.getOrNull()
            }
            if (cookies.isNotEmpty()) {
                store.put(url, cookies)
                cookies.forEach { synced += "${url.host}:${it.name}" }
            }
        }
        return synced
    }

    fun flush() {
        runCatching { CookieManager.getInstance().flush() }
    }
}
