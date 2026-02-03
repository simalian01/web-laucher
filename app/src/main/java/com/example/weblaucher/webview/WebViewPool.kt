package com.example.weblaucher.webview

import android.content.Context
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.weblaucher.data.LauncherSettings
import com.example.weblaucher.data.WindowRepository
import com.example.weblaucher.model.WindowConfig
import com.example.weblaucher.model.WindowState
import java.util.concurrent.ConcurrentHashMap

class WebViewPool(
    private val context: Context,
    private val windowRepository: WindowRepository,
    private var settings: LauncherSettings,
    private val fileChooserHandler: (ValueCallback<Array<Uri>>, Boolean, Array<String>) -> Unit,
    private val externalOpener: (String) -> Unit,
    private val onRendererGone: (String) -> Unit
) {
    private val pool = ConcurrentHashMap<String, WebView>()

    fun updateSettings(newSettings: LauncherSettings) {
        settings = newSettings
        pool.values.forEach { view ->
            CookieManager.getInstance().setAcceptThirdPartyCookies(view, settings.thirdPartyCookies)
            view.settings.userAgentString = settings.userAgent.takeIf { it.isNotBlank() }
                ?: WebSettings.getDefaultUserAgent(context)
        }
    }

    fun getOrCreate(window: WindowConfig): WebView {
        return pool[window.id] ?: createWebView(window)
    }

    fun attachTo(window: WindowConfig, container: ViewGroup) {
        val webView = getOrCreate(window)
        if (webView.parent != null) {
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
        container.addView(webView)
        webView.onResume()
        webView.resumeTimers()
        windowRepository.updateWindowState(
            WindowState(
                windowId = window.id,
                lastUrl = webView.url ?: window.url,
                lastScrollY = webView.scrollY,
                lastActiveTime = System.currentTimeMillis()
            )
        )
        enforceMaxAlive()
    }

    fun detach(windowId: String) {
        val webView = pool[windowId] ?: return
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.onPause()
        webView.pauseTimers()
        windowRepository.updateWindowState(
            WindowState(
                windowId = windowId,
                lastUrl = webView.url.orEmpty(),
                lastScrollY = webView.scrollY,
                lastActiveTime = System.currentTimeMillis()
            )
        )
    }

    fun refresh(windowId: String) {
        pool[windowId]?.reload()
    }

    fun refreshAll() {
        pool.values.forEach { it.reload() }
    }

    fun recreate(window: WindowConfig) {
        val existing = pool.remove(window.id)
        existing?.let { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        createWebView(window)
    }

    fun destroyAll() {
        pool.values.forEach { webView ->
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.clearHistory()
            webView.removeAllViews()
            webView.destroy()
        }
        pool.clear()
    }

    fun canGoBack(windowId: String): Boolean = pool[windowId]?.canGoBack() == true

    fun goBack(windowId: String) {
        pool[windowId]?.goBack()
    }

    fun enforceMaxAlive() {
        val maxAlive = settings.maxAlive
        if (pool.size <= maxAlive) return
        val candidates = pool.values.mapNotNull { webView ->
            val state = windowRepository.getWindowState(webView.tag as? String ?: return@mapNotNull null)
            state?.let { webView to it }
        }.sortedBy { it.second.lastActiveTime }
        val toFreeze = candidates.take(pool.size - maxAlive)
        toFreeze.forEach { (webView, state) ->
            detach(state.windowId)
        }
    }

    private fun createWebView(window: WindowConfig): WebView {
        val webView = WebView(context)
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webView.tag = window.id
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.databaseEnabled = true
        webSettings.userAgentString = settings.userAgent.takeIf { it.isNotBlank() }
            ?: WebSettings.getDefaultUserAgent(context)
        webView.isVerticalScrollBarEnabled = true
        webView.isHorizontalScrollBarEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, settings.thirdPartyCookies)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                val allowMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                val acceptTypes = fileChooserParams.acceptTypes
                fileChooserHandler(filePathCallback, allowMultiple, acceptTypes)
                return true
            }
        }

        val windowState = windowRepository.getWindowState(window.id)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val currentUrl = view.url.orEmpty()
                if (shouldOpenExternally(currentUrl, url)) {
                    externalOpener(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                val scrollY = windowState?.lastScrollY ?: 0
                if (scrollY > 0) {
                    view.post { view.scrollTo(0, scrollY) }
                }
            }

            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                onRendererGone(window.id)
                return true
            }
        }

        if (windowState?.lastUrl?.isNotBlank() == true) {
            webView.loadUrl(windowState.lastUrl)
        } else {
            webView.loadUrl(window.url)
        }
        pool[window.id] = webView
        return webView
    }

    private fun shouldOpenExternally(currentUrl: String, targetUrl: String): Boolean {
        val whitelist = settings.whitelistDomains
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val currentHost = Uri.parse(currentUrl).host.orEmpty()
        val targetHost = Uri.parse(targetUrl).host.orEmpty()
        if (whitelist.isEmpty()) {
            return currentHost.isNotBlank() && targetHost.isNotBlank() && currentHost != targetHost
        }
        return targetHost.isNotBlank() && whitelist.none { targetHost.endsWith(it) }
    }
}
