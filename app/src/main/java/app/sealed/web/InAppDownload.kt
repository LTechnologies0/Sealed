package app.sealed.web

import android.content.Context
import android.os.Environment
import android.webkit.URLUtil
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

object InAppDownload {
    const val MAX_BYTES = 100L * 1024 * 1024

    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "sealed-download").apply { isDaemon = true }
    }

    fun start(
        context: Context,
        profileName: String,
        url: String,
        disposition: String?,
        mime: String?,
        cookie: String?,
        onDone: (File) -> Unit,
        onFail: () -> Unit,
    ) {
        if (RequestGate.blockReason(url, "", lockToHost = false, blockTrackers = false) != null) {
            onFail()
            return
        }
        if (!OnionProxy.reachable()) {
            onFail()
            return
        }
        val app = context.applicationContext
        worker.execute {
            val result = runCatching {
                fetch(app, profileName, url, disposition, mime, cookie)
            }
            val file = result.getOrNull()
            if (file != null) onDone(file) else onFail()
        }
    }

    fun deleteProfile(context: Context, profileName: String) {
        runCatching { profileDir(context, profileName).deleteRecursively() }
    }

    fun safeFileName(raw: String): String {
        val leaf = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = buildString(leaf.length) {
            for (ch in leaf) {
                when {
                    ch.code < 0x20 || ch.code == 0x7f -> append('_')
                    ch == '/' || ch == '\\' -> append('_')
                    else -> append(ch)
                }
            }
        }.replace("..", "_").trim().trimStart('.').take(120)
        return if (cleaned.isBlank() || cleaned.all { it == '_' || it == '.' }) "download" else cleaned
    }

    private fun fetch(
        context: Context,
        profileName: String,
        url: String,
        disposition: String?,
        mime: String?,
        cookie: String?,
    ): File {
        val name = safeFileName(URLUtil.guessFileName(url, disposition, mime))
        val dir = profileDir(context, profileName).apply { mkdirs() }
        val dest = File(dir, name)
        check(dest.canonicalFile.parentFile == dir.canonicalFile)
        val connection = (URI(url).toURL().openConnection(OnionProxy.socks()) as HttpsURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            cookie?.let { setRequestProperty("Cookie", it) }
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("http $code")
            val finalUrl = connection.url.toString()
            if (RequestGate.blockReason(finalUrl, "", lockToHost = false, blockTrackers = false) != null) {
                error("redirect")
            }
            val declared = connection.contentLengthLong
            if (declared > MAX_BYTES) error("too large")
            dest.outputStream().use { output ->
                connection.inputStream.use { input -> copyBounded(input, output, MAX_BYTES) }
            }
        } finally {
            connection.disconnect()
        }
        return dest
    }

    private fun copyBounded(input: InputStream, output: OutputStream, maxBytes: Long) {
        val buf = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) error("too large")
            output.write(buf, 0, n)
        }
    }

    private fun profileDir(context: Context, profileName: String): File {
        val safe = profileName.filter { it.isLetterOrDigit() }.ifBlank { "profile" }
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        return File(root, safe)
    }
}
