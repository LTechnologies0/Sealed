package app.sealed

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.sealed.data.BlockLog
import app.sealed.data.SecretStore
import app.sealed.data.SettingsRepository
import app.sealed.data.WebAppRepository
import app.sealed.model.InstalledWebApp
import app.sealed.ui.BrowserSettingsScreen
import app.sealed.ui.SiteLogin
import app.sealed.ui.theme.SealedTheme
import app.sealed.ui.theme.applyColorScheme
import app.sealed.web.InAppDownload
import app.sealed.web.ProfileIsolation
import app.sealed.web.UrlPolicy

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = WebAppRepository(this)
        val defaults = SettingsRepository(this)
        applyColorScheme(defaults.defaults().colorScheme)
        setContent {
            var scheme by remember { mutableStateOf(defaults.defaults().colorScheme) }
            SealedTheme(colorScheme = scheme) {
                Home(repo, defaults, onScheme = { next ->
                    scheme = next
                    applyColorScheme(next)
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Home(
    repo: WebAppRepository,
    defaults: SettingsRepository,
    onScheme: (String) -> Unit,
) {
    val activity = androidx.compose.ui.platform.LocalContext.current as ComponentActivity
    var apps by remember { mutableStateOf(repo.list()) }
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<InstalledWebApp?>(null) }
    var editingDefaults by remember { mutableStateOf(false) }
    val profiles = ProfileIsolation.supported()
    val secrets = remember { SecretStore(activity) }

    DisposableEffect(repo) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                apps = repo.list()
                onScheme(defaults.defaults().colorScheme)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    editing?.let { app ->
        BrowserSettingsScreen(
            title = app.title,
            initial = app.settings,
            onBack = { editing = null },
            onSave = { settings ->
                repo.update(app.copy(settings = settings))
                apps = repo.list()
                editing = null
            },
            blockedHosts = BlockLog(activity).recent(app.profileName),
            siteLogin = SiteLogin(
                username = secrets.username(app.id),
                cookies = secrets.cookies(app.id),
                hasPassword = secrets.hasPassword(app.id),
            ),
            onSaveLogin = { login ->
                secrets.save(app.id, login.username, login.cookies, login.password.ifBlank { null }, login.clearPassword)
            },
        )
        return
    }
    if (editingDefaults) {
        BrowserSettingsScreen(
            title = "Réglages par défaut",
            initial = defaults.defaults(),
            onBack = { editingDefaults = false },
            onSave = {
                defaults.saveDefaults(it)
                onScheme(it.colorScheme)
                editingDefaults = false
            },
        )
        return
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Sealed") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                actions = {
                    FilledTonalIconButton(onClick = { editingDefaults = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Réglages")
                    }
                },
            )
        },
        floatingActionButton = {
            if (profiles) {
                LargeFloatingActionButton(onClick = { showAdd = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text(
                if (profiles) {
                    "Chaque site a son propre profil WebView : cookies, stockage et permissions ne sortent pas de l’app."
                } else {
                    "Ce WebView ne prend pas en charge les profils multiples. Mets à jour Android System WebView."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps, key = { it.id }) { app ->
                    AppRow(
                        app = app,
                        onOpen = {
                            activity.startActivity(
                                Intent(activity, WebAppActivity::class.java)
                                    .putExtra(WebAppActivity.EXTRA_ID, app.id),
                            )
                        },
                        onSettings = { editing = app },
                        onDelete = {
                            ProfileIsolation.delete(app.profileName)
                            BlockLog(activity).delete(app.profileName)
                            InAppDownload.deleteProfile(activity, app.profileName)
                            secrets.delete(app.id)
                            repo.delete(app.id)
                            apps = repo.list()
                        },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onSave = { title, url, username, password, cookies ->
                val start = UrlPolicy.normalizeStartUrl(url)
                val host = start?.let { UrlPolicy.hostOf(it) }
                if (start != null && host != null) {
                    val created = repo.add(title.ifBlank { host }, start, host, defaults.defaults())
                    secrets.save(created.id, username, cookies, password.ifBlank { null }, clearPassword = false)
                    apps = repo.list()
                    showAdd = false
                }
            },
        )
    }
}

@Composable
private fun AppRow(
    app: InstalledWebApp,
    onOpen: () -> Unit,
    onSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            headlineContent = { Text(app.title) },
            supportingContent = { Text(app.host) },
            trailingContent = {
                Row {
                    FilledTonalIconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Réglages du site")
                    }
                    FilledTonalIconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                    }
                }
            },
            colors = androidx.compose.material3.ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
    }
}

@Composable
private fun AddDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, url: String, username: String, password: String, cookies: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var cookies by remember { mutableStateOf("") }
    val valid = UrlPolicy.normalizeStartUrl(url) != null
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(
                Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Sceller un site", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("URL https") }, singleLine = true)
                OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("E-mail ou identifiant") }, singleLine = true)
                OutlinedTextField(
                    password,
                    { password = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(cookies, { cookies = it }, Modifier.fillMaxWidth(), label = { Text("Cookies, un par ligne") }, minLines = 2)
                if (url.isNotBlank() && !valid) {
                    Text("Seules les URL https vers un nom de domaine sont acceptées.")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Annuler") }
                    Button(enabled = valid, onClick = { onSave(title, url, username, password, cookies) }) {
                        Text("Installer")
                    }
                }
            }
        }
    }
}
