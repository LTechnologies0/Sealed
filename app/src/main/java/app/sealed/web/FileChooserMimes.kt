package app.sealed.web

import android.webkit.MimeTypeMap

object FileChooserMimes {
    fun filters(acceptTypes: Array<String>?): Array<String> {
        val mapped = acceptTypes.orEmpty().flatMap { raw ->
            raw.split(',', '|').map { it.trim() }.mapNotNull { token ->
                val clean = token.substringBefore(';').trim()
                when {
                    clean.isEmpty() -> null
                    clean == "*/*" -> "*/*"
                    clean.startsWith(".") -> extensionType(clean.drop(1))
                    '/' in clean && ' ' !in clean -> clean
                    else -> extensionType(clean)
                }
            }
        }.distinct()
        return if (mapped.isEmpty()) arrayOf("*/*") else mapped.toTypedArray()
    }

    private fun extensionType(extension: String): String? {
        val ext = extension.lowercase().trimStart('.')
        if (ext.isEmpty()) return null
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }
}
