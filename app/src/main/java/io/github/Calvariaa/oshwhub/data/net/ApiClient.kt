package io.github.Calvariaa.oshwhub.data.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cache
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message) {
    val isAuthError: Boolean get() = code == 401 || code == 403
}

private class PersistentCookieJar(private val store: CookieStore) : CookieJar {
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store.get(url)
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = store.put(url, cookies)
}

/**
 * 复刻 oshwhub 桌面端 web 的请求层：
 * - 统一 UA / Referer（后端对空 Referer 会拒绝部分接口）
 * - Cookie 持久化（登录态）
 * - `{success, code, result}` 信封解析
 */
class ApiClient(
    val cookieStore: CookieStore,
    cacheDir: File,
    enableLogging: Boolean = false,
) {

    companion object {
        const val WEB_BASE = "https://oshwhub.com"
        const val PASSPORT_BASE = "https://passport.jlc.com"
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val FORM_MEDIA = "application/x-www-form-urlencoded".toMediaType()
    }

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(PersistentCookieJar(cookieStore))
        .cache(Cache(File(cacheDir, "http"), 48L * 1024 * 1024))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (enableLogging) {
                addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                    level = okhttp3.logging.HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", UA)
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Referer", WEB_BASE + "/")
                .build()
            chain.proceed(req)
        }
        .build()

    // ---------------------------------------------------------------- raw --

    private suspend fun exec(request: Request): String = withContext(Dispatchers.IO) {
        okHttp.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code >= 500) throw ApiException(resp.code, "服务器错误 (${resp.code})")
            body
        }
    }

    fun urlOf(path: String, params: Map<String, Any?> = emptyMap(), base: String = WEB_BASE): HttpUrl {
        val absolute = path.startsWith("http")
        val normalized = if (absolute || path.startsWith("/")) path else "/$path"
        val builder = (if (absolute) path.toHttpUrl() else (base + normalized).toHttpUrl()).newBuilder()
        params.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is Iterable<*> -> builder.addQueryParameter(k, v.joinToString(",") { it.toString() })
                is Boolean -> builder.addQueryParameter(k, v.toString())
                else -> builder.addQueryParameter(k, v.toString())
            }
        }
        return builder.build()
    }

    suspend fun getText(path: String, params: Map<String, Any?> = emptyMap(), base: String = WEB_BASE, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder().url(urlOf(path, params, base)).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        return exec(builder.build())
    }

    suspend fun postJsonText(
        path: String,
        payload: JsonElement?,
        base: String = WEB_BASE,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val body: RequestBody = (payload?.toString() ?: "{}").toRequestBody(JSON_MEDIA)
        val builder = Request.Builder().url(urlOf(path, emptyMap(), base)).post(body)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return exec(builder.build())
    }

    suspend fun postFormText(
        path: String,
        form: Map<String, String>,
        base: String = WEB_BASE,
        headers: Map<String, String> = emptyMap(),
    ): String {
        val body = form.entries.joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
            .toRequestBody(FORM_MEDIA)
        val builder = Request.Builder().url(urlOf(path, emptyMap(), base)).post(body)
        headers.forEach { (k, v) -> builder.header(k, v) }
        return exec(builder.build())
    }

    suspend fun deleteText(path: String, base: String = WEB_BASE): String {
        return exec(Request.Builder().url(urlOf(path, emptyMap(), base)).delete().build())
    }

    /** 直接拿到 Response（需要读 header / 跟随重定向的业务） */
    suspend fun execRaw(request: Request): okhttp3.Response = withContext(Dispatchers.IO) {
        okHttp.newCall(request).execute()
    }

    // ----------------------------------------------------------- envelope --

    /** 解析信封并返回 result/data 负载；`success=false` 抛 ApiException。 */
    fun payloadOf(text: String): JsonElement {
        if (text.isBlank()) return JsonNull
        val element = runCatching { json.parseToJsonElement(text) }
            .getOrElse { throw ApiException(-1, "返回体不是 JSON") }
        if (element is JsonObject) {
            val success = element["success"]?.jsonPrimitive?.booleanOrNull
            val code = element["code"]?.jsonPrimitive?.intOrNull ?: 0
            val failed = success == false || code >= 400
            if (failed) {
                val msg = element["message"]?.jsonPrimitive?.contentOrNull
                    ?: element["msg"]?.jsonPrimitive?.contentOrNull
                    ?: defaultMessageFor(code)
                throw ApiException(code.coerceAtLeast(1), msg)
            }
            return element["result"]?.takeIf { it !is JsonNull }
                ?: element["data"]?.takeIf { it !is JsonNull }
                ?: JsonNull
        }
        return element
    }

    private fun defaultMessageFor(code: Int): String = when (code) {
        401 -> "请先登录"
        403 -> "没有权限"
        404 -> "内容不存在"
        422 -> "请求参数有误"
        else -> "请求失败（$code）"
    }

    // ------------------------------------------------------------- typed ----

    suspend inline fun <reified T> get(path: String, params: Map<String, Any?> = emptyMap()): T {
        val el = payloadOf(getText(path, params))
        return json.decodeFromJsonElement(el)
    }

    suspend inline fun <reified T> post(path: String, body: JsonElement = JsonObject(emptyMap())): T {
        val el = payloadOf(postJsonText(path, body))
        return json.decodeFromJsonElement(el)
    }

    suspend inline fun <reified T> delete(path: String): T {
        val el = payloadOf(deleteText(path))
        return json.decodeFromJsonElement(el)
    }

    suspend inline fun <reified T> getOrNull(path: String, params: Map<String, Any?> = emptyMap()): T? =
        runCatching { get<T>(path, params) }.getOrNull()

    fun obj(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

    fun put(builder: kotlinx.serialization.json.JsonObjectBuilder, key: String, value: Any?) {
        when (value) {
            null -> Unit
            is String -> builder.put(key, value)
            is Int -> builder.put(key, value)
            is Long -> builder.put(key, value)
            is Boolean -> builder.put(key, value)
            is JsonElement -> builder.put(key, value)
            else -> builder.put(key, value.toString())
        }
    }
}
