package app.sealed

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.print.PrintManager
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.sealed.data.BlockLog
import app.sealed.data.SecretStore
import app.sealed.data.WebAppRepository
import app.sealed.web.SensorGate
import app.sealed.model.BrowserSettings
import app.sealed.model.InstalledWebApp
import app.sealed.ui.theme.SealedTheme
import app.sealed.web.CookieLines
import app.sealed.web.ProfileIsolation
import app.sealed.web.RequestGate
import java.util.concurrent.atomic.AtomicBoolean

class WebAppActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val id = intent.getStringExtra(EXTRA_ID)
        val app = id?.let { WebAppRepository(this).get(it) }
        if (app == null) {
            finish()
            return
        }
        if (!ProfileIsolation.supported()) {
            Toast.makeText(this, "WebView trop ancien : profils isolés indisponibles.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContent {
            SealedTheme {
                IsolatedWebApp(app)
            }
        }
    }

    companion object {
        const val EXTRA_ID = "app.sealed.EXTRA_ID"
        const val DESKTOP_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
    }
}

private class SensorPrompt(
    val origin: String,
    val names: String,
    val allowSpoof: Boolean,
    val choose: (String) -> Unit,
    val dismiss: () -> Unit,
)

private class JsAsk(
    val message: String,
    val prompt: Boolean,
    val defaultValue: String,
    val resume: (Boolean, String) -> Unit,
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IsolatedWebApp(app: InstalledWebApp) {
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    var progress by mutableIntStateOf(0)
    var blocked by mutableStateOf<String?>(null)
    var pageError by mutableStateOf<String?>(null)
    var webViewRef by mutableStateOf<WebView?>(null)
    var loading by mutableStateOf(false)
    var menu by mutableStateOf(false)
    var desktop by mutableStateOf(false)
    var findQuery by mutableStateOf<String?>(null)
    var jsAsk by mutableStateOf<JsAsk?>(null)
    var customView by mutableStateOf<View?>(null)
    var customCallback by mutableStateOf<WebChromeClient.CustomViewCallback?>(null)
    var fileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingGrant by remember { mutableStateOf<Array<String>?>(null) }
    var pendingGeo by remember { mutableStateOf<Pair<String?, GeolocationPermissions.Callback>?>(null) }
    var sensorPrompt by remember { mutableStateOf<SensorPrompt?>(null) }
    val settingsBox = remember { mutableStateOf(app.settings) }
    val pendingStart = remember { arrayOfNulls<Runnable>(1) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val callback = fileCallback
        fileCallback = null
        if (uri != null) {
            runCatching {
                activity.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        callback?.onReceiveValue(uri?.let { arrayOf(it) })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val geo = pendingGeo
        pendingGeo = null
        if (geo != null) {
            geo.second.invoke(geo.first, granted.values.all { it }, false)
            return@rememberLauncherForActivityResult
        }
        val request = pendingPermission
        val grant = pendingGrant
        pendingPermission = null
        pendingGrant = null
        if (request == null) return@rememberLauncherForActivityResult
        if (granted.values.all { it }) request.grant(grant ?: request.resources) else request.deny()
    }

    fun launchLocation(origin: String?, callback: GeolocationPermissions.Callback) {
        pendingGeo = origin to callback
        permissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    fun saveSensors(next: BrowserSettings, page: WebView) {
        settingsBox.value = next
        WebAppRepository(activity.applicationContext).update(app.copy(settings = next))
        page.settings.setGeolocationEnabled(
            next.locationMode == SensorGate.ASK || next.locationMode == SensorGate.ALLOW,
        )
    }

    fun applyStored(request: PermissionRequest, page: WebView) {
        val grant = ArrayList<String>()
        val os = ArrayList<String>()
        for (resource in request.resources) {
            val kind = sensorKind(resource) ?: continue
            if (settingsBox.value.sensorMode(kind) == SensorGate.ALLOW) {
                grant.add(resource)
                resourcePermission(resource)?.let { os.add(it) }
            }
        }
        if (grant.isEmpty()) {
            request.deny()
            if (settingsBox.value.usesSensorSpoof()) ProfileIsolation.installSensor(page, settingsBox.value)
            return
        }
        if (os.isEmpty()) {
            request.grant(grant.toTypedArray())
            return
        }
        pendingGrant = grant.toTypedArray()
        pendingPermission = request
        permissionLauncher.launch(os.distinct().toTypedArray())
    }

    val viewState = rememberUpdatedState(webViewRef)
    val customState = rememberUpdatedState(customCallback)
    DisposableEffect(app.id) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val custom = customState.value
                if (custom != null) {
                    custom.onCustomViewHidden()
                    return
                }
                val view = viewState.value
                if (view != null && view.canGoBack()) view.goBack() else activity.finish()
            }
        }
        activity.onBackPressedDispatcher.addCallback(callback)
        onDispose { callback.remove() }
    }
    DisposableEffect(webViewRef) {
        val view = webViewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    view?.onPause()
                    view?.pauseTimers()
                    view?.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_WAIVED, true)
                }
                Lifecycle.Event.ON_START -> {
                    view?.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                    view?.onResume()
                    view?.resumeTimers()
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets.statusBars
            .union(WindowInsets.displayCutout)
            .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)),
        bottomBar = {
            val barSides = if (WindowInsets.isImeVisible) {
                WindowInsetsSides.Horizontal
            } else {
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
            }
            Box(
                Modifier.windowInsetsPadding(
                    WindowInsets.navigationBars.union(WindowInsets.displayCutout).only(barSides),
                ),
            ) {
            BrowserBar(
                loading = loading,
                onBack = { webViewRef?.takeIf { it.canGoBack() }?.goBack() },
                onForward = { webViewRef?.takeIf { it.canGoForward() }?.goForward() },
                onReload = {
                    if (loading) webViewRef?.stopLoading() else webViewRef?.reload()
                },
                onFind = { findQuery = "" },
                onShare = { webViewRef?.url?.let { share(activity, it) } },
                menu = menu,
                onMenu = { menu = it },
                desktop = desktop,
                onDesktop = {
                    val next = !desktop
                    desktop = next
                        webViewRef?.let { page ->
                        page.settings.apply {
                            userAgentString =
                                if (next) WebAppActivity.DESKTOP_AGENT else app.settings.resolvedUserAgent()
                            loadWithOverviewMode = next
                            useWideViewPort = true
                        }
                        ProfileIsolation.applyClientHints(page, app.settings, desktop = next)
                    }
                    webViewRef?.reload()
                    menu = false
                },
                onCopy = {
                    webViewRef?.url?.let { copyText(activity, it) }
                    menu = false
                },
                onPrint = {
                    val view = webViewRef
                    if (view != null) {
                        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                        printManager.print(app.title, view.createPrintDocumentAdapter(app.title), null)
                    }
                    menu = false
                },
            )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        isFocusable = true
                        isFocusableInTouchMode = true
                        ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ -> WindowInsetsCompat.CONSUMED }
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                        val profile = ProfileIsolation.bind(this, app.profileName, app.settings, app.host)
                        val secrets = SecretStore(context.applicationContext)
                        ProfileIsolation.installLogin(this, app.host, secrets.username(app.id), secrets.password(app.id))
                        val cookies = CookieLines.normalize(secrets.cookies(app.id), app.host)
                        val headers = ProfileIsolation.navigationHeaders(app.settings)
                        val started = AtomicBoolean(false)
                        val start = Runnable {
                            if (started.compareAndSet(false, true)) loadUrl(app.startUrl, headers)
                        }
                        pendingStart[0] = start
                        ProfileIsolation.seedCookies(profile, "https://${app.host}/", cookies) { post(start) }
                        postDelayed(start, 1500)
                        val log = BlockLog(context.applicationContext)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val reason = RequestGate.blockReason(
                                    request.url.toString(),
                                    app.host,
                                    app.settings.lockToHost,
                                    blockTrackers = false,
                                )
                                if (reason == null) return false
                                blocked = request.url.host ?: request.url.toString()
                                return true
                            }

                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                val response = ProfileIsolation.blockedResponse(
                                    request.url.toString(),
                                    app.settings,
                                    app.host,
                                    navigation = false,
                                )
                                if (response != null) request.url.host?.let { log.append(app.profileName, it) }
                                return response
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                pageError = null
                            }

                            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                if (request.isForMainFrame) pageError = error.description?.toString()
                            }

                            override fun onSafeBrowsingHit(
                                view: WebView,
                                request: WebResourceRequest,
                                threatType: Int,
                                callback: android.webkit.SafeBrowsingResponse,
                            ) {
                                callback.backToSafety(true)
                                pageError = "Site signalé comme dangereux"
                            }

                            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                                activity.finish()
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                loading = newProgress in 1..99
                            }

                            override fun onPermissionRequest(request: PermissionRequest) {
                                if (!SensorGate.sameSite(request.origin?.toString(), app.host)) {
                                    request.deny()
                                    pageError = "Capteur refusé : autre site"
                                    return
                                }
                                val known = request.resources.filter { sensorKind(it) != null }
                                if (known.isEmpty()) {
                                    request.deny()
                                    return
                                }
                                val asking = known.filter {
                                    settingsBox.value.sensorMode(sensorKind(it)!!) == SensorGate.ASK
                                }
                                if (asking.isEmpty()) {
                                    applyStored(request, this@apply)
                                    return
                                }
                                pendingPermission?.deny()
                                pendingPermission = request
                                sensorPrompt = SensorPrompt(
                                    origin = request.origin?.toString().orEmpty(),
                                    names = asking.joinToString(", ") { sensorLabel(it) },
                                    allowSpoof = asking.all {
                                        val kind = sensorKind(it)
                                        kind == "camera" || kind == "microphone"
                                    },
                                    choose = { choice ->
                                        var next = settingsBox.value
                                        asking.forEach { resource ->
                                            next = next.withSensor(sensorKind(resource)!!, choice)
                                        }
                                        saveSensors(next, this@apply)
                                        sensorPrompt = null
                                        if (choice == SensorGate.SPOOF) {
                                            ProfileIsolation.installSensor(this@apply, next)
                                            request.deny()
                                            this@apply.reload()
                                        } else {
                                            applyStored(request, this@apply)
                                        }
                                    },
                                    dismiss = {
                                        sensorPrompt = null
                                        pendingPermission = null
                                        request.deny()
                                    },
                                )
                            }

                            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                                if (request != null && pendingPermission == request) {
                                    pendingPermission = null
                                    sensorPrompt = null
                                }
                            }

                            override fun onGeolocationPermissionsShowPrompt(
                                origin: String?,
                                callback: GeolocationPermissions.Callback?,
                            ) {
                                if (callback == null) return
                                if (!SensorGate.sameSite(origin, app.host)) {
                                    callback.invoke(origin, false, false)
                                    return
                                }
                                when (settingsBox.value.locationMode) {
                                    SensorGate.DENY, SensorGate.SPOOF -> callback.invoke(origin, false, false)
                                    SensorGate.ALLOW -> launchLocation(origin, callback)
                                    else -> {
                                        sensorPrompt = SensorPrompt(
                                            origin = origin ?: app.host,
                                            names = "Localisation",
                                            allowSpoof = true,
                                            choose = { choice ->
                                                val next = settingsBox.value.withSensor("location", choice)
                                                saveSensors(next, this@apply)
                                                sensorPrompt = null
                                                when (choice) {
                                                    SensorGate.ALLOW -> launchLocation(origin, callback)
                                                    SensorGate.SPOOF -> {
                                                        ProfileIsolation.installSensor(this@apply, next)
                                                        callback.invoke(origin, false, false)
                                                        this@apply.reload()
                                                    }
                                                    else -> callback.invoke(origin, false, false)
                                                }
                                            },
                                            dismiss = {
                                                sensorPrompt = null
                                                callback.invoke(origin, false, false)
                                            },
                                        )
                                    }
                                }
                            }

                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?,
                            ): Boolean {
                                fileCallback?.onReceiveValue(null)
                                fileCallback = filePathCallback
                                val accept = fileChooserParams?.acceptTypes?.filter { it.isNotBlank() }?.toTypedArray()
                                filePicker.launch(if (accept.isNullOrEmpty()) arrayOf("*/*") else accept)
                                return true
                            }

                            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                if (!app.settings.popups || resultMsg == null) return false
                                val probe = WebView(view.context)
                                probe.webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(popup: WebView, request: WebResourceRequest): Boolean {
                                        view.loadUrl(request.url.toString())
                                        probe.destroy()
                                        return true
                                    }
                                }
                                val transport = resultMsg.obj as WebView.WebViewTransport
                                transport.webView = probe
                                resultMsg.sendToTarget()
                                return true
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                customCallback?.onCustomViewHidden()
                                customView = view
                                customCallback = callback
                                systemBars(activity, show = false)
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customCallback = null
                                systemBars(activity, show = true)
                            }

                            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                jsAsk = JsAsk(message.orEmpty(), false, "", { ok, _ -> if (ok) result?.confirm() else result?.cancel() })
                                return true
                            }

                            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                                jsAsk = JsAsk(message.orEmpty(), false, "", { ok, _ -> if (ok) result?.confirm() else result?.cancel() })
                                return true
                            }

                            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                                jsAsk = JsAsk(message.orEmpty(), true, defaultValue.orEmpty()) { ok, value ->
                                    if (ok) result?.confirm(value) else result?.cancel()
                                }
                                return true
                            }
                        }
                        setDownloadListener { url, _, contentDisposition, mime, _ ->
                            download(context, url, contentDisposition, mime, CookieManager.getInstance().getCookie(url))
                        }
                        setOnLongClickListener {
                            val hit = hitTestResult
                            val link = hit.extra
                            if (link != null && hit.type != WebView.HitTestResult.UNKNOWN_TYPE) {
                                copyText(context, link)
                                true
                            } else {
                                false
                            }
                        }
                        profile.cookieManager.setAcceptThirdPartyCookies(this, !app.settings.blockThirdPartyCookies)
                    }
                },
                onRelease = { view ->
                    pendingStart[0]?.let { view.removeCallbacks(it) }
                    fileCallback?.onReceiveValue(null)
                    fileCallback = null
                    pendingPermission?.deny()
                    pendingPermission = null
                    pendingGeo?.second?.invoke(pendingGeo?.first, false, false)
                    pendingGeo = null
                    if (app.settings.clearOnExit) ProfileIsolation.wipeSession(view)
                    view.onPause()
                    view.pauseTimers()
                    view.destroy()
                },
            )
            if (loading) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                )
            }
            findQuery?.let { query ->
                OutlinedTextField(
                    query,
                    {
                        findQuery = it
                        if (it.isEmpty()) webViewRef?.clearMatches() else webViewRef?.findAllAsync(it)
                    },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                    label = { Text("Dans la page") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { webViewRef?.findNext(true) }),
                    trailingIcon = {
                        IconButton(onClick = {
                            webViewRef?.clearMatches()
                            findQuery = null
                        }) { Icon(Icons.Default.Close, contentDescription = "Fermer") }
                    },
                )
            }
            blocked?.let { host ->
                Text(
                    "Navigation bloquée vers $host.",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            pageError?.let { message ->
                Text(
                    message,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
        customView?.let { shown ->
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    }
                },
                update = { frame ->
                    if (shown.parent !== frame) {
                        (shown.parent as? ViewGroup)?.removeView(shown)
                        frame.removeAllViews()
                        frame.addView(
                            shown,
                            FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            ),
                        )
                    }
                },
            )
        }
    }

    sensorPrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = prompt.dismiss,
            title = { Text(prompt.names) },
            text = {
                Column {
                    Text("${prompt.origin} demande cet accès pour cette webapp. Leurre n’ouvre pas le capteur : image fixe, silence, ou position enregistrée.")
                    if (prompt.allowSpoof) {
                        TextButton(onClick = { prompt.choose(SensorGate.SPOOF) }) { Text("Leurre") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { prompt.choose(SensorGate.ALLOW) }) { Text("Autoriser") }
            },
            dismissButton = {
                TextButton(onClick = { prompt.choose(SensorGate.DENY) }) { Text("Refuser") }
            },
        )
    }

    jsAsk?.let { ask ->
        var draft by remember(ask) { mutableStateOf(ask.defaultValue) }
        AlertDialog(
            onDismissRequest = {
                ask.resume(false, "")
                jsAsk = null
            },
            title = { Text(ask.message) },
            text = {
                if (ask.prompt) {
                    OutlinedTextField(draft, { draft = it }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    ask.resume(true, draft)
                    jsAsk = null
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    ask.resume(false, "")
                    jsAsk = null
                }) { Text("Annuler") }
            },
        )
    }
}

