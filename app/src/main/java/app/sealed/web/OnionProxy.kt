package app.sealed.web

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.util.concurrent.Executor
import java.util.concurrent.Executors

object OnionProxy {
    const val HOST = "127.0.0.1"
    const val PORT = 18202

    private val io = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sealed-onion-proxy").apply { isDaemon = true }
    }
    private val executor = Executor { command -> command.run() }

    fun socks(): Proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(HOST, PORT))

    fun reachable(): Boolean = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(HOST, PORT), 400)
        }
        true
    }.getOrDefault(false)

    fun install(onReady: () -> Unit, onFail: (String) -> Unit) {
        io.execute {
            if (!reachable()) {
                onFail("OnionVPN injoignable (SOCKS $HOST:$PORT).")
                return@execute
            }
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                onFail("Ce WebView ne peut pas forcer le proxy SOCKS.")
                return@execute
            }
            val config = ProxyConfig.Builder()
                .addProxyRule("socks://$HOST:$PORT")
                .build()
            val applied = runCatching {
                ProxyController.getInstance().setProxyOverride(config, executor) { onReady() }
            }
            if (applied.isFailure) onFail("Proxy WebView refusé.")
        }
    }
}
