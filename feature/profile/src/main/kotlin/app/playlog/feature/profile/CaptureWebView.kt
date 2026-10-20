package app.playlog.feature.profile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Opens [url] and reports the first navigation whose URL starts with [capturePrefix] (an OAuth
 * redirect to a custom scheme such as `npf…://auth` or `com.scee.psxandroid.scecompcall://`).
 * Cookies are wiped on exit so tokens do not linger in the WebView profile.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptureWebView(url: String, capturePrefix: String, onCaptured: (String) -> Unit, modifier: Modifier = Modifier) {
    val callback by rememberUpdatedState(onCaptured)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    private var captured = false

                    private fun intercept(target: String): Boolean {
                        if (!target.startsWith(capturePrefix)) return false
                        if (!captured) {
                            captured = true
                            callback(target)
                        }
                        return true
                    }

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        intercept(request.url.toString())

                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        if (intercept(url)) view.stopLoading() else super.onPageStarted(view, url, favicon)
                    }
                }
                loadUrl(url)
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
}
