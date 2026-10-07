package digital.kuduy.kudownloader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.kuduy.kudownloader.core.AirMessage
import digital.kuduy.kudownloader.core.AirRequest
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.Prefs
import digital.kuduy.kudownloader.i18n.t
import digital.kuduy.kudownloader.i18n.te
import digital.kuduy.kudownloader.i18n.tf
import digital.kuduy.kudownloader.ui.screens.AboutScreen
import digital.kuduy.kudownloader.ui.screens.AddSheet
import digital.kuduy.kudownloader.ui.screens.AirSendPrompts
import digital.kuduy.kudownloader.ui.screens.AirSendScreen
import digital.kuduy.kudownloader.ui.screens.BatchScreen
import digital.kuduy.kudownloader.ui.screens.BrowserScreen
import digital.kuduy.kudownloader.ui.screens.DetailsSheet
import digital.kuduy.kudownloader.ui.screens.DownloadsScreen
import digital.kuduy.kudownloader.ui.screens.FetchScreen
import digital.kuduy.kudownloader.ui.screens.MoreScreen
import digital.kuduy.kudownloader.ui.screens.QualitySheet
import digital.kuduy.kudownloader.ui.screens.QueuesScreen
import digital.kuduy.kudownloader.ui.screens.RemoteSendSheet
import digital.kuduy.kudownloader.ui.screens.SettingsScreen
import digital.kuduy.kudownloader.ui.screens.VideoScreen
import digital.kuduy.kudownloader.ui.screens.WelcomeDialog
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun KuRoot() {
    KuTheme {
        val phase by Ku.phase.collectAsStateWithLifecycle()
        when (phase) {
            Ku.Phase.Starting -> Splash()
            Ku.Phase.Failed -> StartFailed()
            Ku.Phase.Ready -> Main()
        }
    }
}