@Composable
private fun BrowserBar(
    loading: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReload: () -> Unit,
    onFind: () -> Unit,
    onShare: () -> Unit,
    menu: Boolean,
    onMenu: (Boolean) -> Unit,
    desktop: Boolean,
    onDesktop: () -> Unit,
    onCopy: () -> Unit,
    onPrint: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Précédent") }
        IconButton(onClick = onForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Suivant") }
        IconButton(onClick = onReload) {
            Icon(if (loading) Icons.Default.Close else Icons.Default.Refresh, contentDescription = if (loading) "Stop" else "Recharger")
        }
        IconButton(onClick = onFind) { Icon(Icons.Default.Search, contentDescription = "Rechercher") }
        IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Partager") }
        Box {
            IconButton(onClick = { onMenu(true) }) { Icon(Icons.Default.MoreVert, contentDescription = "Plus") }
            DropdownMenu(expanded = menu, onDismissRequest = { onMenu(false) }) {
                DropdownMenuItem(text = { Text(if (desktop) "Site mobile" else "Site ordinateur") }, onClick = onDesktop)
                DropdownMenuItem(text = { Text("Copier l’adresse") }, onClick = onCopy)
                DropdownMenuItem(text = { Text("Imprimer") }, onClick = onPrint)
            }
        }
    }
}

