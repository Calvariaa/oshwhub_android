package io.github.Calvariaa.oshwhub.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.Calvariaa.oshwhub.OshApplication
import io.github.Calvariaa.oshwhub.util.DeepLink
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 应用内网页兜底页。
 *
 * 原生页面覆盖不到的目标（活动、帖子详情等）在 App 内打开，不再 `ACTION_VIEW`
 * 甩给系统浏览器。打开前把 OkHttp 的 Cookie 灌进 WebView，所以看到的是登录态。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebScreen(
    url: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val start = remember(url) { DeepLink.normalize(url) ?: url }
    var progress by remember { mutableIntStateOf(100) }
    var title by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf(start) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    // 把 OkHttp 侧的 cookie（含登录会话）搬进 WebView
    remember(start) {
        runCatching {
            val store = (context.applicationContext as OshApplication).container.cookieStore
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            val parsed = start.toHttpUrlOrNull() ?: return@runCatching
            store.get(parsed).forEach { c ->
                cm.setCookie(start, "${c.name}=${c.value}; path=${c.path}; domain=${c.domain}")
            }
            cm.flush()
        }
        true
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text(
                title.ifBlank { "网页" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { webView?.reload() }) { Icon(Icons.Filled.Refresh, "刷新") }
            IconButton(onClick = {
                val activity = context as? Activity ?: return@IconButton
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                }
            }) { Icon(Icons.Filled.OpenInNew, "浏览器打开") }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = true
                    settings.useWideViewPort = false
                    settings.setSupportZoom(false)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            progress = newProgress
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                            progress = 5
                            url?.let { currentUrl = it }
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            progress = 100
                            view.title?.takeIf { it.isNotBlank() }?.let { title = it }
                            url?.let { currentUrl = it }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val next = request.url.toString()
                            if (next.startsWith("http")) return false
                            // tel:/mailto:/支付宝等外部协议交给系统
                            runCatching { view.context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                            return true
                        }
                    }
                    loadUrl(start)
                    webView = this
                }
            },
            update = { webView = it },
        )
    }
}
