package app.sealed.data

import android.content.Context
import android.util.AtomicFile
import app.sealed.model.BrowserSettings
import app.sealed.model.InstalledWebApp
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

class WebAppRepository(context: Context) {
    private val file = File(context.filesDir, "webapps.json")
    private val atomic = AtomicFile(file)

    fun list(): List<InstalledWebApp> = runCatching {
        if (!file.exists()) return emptyList()
        val raw = atomic.readFully().toString(StandardCharsets.UTF_8)
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                add(array.getJSONObject(i).toApp())
            }
        }.sortedBy { it.title.lowercase() }
    }.getOrDefault(emptyList())

    fun get(id: String): InstalledWebApp? = list().firstOrNull { it.id == id }

    fun add(title: String, startUrl: String, host: String, settings: BrowserSettings): InstalledWebApp {
        val id = UUID.randomUUID().toString()
        val app = InstalledWebApp(
            id = id,
            profileName = "s" + id.replace("-", ""),
            title = title.trim().ifEmpty { host },
            startUrl = startUrl,
            host = host,
            createdAt = System.currentTimeMillis(),
            settings = settings,
        )
        val next = list() + app
        write(next)
        return app
    }

    fun update(app: InstalledWebApp) {
        write(list().map { if (it.id == app.id) app else it })
    }

    fun delete(id: String): InstalledWebApp? {
        val current = list()
        val removed = current.firstOrNull { it.id == id } ?: return null
        write(current.filterNot { it.id == id })
        return removed
    }

    private fun write(apps: List<InstalledWebApp>) {
        val array = JSONArray()
        apps.forEach { array.put(it.toJson()) }
        val bytes = array.toString().toByteArray(StandardCharsets.UTF_8)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            atomic.finishWrite(stream)
            stream = null
        } catch (error: Throwable) {
            stream?.let { atomic.failWrite(it) }
            throw error
        }
    }

    private fun InstalledWebApp.toJson() = JSONObject()
        .put("id", id)
        .put("profileName", profileName)
        .put("title", title)
        .put("startUrl", startUrl)
        .put("host", host)
        .put("createdAt", createdAt)
        .put("settings", settings.toJson())

    private fun JSONObject.toApp(): InstalledWebApp {
        val settings = if (has("settings")) {
            BrowserSettings.fromJson(getJSONObject("settings"))
        } else {
            BrowserSettings(
                customUserAgent = optString("userAgent"),
                lockToHost = optBoolean("lockToHost", true),
            )
        }
        return InstalledWebApp(
            id = getString("id"),
            profileName = getString("profileName"),
            title = getString("title"),
            startUrl = getString("startUrl"),
            host = getString("host"),
            createdAt = optLong("createdAt"),
            settings = settings,
        )
    }
}