private fun systemBars(activity: ComponentActivity, show: Boolean) {
    val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
    val bars = WindowInsetsCompat.Type.systemBars()
    if (show) {
        controller.show(bars)
    } else {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(bars)
    }
}

private fun sensorKind(resource: String): String? = when (resource) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "camera"
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "microphone"
    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> "media"
    PermissionRequest.RESOURCE_MIDI_SYSEX -> "midi"
    else -> null
}

private fun sensorLabel(resource: String): String = when (resource) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> "Caméra"
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> "Micro"
    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> "Média protégé"
    PermissionRequest.RESOURCE_MIDI_SYSEX -> "MIDI"
    else -> resource
}

private fun resourcePermission(resource: String): String? = when (resource) {
    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> android.Manifest.permission.CAMERA
    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> android.Manifest.permission.RECORD_AUDIO
    else -> null
}

private fun download(context: Context, url: String, disposition: String, mime: String, cookie: String?) {
    val name = URLUtil.guessFileName(url, disposition, mime)
    val request = DownloadManager.Request(Uri.parse(url))
        .setMimeType(mime)
        .setTitle(name)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, name)
    cookie?.let { request.addRequestHeader("Cookie", it) }
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.enqueue(request)
}

private fun share(context: Context, url: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url),
            null,
        ),
    )
}

private fun copyText(context: Context, text: String) {
    val clipboard = ContextCompat.getSystemService(context, ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("lien", text))
}
