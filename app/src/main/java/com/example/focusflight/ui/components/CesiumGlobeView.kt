package com.example.focusflight.ui.components

import android.annotation.SuppressLint
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.example.focusflight.bridge.CesiumBridge
import com.example.focusflight.ui.viewmodel.FlightViewModel

private const val TAG = "CesiumGlobeView"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CesiumGlobeView(
    viewModel: FlightViewModel, 
    modifier: Modifier = Modifier,
    onWebViewReady: (WebView) -> Unit = {}
) {
    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .setDomain("appassets.androidplatform.net")
                    .setHttpAllowed(true)
                    .build()

                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        WebView.setWebContentsDebuggingEnabled(true)
                    }

                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    settings.allowContentAccess = true
                    settings.allowFileAccess = true
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = false

                    // Add Javascript Bridge
                    addJavascriptInterface(CesiumBridge(viewModel), "AndroidBridge")

                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                            val level = when (msg.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                else -> "INFO"
                            }
                            Log.d(TAG, "[$level] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                            return true
                        }
                    }

                    webViewClient = object : WebViewClientCompat() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            Log.d(TAG, "Page loaded: $url")
                        }
                    }

                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(0xFF000000.toInt())

                    loadUrl("https://appassets.androidplatform.net/assets/index.html")
                    
                    onWebViewReady(this)
                }
            }
        )
    }
}
