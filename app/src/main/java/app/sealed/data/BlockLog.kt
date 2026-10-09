package app.sealed.data

import android.content.Context
import java.io.File

class BlockLog(context: Context) {
    private val dir = File(context.filesDir, "block-log").apply { mkdirs() }

    @Synchronized
    fun append(profileName: String, host: String) {
        val file = file(profileName)
        val prior = if (file.exists()) file.readLines() else emptyList()
        val next = (listOf(host.lowercase()) + prior.filterNot { it == host.lowercase() }).take(100)
        file.writeText(next.joinToString("\n"))
    }

    fun recent(profileName: String): List<String> {
        val file = file(profileName)
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    fun delete(profileName: String) {
        file(profileName).delete()
    }

    private fun file(profileName: String) = File(dir, profileName.filter { it.isLetterOrDigit() })
}
