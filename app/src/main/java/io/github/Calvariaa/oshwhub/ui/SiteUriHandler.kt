package io.github.Calvariaa.oshwhub.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.UriHandler
import androidx.navigation.NavHostController
import io.github.Calvariaa.oshwhub.data.OshApi
import kotlinx.coroutines.launch

/**
 * 全局链接拦截。
 *
 * Compose 的 `AnnotatedString.fromHtml` / `withLink` 默认把点击交给系统浏览器，
 * 所以正文里点一个 `https://oshwhub.com/xxx` 会跳出 App。这里接管
 * `LocalUriHandler`：站内地址解析成原生路由，站外域名的 http 链接落内置 WebView，
 * 只有 tel:/mailto: 这类协议才交给系统。
 */
@Composable
fun rememberSiteUriHandler(
    navController: NavHostController,
    api: OshApi,
): UriHandler {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(navController, api) {
        SiteUriHandler(
            api = api,
            scope = scope,
            onTarget = { navigateTarget(navController, it) },
            onExternal = { url ->
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)),
                    )
                }
            },
        )
    }
}

private class SiteUriHandler(
    private val api: OshApi,
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val onTarget: (NavTarget) -> Unit,
    private val onExternal: (String) -> Unit,
) : UriHandler {

    override fun openUri(uri: String) {
        if (!uri.startsWith("http", ignoreCase = true) && !uri.startsWith("/")) {
            onExternal(uri)
            return
        }
        scope.launch {
            val target = resolveNavTarget(api, uri)
            if (target != null) onTarget(target) else onExternal(uri)
        }
    }
}
