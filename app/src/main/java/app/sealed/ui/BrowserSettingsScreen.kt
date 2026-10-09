package app.sealed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.sealed.model.BrowserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserSettingsScreen(
    title: String,
    initial: BrowserSettings,
    onBack: () -> Unit,
    onSave: (BrowserSettings) -> Unit,
    blockedHosts: List<String> = emptyList(),
    siteLogin: SiteLogin? = null,
    onSaveLogin: (SiteLogin) -> Unit = {},
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var username by remember(siteLogin) { mutableStateOf(siteLogin?.username.orEmpty()) }
    var password by remember(siteLogin) { mutableStateOf("") }
    var cookies by remember(siteLogin) { mutableStateOf(siteLogin?.cookies.orEmpty()) }
    var clearPassword by remember(siteLogin) { mutableStateOf(false) }
    var latitude by remember(initial) { mutableStateOf(initial.spoofLatitude.toString()) }
    var longitude by remember(initial) { mutableStateOf(initial.spoofLongitude.toString()) }
    var accuracy by remember(initial) { mutableStateOf(initial.spoofAccuracyMeters.toString()) }
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                navigationIcon = { TextButton(onClick = onBack) { Text("Retour") } },
                actions = {
                    Button(onClick = {
                        onSave(draft)
                        if (siteLogin != null) {
                            onSaveLogin(
                                siteLogin.copy(
                                    username = username,
                                    cookies = cookies,
                                    password = password,
                                    clearPassword = clearPassword,
                                ),
                            )
                        }
                    }) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Persona Pixel 10")
            Toggle("Imiter Chrome sur Pixel 10", draft.pixelPersona) { draft = draft.copy(pixelPersona = it) }
            Text(
                "User-agent et client hints seulement, via le WebView. Écran, canvas, audio, WebGL et fuseau restent ceux du téléphone : les masquer en JavaScript est détectable.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!draft.pixelPersona) {
                OutlinedTextField(
                    draft.customUserAgent,
                    { draft = draft.copy(customUserAgent = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("User-Agent personnalisé") },
                )
            }
            OutlinedTextField(
                draft.language,
                { draft = draft.copy(language = it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Langue") },
                singleLine = true,
            )
            if (siteLogin != null) {
                Section("Connexion")
                OutlinedTextField(
                    username,
                    { username = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("E-mail ou identifiant") },
                    singleLine = true,
                )
                OutlinedTextField(
                    password,
                    { password = it; clearPassword = false },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (siteLogin.hasPassword) "Nouveau mot de passe" else "Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (siteLogin.hasPassword) {
                    TextButton(onClick = { clearPassword = true; password = "" }) {
                        Text(if (clearPassword) "Mot de passe effacé à l’enregistrement" else "Effacer le mot de passe")
                    }
                }
                OutlinedTextField(
                    cookies,
                    { cookies = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cookies, un par ligne") },
                    minLines = 3,
                )
                Text(
                    "Les cookies sont posés sur ce domaine avant l’ouverture. L’identifiant et le mot de passe remplissent le formulaire qui contient un champ mot de passe. Le bouton de connexion reste à toi.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Section("Confidentialité et sécurité")
            Toggle("Cookies tiers bloqués", draft.blockThirdPartyCookies) { draft = draft.copy(blockThirdPartyCookies = it) }
            Toggle("Cookies du site", draft.acceptCookies) { draft = draft.copy(acceptCookies = it) }
            Toggle("Do Not Track", draft.doNotTrack) { draft = draft.copy(doNotTrack = it) }
            Toggle("Global Privacy Control", draft.globalPrivacyControl) { draft = draft.copy(globalPrivacyControl = it) }
            Toggle("Protection contre la navigation dangereuse", draft.safeBrowsing) { draft = draft.copy(safeBrowsing = it) }
            Toggle("Effacer cookies, cache et stockage à la fermeture", draft.clearOnExit) { draft = draft.copy(clearOnExit = it) }
            Toggle("Bloquer les hôtes de mesure d’audience", draft.blockTrackers) { draft = draft.copy(blockTrackers = it) }
            Text(
                "HTTPS uniquement. Le HTTP, le contenu mixte et les pixels listés sont refusés, y compris depuis un service worker. WebRTC et l’adresse IP ne sont pas coupés : le WebView ne le permet pas sans script détectable.",
                style = MaterialTheme.typography.bodySmall,
            )
            Section("Capteurs de cette webapp")
            Text(
                "Chaque demande est mémorisée ici. Autoriser ouvre la permission Android. Leurre laisse le site hors du capteur réel : image fixe, piste silencieuse, position ci-dessous. Un site peut détecter ce remplacement.",
                style = MaterialTheme.typography.bodySmall,
            )
            SensorMode("Caméra", draft.camera) { draft = draft.copy(camera = it) }
            SensorMode("Micro", draft.microphone) { draft = draft.copy(microphone = it) }
            SensorMode("Localisation", draft.locationMode) {
                draft = draft.copy(locationMode = it, geolocation = it == "allow")
            }
            SensorMode("Média protégé", draft.protectedMedia) { draft = draft.copy(protectedMedia = it) }
            SensorMode("MIDI", draft.midiSysex) { draft = draft.copy(midiSysex = it) }
            if (draft.camera == "spoof" || draft.microphone == "spoof" || draft.locationMode == "spoof") {
                OutlinedTextField(
                    latitude,
                    { value ->
                        latitude = value
                        value.replace(',', '.').toDoubleOrNull()?.let {
                            draft = draft.copy(spoofLatitude = it.coerceIn(-90.0, 90.0))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Latitude du leurre") },
                    singleLine = true,
                )
                OutlinedTextField(
                    longitude,
                    { value ->
                        longitude = value
                        value.replace(',', '.').toDoubleOrNull()?.let {
                            draft = draft.copy(spoofLongitude = it.coerceIn(-180.0, 180.0))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Longitude du leurre") },
                    singleLine = true,
                )
                OutlinedTextField(
                    accuracy,
                    { value ->
                        accuracy = value
                        value.replace(',', '.').toDoubleOrNull()?.let {
                            draft = draft.copy(spoofAccuracyMeters = it.coerceIn(1.0, 10000.0))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Précision du leurre, en mètres") },
                    singleLine = true,
                )
            }
            Section("Referrer")
            SchemePicker(
                current = draft.referrerPolicy,
                options = listOf(
                    "no-referrer" to "Aucun",
                    "strict-origin-when-cross-origin" to "Origine stricte",
                    "off" to "Navigateur",
                ),
                onChange = { draft = draft.copy(referrerPolicy = it) },
            )
            if (blockedHosts.isNotEmpty()) {
                Section("Hôtes bloqués")
                blockedHosts.take(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }

            Section("Paramètres des sites")
            Toggle("JavaScript", draft.javascript) { draft = draft.copy(javascript = it) }
            Toggle("Images", draft.images) { draft = draft.copy(images = it) }
            Toggle("Pop-ups", draft.popups) { draft = draft.copy(popups = it) }
            Toggle("Lecture automatique", draft.autoplay) { draft = draft.copy(autoplay = it) }
            Toggle("Stockage local", draft.domStorage) { draft = draft.copy(domStorage = it) }
            Toggle("Rester sur le domaine installé", draft.lockToHost) { draft = draft.copy(lockToHost = it) }

            Section("Apparence")
            Text("Taille du texte ${draft.textZoomPercent} %")
            Slider(
                value = draft.textZoomPercent.toFloat(),
                onValueChange = { draft = draft.copy(textZoomPercent = it.toInt()) },
                valueRange = 50f..200f,
            )
            Toggle("Zoom des pages", draft.zoomEnabled) { draft = draft.copy(zoomEnabled = it) }
            SchemePicker(
                current = draft.colorScheme,
                options = listOf("system" to "Système", "light" to "Clair", "dark" to "Sombre"),
                onChange = { draft = draft.copy(colorScheme = it) },
            )
        }
    }
}

data class SiteLogin(
    val username: String,
    val cookies: String,
    val hasPassword: Boolean,
    val password: String = "",
    val clearPassword: Boolean = false,
)

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = androidx.compose.material3.ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SensorMode(label: String, current: String, onChange: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    val options = if (label == "Média protégé" || label == "MIDI") {
        listOf("ask" to "Demander", "deny" to "Refuser", "allow" to "Autoriser")
    } else {
        listOf("ask" to "Demander", "deny" to "Refuser", "allow" to "Autoriser", "spoof" to "Leurre")
    }
    SchemePicker(current = current, options = options, onChange = onChange)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchemePicker(current: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (id, label) ->
            FilterChip(
                selected = current == id,
                onClick = { onChange(id) },
                label = { Text(label) },
            )
        }
    }
}
