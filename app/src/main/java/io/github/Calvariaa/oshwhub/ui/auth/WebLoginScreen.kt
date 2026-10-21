package io.github.Calvariaa.oshwhub.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.Calvariaa.oshwhub.data.SessionManager
import io.github.Calvariaa.oshwhub.data.net.CookieStore
import io.github.Calvariaa.oshwhub.data.net.WebCookieBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val CAS_LOGIN_URL =
    "https://passport.jlc.com/login?appId=JLC_OSHWHUB&redirectUrl=https%3A%2F%2Foshwhub.com%2F"

/** 与 ApiClient 保持一致的桌面 UA，避免站点下发移动版页面 */
internal const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/155.0.0.0 Safari/537.36 Edg/155.0.0.0"

private val AC_CODE = Regex("""AC-[0-9a-fA-F]{32}""")

/**
 * 网页登录的中枢：
 * 1. 盯着 WebView 的 URL，一旦出现 CAS 票据 `AC-xxxxxxxx`，立刻拦下该导航，
 *    改用 OkHttp 自己 POST /api/login/login-by-code 换会话（这样会话 cookie 落在
 *    我们自己的 CookieStore 里，不受 WebView HttpOnly 限制）。
 * 2. 若票据没抓到、但 WebView 已登进站内，就退回“同步 cookie + 校验 /api/users/me”。
 */
private class WebLoginBridge(
    private val session: SessionManager,
    private val cookieStore: CookieStore,
    private val scope: CoroutineScope,
    private val onStatus: (String) -> Unit,
    private val onLoggedIn: (String) -> Unit,
) {
    @Volatile private var redeeming = false
    @Volatile private var verifying = false
    @Volatile private var finished = false

    /** @return true 表示已接管该导航（票据交给我们换取），WebView 不再跳转 */
    fun intercept(url: String?): Boolean {
        if (url.isNullOrBlank() || finished) return false
        val code = AC_CODE.find(url)?.value ?: return false
        redeem(code)
        return true
    }

    fun inspect(url: String?) {
        if (url.isNullOrBlank() || finished) return
        if (AC_CODE.containsMatchIn(url)) {
            intercept(url)
            return
        }
        if (!url.startsWith("https://oshwhub.com") && !url.startsWith("https://www.oshwhub.com")) return
        if (url.contains("/login") || url.contains("passport")) return
        verifyByCookie()
    }

    fun verifyByCookie() {
        if (finished || verifying || redeeming) return
        verifying = true
        scope.launch {
            val me = runCatching {
                WebCookieBridge.syncTo(cookieStore)
                WebCookieBridge.flush()
                session.refresh()
                session.user.value
            }.getOrNull()
            verifying = false
            if (me != null) {
                finish(me.nickname.ifBlank { me.username })
            } else {
                onStatus("尚未检测到登录态，请在页面里完成登录（可点右上角“我已登录”手动检测）")
            }
        }
    }

    private fun redeem(code: String) {
        if (finished || redeeming) return
        redeeming = true
        onStatus("已拿到登录票据 $code，正在换取应用内会话…")
        scope.launch {
            val result = runCatching {
                WebCookieBridge.syncTo(cookieStore)
                session.loginWithCasCode(code)
            }
            redeeming = false
            result.onSuccess { finish(it.nickname.ifBlank { it.username }) }
                .onFailure { e ->
                    onStatus("票据兑换失败：${e.message ?: "未知错误"}，改为校验站点 cookie…")
                    verifyByCookie()
                }
        }
    }

    private fun finish(name: String) {
        if (finished) return
        finished = true
        onStatus("登录成功：$name")
        WebCookieBridge.flush()
        onLoggedIn(name)
    }
}

/**
 * 网页登录页：遇到“检测到当前环境有风险，需要安全验证！”时用这个入口。
 *
 * 登录接口由火山 WAF 保护，纯 HTTP 请求拿不到风控 cookie，所以这里让真实 Chromium
 * （WebView）跑完整登录流程——包括手机验证码与滑块验证——再把凭据交回原生层。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLoginScreen(
    session: SessionManager,
    cookieStore: CookieStore,
    onBack: () -> Unit,
    onLoggedIn: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("在下方页面完成登录：账号密码、手机验证码都可以，安全验证也会在页面里出现") }
    var loading by remember { mutableStateOf(true) }

    val bridge = remember {
        WebLoginBridge(
            session = session,
            cookieStore = cookieStore,
            scope = scope,
            onStatus = { status = it },
            onLoggedIn = { name -> onLoggedIn(name) },
        )
    }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(false)
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                userAgentString = DESKTOP_UA
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    return bridge.intercept(request.url.toString())
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    bridge.intercept(url)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    loading = false
                    WebCookieBridge.flush()
                    bridge.inspect(url)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        loading = false
                        status = "页面加载失败：${error.description}；可点下方“重新加载”"
                    }
                }
            }
            loadUrl(CAS_LOGIN_URL)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webView.stopLoading()
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text(
                "网页登录",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { bridge.verifyByCookie() }) { Text("我已登录") }
            TextButton(onClick = { webView.reload() }) { Text("重新加载") }
        }
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                status,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}