@Composable
private fun Splash() {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(20.dp))
            Text(t("Preparing KuDownloader…"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                t("The first start unpacks the video tools; this takes a few seconds."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StartFailed() {
    val err by Ku.startError.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        Text(t("KuDownloader could not start"), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Notice(err ?: "")
    }
}

private data class Tab(val screen: Screen, val label: String, val icon: ImageVector)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Main() {
    val snack = remember { SnackbarHostState() }
    val screen = UiState.stack.last()
    val active by Ku.stats.collectAsStateWithLifecycle()
    val requests = UiState.airRequests.size

    BackHandler(enabled = UiState.stack.size > 1 || screen != Screen.Downloads) { UiState.back() }

    // Engine events the interface shows (notifications cover the background).
    LaunchedEffect(Unit) {
        Ku.events.collect { e ->
            val str = { k: String -> e[k]?.jsonPrimitive?.contentOrNull }
            when (str("type")) {
                "notice" -> UiState.toast(listOfNotNull(str("title")?.let { te(it) }, str("message")?.let { te(it) }).filter { it.isNotBlank() }.joinToString(": "))
                "airSendRequest" -> UiState.airRequests.add(Ku.json.decodeFromJsonElement<AirRequest>(e["request"]!!))
                "airSendTrust" -> {
                    val r = Ku.json.decodeFromJsonElement<digital.kuduy.kudownloader.core.AirTrustRequest>(e["request"]!!)
                    UiState.airTrusts.removeAll { it.peerFingerprint == r.peerFingerprint }
                    UiState.airTrusts.add(r)
                }
                "airSendDownload" -> UiState.airDownloads.add(Ku.json.decodeFromJsonElement<digital.kuduy.kudownloader.core.AirDownloadRequest>(e["request"]!!))
                "airSendMessage" -> UiState.airMessages.add(Ku.json.decodeFromJsonElement<AirMessage>(e["message"]!!))
                "toolDone" -> if (e["ok"]?.jsonPrimitive?.contentOrNull == "false") UiState.toast(te(str("message") ?: ""))
                "queueDone" -> UiState.toast(tf("All downloads in {name} are done.", "name" to (str("name") ?: "")))
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            if (UiState.toasts.isNotEmpty()) {
                val msg = UiState.toasts.removeAt(0)
                if (msg.isNotBlank()) snack.showSnackbar(msg, withDismissAction = true, duration = SnackbarDuration.Short)
            } else {
                kotlinx.coroutines.delay(150)
            }
        }
    }
    val offer = UiState.clipboardOffer
    LaunchedEffect(offer) {
        if (offer != null) {
            val r = snack.showSnackbar(tf("Download the copied link? {url}", "url" to offer.take(80)), actionLabel = t("Download"), withDismissAction = true, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) UiState.add = AddPrefill(url = offer, source = "clipboard")
            UiState.clipboardOffer = null
        }
    }

    val tabs = listOf(
        Tab(Screen.Downloads, t("Downloads"), Icons.Filled.Download),
        Tab(Screen.Browser, t("Browser"), Icons.Filled.Public),
        Tab(Screen.Video, t("Video"), Icons.Filled.SmartDisplay),
        Tab(Screen.AirSend, "AirSend", Icons.Filled.WifiTethering),
        Tab(Screen.More, t("More"), Icons.Filled.MoreHoriz),
    )
    val topScreen = UiState.stack.first()
    // Typing (an address, a page's form): the keyboard takes the tab bar's place
    // and the screen ends above it, so the focused field stays visible.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            // Browsing with the address bar shrunk: the page gets the whole screen.
            val immersive = screen == Screen.Browser && digital.kuduy.kudownloader.browser.BrowserSignals.compact
            if (screen.top && !imeVisible && !immersive) {
                FloatingTabBar(tabs.size, tabs.indexOfFirst { it.screen == topScreen }, { UiState.go(tabs[it].screen) }) { i, on ->
                    val tab = tabs[i]
                    val tint = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        when {
                            tab.screen == Screen.Downloads && active.active > 0 -> BadgedBox(badge = { Badge { Text("${active.active}") } }) { Icon(tab.icon, tab.label, tint = tint) }
                            tab.screen == Screen.AirSend && requests > 0 -> BadgedBox(badge = { Badge { Text("$requests") } }) { Icon(tab.icon, tab.label, tint = tint) }
                            else -> Icon(tab.icon, tab.label, tint = tint, modifier = Modifier.size(22.dp))
                        }
                        Text(tab.label, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = tint)
                    }
                }
            }
        },
    ) { pad ->
        // Only the bottom is handled here (tab bar, keyboard): each screen's own top
        // bar still keeps clear of the status bar, so the top must not be consumed.
        val bottomPad = androidx.compose.foundation.layout.PaddingValues(bottom = pad.calculateBottomPadding())
        Box(Modifier.fillMaxSize().padding(bottomPad).consumeWindowInsets(bottomPad).imePadding()) {
            AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
                when (s) {
                    Screen.Downloads -> DownloadsScreen()
                    Screen.Browser -> BrowserScreen()
                    Screen.Video -> VideoScreen()
                    Screen.AirSend -> AirSendScreen()
                    Screen.More -> MoreScreen()
                    Screen.Batch -> BatchScreen()
                    Screen.Fetch -> FetchScreen()
                    Screen.Queues -> QueuesScreen()
                    Screen.Settings -> SettingsScreen()
                    Screen.About -> AboutScreen()
                }
            }
        }
    }

    UiState.add?.let { AddSheet(it) { UiState.add = null } }
    UiState.quality?.let { QualitySheet(it) { UiState.quality = null } }
    UiState.details?.let { id -> DetailsSheet(id) { UiState.details = null } }
    AirSendPrompts()
    UiState.remoteSend?.let { RemoteSendSheet(it) { UiState.remoteSend = null } }
    val welcomed by Prefs.welcomed.state.collectAsStateWithLifecycle()
    if (!welcomed) WelcomeDialog { Prefs.welcomed.value = true }
    // After an update: what changed (checked once per start).
    // The app crashed last time: offer the report (it helps fix the cause).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var crash by remember { androidx.compose.runtime.mutableStateOf(runCatching { java.io.File(ctx.filesDir, digital.kuduy.kudownloader.CRASH_FILE).takeIf { it.exists() }?.readText() }.getOrNull()) }
    crash?.let { report ->
        val dismiss = {
            runCatching { java.io.File(ctx.filesDir, digital.kuduy.kudownloader.CRASH_FILE).delete() }
            crash = null
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(t("KuDownloader closed unexpectedly")) },
            text = { Text(t("Share the report so the problem can be fixed.")) },
            confirmButton = {
                androidx.compose.material3.TextButton({
                    digital.kuduy.kudownloader.core.Files.shareText(ctx, report)
                    dismiss()
                }) { Text(t("Share report")) }
            },
            dismissButton = { androidx.compose.material3.TextButton(dismiss) { Text(t("Close")) } },
        )
    }
    val notes = remember { pendingWhatsNew() }
    var showNotes by remember { androidx.compose.runtime.mutableStateOf(notes != null) }
    if (showNotes && notes != null && welcomed) WhatsNewDialog(notes) { showNotes = false }
}
