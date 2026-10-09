package app.sealed.web

import android.webkit.CookieManager
import android.webkit.ServiceWorkerClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.sealed.model.BrowserSettings
import app.sealed.model.PixelPersona

object ProfileIsolation {
    private val sensorScripts = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())

    fun supported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    fun bind(webView: WebView, profileName: String, settings: BrowserSettings, siteHost: String): Profile {
        check(supported())
        val profile = ProfileStore.getInstance().getOrCreateProfile(profileName)
        WebViewCompat.setProfile(webView, profile.name)
        apply(webView.settings, settings)
        profile.cookieManager.setAcceptCookie(settings.acceptCookies)
        profile.cookieManager.setAcceptThirdPartyCookies(webView, !settings.blockThirdPartyCookies)
        profile.serviceWorkerController.setServiceWorkerClient(object : ServiceWorkerClient() {
            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                return blockedResponse(request.url.toString(), settings, siteHost, navigation = false)
            }
        })
        installReferrer(webView, settings)
        installSensor(webView, settings)
        return profile
    }

    fun installSensor(webView: WebView, settings: BrowserSettings) {
        if (!settings.usesSensorSpoof()) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val script = SensorGate.script(settings)
        if (!sensorScripts.add(webView)) {
            webView.evaluateJavascript(script, null)
            return
        }
        WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
    }

    fun installLogin(webView: WebView, host: String, username: String, password: String) {
        if (username.isEmpty() && password.isEmpty()) return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            LoginFill.script(username, password),
            setOf("https://$host", "https://*.$host"),
        )
    }

    fun seedCookies(profile: Profile, origin: String, cookies: List<String>, onDone: () -> Unit) {
        if (cookies.isEmpty()) {
            onDone()
            return
        }
        val manager = profile.cookieManager
        val left = java.util.concurrent.atomic.AtomicInteger(cookies.size)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        fun finish() {
            if (finished.compareAndSet(false, true)) {
                manager.flush()
                onDone()
            }
        }
        cookies.forEach { cookie ->
            manager.setCookie(origin, cookie) {
                if (left.decrementAndGet() == 0) finish()
            }
        }
    }

    fun blockedResponse(
        url: String,
        settings: BrowserSettings,
        siteHost: String,
        navigation: Boolean,
    ): WebResourceResponse? {
        val reason = RequestGate.blockReason(
            url,
            siteHost,
            lockToHost = navigation && settings.lockToHost,
            blockTrackers = settings.blockTrackers,
        )
        if (reason == null) return null
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            403,
            "Blocked",
            emptyMap(),
            ByteArray(0).inputStream(),
        )
    }

    fun delete(profileName: String) {
        if (!supported()) return
        val store = ProfileStore.getInstance()
        if (store.getProfile(profileName) != null) {
            store.deleteProfile(profileName)
        }
    }

    fun wipeSession(webView: WebView) {
        webView.clearCache(true)
        webView.clearHistory()
        webView.clearFormData()
        if (supported()) {
            val profile = WebViewCompat.getProfile(webView)
            profile.cookieManager.removeAllCookies(null)
            profile.cookieManager.flush()
            profile.webStorage.deleteAllData()
        } else {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
    }

    fun navigationHeaders(settings: BrowserSettings): Map<String, String> {
        val headers = linkedMapOf(
            "Accept-Language" to acceptLanguage(settings.language),
        )
        if (settings.doNotTrack) headers["DNT"] = "1"
        if (settings.globalPrivacyControl) headers["Sec-GPC"] = "1"
        return headers
    }

    private fun apply(settings: WebSettings, browser: BrowserSettings) {
        settings.javaScriptEnabled = browser.javascript
        settings.domStorageEnabled = browser.domStorage
        settings.loadsImagesAutomatically = browser.images
        settings.blockNetworkImage = !browser.images
        settings.userAgentString = browser.resolvedUserAgent()
        settings.textZoom = browser.textZoomPercent
        settings.setSupportZoom(browser.zoomEnabled)
        settings.builtInZoomControls = browser.zoomEnabled
        settings.displayZoomControls = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = browser.popups
        settings.setSupportMultipleWindows(browser.popups)
        settings.offscreenPreRaster = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = !browser.autoplay
        settings.setGeolocationEnabled(
            browser.locationMode == SensorGate.ASK || browser.locationMode == SensorGate.ALLOW,
        )
        @Suppress("DEPRECATION")
        settings.saveFormData = false
        settings.safeBrowsingEnabled = browser.safeBrowsing
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, browser.colorScheme != "light")
        }
        if (browser.pixelPersona && WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.setUserAgentMetadata(settings, metadata())
        }
    }

    private fun metadata(): UserAgentMetadata {
        fun brand(name: String, major: String, full: String) = UserAgentMetadata.BrandVersion.Builder()
            .setBrand(name)
            .setMajorVersion(major)
            .setFullVersion(full)
            .build()
        return UserAgentMetadata.Builder()
            .setBrandVersionList(
                listOf(
                    brand("Chromium", PixelPersona.CHROME_MAJOR, PixelPersona.CHROME_FULL),
                    brand("Google Chrome", PixelPersona.CHROME_MAJOR, PixelPersona.CHROME_FULL),
                    brand("Not?A_Brand", "24", "24.0.0.0"),
                ),
            )
            .setFullVersion(PixelPersona.CHROME_FULL)
            .setPlatform("Android")
            .setPlatformVersion(PixelPersona.PLATFORM_VERSION)
            .setArchitecture("")
            .setModel(PixelPersona.MODEL)
            .setMobile(true)
            .setBitness(64)
            .setWow64(false)
            .build()
    }

    private fun installReferrer(webView: WebView, settings: BrowserSettings) {
        val token = RequestGate.referrerToken(RequestGate.referrerMode(settings.referrerPolicy)) ?: return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        val script = """
            (function(){
              try {
                var root = document.head || document.documentElement;
                if (!root) return;
                var meta = document.createElement("meta");
                meta.setAttribute("name", "referrer");
                meta.setAttribute("content", "$token");
                root.insertBefore(meta, root.firstChild);
              } catch (e) {}
            })();
        """.trimIndent()
        WebViewCompat.addDocumentStartJavaScript(webView, script, setOf("*"))
    }

    private fun acceptLanguage(language: String): String {
        val primary = language.ifBlank { "en-US" }
        val bare = primary.substringBefore('-')
        return if (bare == primary) primary else "$primary,$bare;q=0.9"
    }
}
