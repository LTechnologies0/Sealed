package app.sealed.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SecretStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        "sealed-site-secrets",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun username(appId: String): String = prefs.getString(key(appId, "u"), "").orEmpty()

    fun hasPassword(appId: String): Boolean = prefs.contains(key(appId, "p"))

    fun password(appId: String): String = prefs.getString(key(appId, "p"), "").orEmpty()

    fun cookies(appId: String): String = prefs.getString(key(appId, "c"), "").orEmpty()

    fun save(appId: String, username: String, cookies: String, password: String?, clearPassword: Boolean) {
        prefs.edit().apply {
            putString(key(appId, "u"), username)
            putString(key(appId, "c"), cookies)
            when {
                clearPassword -> remove(key(appId, "p"))
                password != null -> putString(key(appId, "p"), password)
            }
        }.apply()
    }

    fun delete(appId: String) {
        prefs.edit()
            .remove(key(appId, "u"))
            .remove(key(appId, "p"))
            .remove(key(appId, "c"))
            .apply()
    }

    private fun key(appId: String, kind: String) = "$kind:$appId"
}
