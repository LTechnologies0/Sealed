package app.sealed.data

import android.content.Context
import app.sealed.model.BrowserSettings
import org.json.JSONObject
import java.io.File

class SettingsRepository(context: Context) {
    private val file = File(context.filesDir, "browser-defaults.json")

    fun defaults(): BrowserSettings {
        if (!file.exists()) return BrowserSettings()
        return runCatching { BrowserSettings.fromJson(JSONObject(file.readText())) }.getOrDefault(BrowserSettings())
    }

    fun saveDefaults(settings: BrowserSettings) {
        file.writeText(settings.toJson().toString())
    }
}
