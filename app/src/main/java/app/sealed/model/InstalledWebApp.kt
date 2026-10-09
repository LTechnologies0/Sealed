package app.sealed.model

data class InstalledWebApp(
    val id: String,
    val profileName: String,
    val title: String,
    val startUrl: String,
    val host: String,
    val createdAt: Long,
    val settings: BrowserSettings = BrowserSettings(),
)
