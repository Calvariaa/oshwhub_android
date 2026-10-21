package io.github.Calvariaa.oshwhub.data

import io.github.Calvariaa.oshwhub.data.crypto.Sm2
import io.github.Calvariaa.oshwhub.data.model.UserInfo
import io.github.Calvariaa.oshwhub.data.net.ApiClient
import io.github.Calvariaa.oshwhub.data.net.ApiException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 登录 = 立创统一登录中心(passport.jlc.com) 的 CAS 流程：
 *  1. GET 登录页，拿 SM2 公钥（页面内联 sm2PublicKey）
 *  2. POST /api/cas/login/get-init-session（初始化 CAS 会话）
 *  3. POST /api/cas/login/with-password（username/password 均 SM2 加密）
 *  4. 从回跳地址中取 code=AC-xxxx
 *  5. POST https://oshwhub.com/api/login/login-by-code (form code=...)
 *  6. GET /api/users 验证登录态
 */
class AuthRepository(private val client: ApiClient, private val api: OshApi) {

    private val sm2KeyRegex = Regex("""sm2PublicKey\s*:\s*"([0-9a-fA-F]{126,130})"""")
    private val acCodeRegex = Regex("""AC-[0-9a-fA-F]{32}""")

    @Volatile
    private var cachedSm2Key: String? = null

    suspend fun fetchSm2Key(force: Boolean = false): String {
        if (!force) cachedSm2Key?.let { return it }
        val html = client.getText(
            "/login",
            mapOf("appId" to APP_ID, "redirectUrl" to "$WEB_BASE/"),
            base = ApiClient.PASSPORT_BASE,
        )
        val key = sm2KeyRegex.find(html)?.groupValues?.get(1)
            ?: throw ApiException(-2, "无法获取登录公钥（登录页结构可能已变）")
        cachedSm2Key = key
        return key
    }

    /** 仅密码登录（web 端账号密码 tab） */
    suspend fun loginByPassword(account: String, password: String, autoLogin: Boolean = true): UserInfo {
        val key = fetchSm2Key()
        // 1) 初始化 CAS 会话（失败不阻塞，某些环境可跳过）
        runCatching {
            client.postJsonText(
                "/api/cas/login/get-init-session",
                buildJsonObject {
                    put("appId", JsonPrimitive(APP_ID))
                    put("clientType", JsonPrimitive("PC-WEB"))
                },
                base = ApiClient.PASSPORT_BASE,
            )
        }

        val body = buildJsonObject {
            put("username", JsonPrimitive(Sm2.encrypt(account, key)))
            put("password", JsonPrimitive(Sm2.encrypt(password, key)))
            put("isAutoLogin", JsonPrimitive(autoLogin))
        }
        val raw = client.postJsonText("/api/cas/login/with-password", body, base = ApiClient.PASSPORT_BASE)
        val code = extractAcCode(raw)
        if (code != null) {
            exchangeCode(code)
        } else {
            // 有些环境下 CAS 直接下发会话 cookie，只需验证
            val me = runCatching { api.me() }.getOrNull()
            if (me == null) {
                throw ApiException(-3, parseMessage(raw) ?: "登录失败，请检查账号或密码")
            }
            return me
        }
        return api.me()
    }

    /** 手机验证码登录：发送验证码 */
    suspend fun sendSmsCode(phone: String): String {
        val key = fetchSm2Key()
        runCatching {
            client.postJsonText(
                "/api/cas/login/get-init-session",
                buildJsonObject {
                    put("appId", JsonPrimitive(APP_ID))
                    put("clientType", JsonPrimitive("PC-WEB"))
                },
                base = ApiClient.PASSPORT_BASE,
            )
        }
        val raw = client.postJsonText(
            "/api/cas/login/with-sms/send-code",
            buildJsonObject {
                put("phoneNumber", JsonPrimitive(Sm2.encrypt(phone, key)))
                put("appId", JsonPrimitive(APP_ID))
            },
            base = ApiClient.PASSPORT_BASE,
        )
        val el = client.json.parseToJsonElement(raw)
        val success = (el as? JsonObject)?.get("success")?.jsonPrimitive?.content?.toBoolean() ?: false
        if (!success) throw ApiException(-4, parseMessage(raw) ?: "验证码发送失败")
        return "验证码已发送"
    }

    /** 手机验证码登录：校验并登录 */
    suspend fun loginBySmsCode(phone: String, smsCode: String): UserInfo {
        val key = fetchSm2Key()
        val raw = client.postJsonText(
            "/api/cas/login/with-sms/check-code",
            buildJsonObject {
                put("phoneNumber", JsonPrimitive(Sm2.encrypt(phone, key)))
                put("validateCode", JsonPrimitive(Sm2.encrypt(smsCode, key)))
                put("isAutoLogin", JsonPrimitive(true))
            },
            base = ApiClient.PASSPORT_BASE,
        )
        val code = extractAcCode(raw)
        if (code != null) {
            exchangeCode(code)
            return api.me()
        }
        val msg = parseMessage(raw)
        if (msg != null) throw ApiException(-5, msg)
        return api.me()
    }

    /** 用 CAS 票据换取 oshwhub 会话 */
    private suspend fun exchangeCode(code: String) {
        client.postFormText("/api/login/login-by-code", mapOf("code" to code))
    }

    /**
     * 网页登录（WebView）拿到 CAS 票据 `AC-xxx` 后，由本机 OkHttp 换取会话。
     *
     * 这样会话 cookie 直接落进我们自己的 CookieStore，绕开 WebView HttpOnly 读不到
     * 会话 cookie 的限制；同时也是“检测到当前环境有风险，需要安全验证！”的正解：
     * 风控在 WebView 里由真实 Chromium 过掉，票据本身与浏览器风控无关。
     */
    suspend fun loginByCasTicket(code: String): UserInfo {
        exchangeCode(code)
        return api.me()
    }

    suspend fun logout() {
        api.logout()
        cachedSm2Key = null
    }

    suspend fun currentUser(): UserInfo? = runCatching { api.me() }.getOrNull()

    private fun extractAcCode(raw: String): String? {
        acCodeRegex.find(raw)?.value?.let { return it }
        // 有时藏在 URL 里且被 unicode 转义
        val unescaped = raw.replace("\\u0026", "&").replace("\\/", "/")
        acCodeRegex.find(unescaped)?.value?.let { return it }
        val codeParam = Regex("""[?&]code=(AC-[0-9a-fA-F]{32})""").find(unescaped)
        return codeParam?.groupValues?.get(1)
    }

    private fun parseMessage(raw: String): String? {
        val el: JsonElement = runCatching { client.json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val obj = el as? JsonObject ?: return null
        return (obj["message"] ?: obj["msg"])?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val APP_ID = "JLC_OSHWHUB"
        const val WEB_BASE = ApiClient.WEB_BASE
    }
}
