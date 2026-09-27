package digital.kuduy.kudownloader.ui.screens

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.kuduy.kudownloader.browser.BrowserSignals
import digital.kuduy.kudownloader.browser.BrowserState
import digital.kuduy.kudownloader.browser.FoundMedia
import digital.kuduy.kudownloader.browser.Tab
import digital.kuduy.kudownloader.core.Files
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.Prefs
import digital.kuduy.kudownloader.i18n.t
import digital.kuduy.kudownloader.i18n.tf
import digital.kuduy.kudownloader.ui.AddPrefill
import digital.kuduy.kudownloader.ui.ClickRow
import digital.kuduy.kudownloader.ui.MediaPrefill
import digital.kuduy.kudownloader.ui.Screen
import digital.kuduy.kudownloader.ui.UiState
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Sites on the start page. */
private val QUICK = listOf(
    "YouTube" to "https://m.youtube.com",
    "Instagram" to "https://www.instagram.com",
    "TikTok" to "https://www.tiktok.com",
    "Facebook" to "https://m.facebook.com",
    "X" to "https://x.com",
    "Vimeo" to "https://vimeo.com",
    "Reddit" to "https://www.reddit.com",
    "SoundCloud" to "https://m.soundcloud.com",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { BrowserState.load() }
    val tab = BrowserState.current ?: return
    // Switching tabs: let go of the WebViews of tabs not used for a while.
    LaunchedEffect(tab.id) { BrowserState.trim() }
    val focus = LocalFocusManager.current
    var address by remember(tab.id) { mutableStateOf(tab.url) }
    var editing by remember { mutableStateOf(false) }
    // Follow the page's address, but never while the user is typing.
    LaunchedEffect(tab.url, editing) { if (!editing) address = tab.url }
    var menu by remember { mutableStateOf(false) }
    var tabsOpen by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    val adblock by Prefs.adblock.state.collectAsStateWithLifecycle()

    // A link opened from elsewhere in the app.
    LaunchedEffect(UiState.browserUrl) {
        UiState.browserUrl?.let { u ->
            val t = if (tab.isStart) tab else BrowserState.newTab()
            open(t, u)
            UiState.browserUrl = null
        }
    }
    // Filters are needed before the first page when blocking is on.
    LaunchedEffect(adblock) {
        if (adblock) {
            val loaded = runCatching { Ku.call("adblockStatus").jsonObject["loaded"]?.jsonPrimitive?.contentOrNull == "true" }.getOrDefault(true)
            if (!loaded) runCatching { Ku.call("adblockUpdate") }
        }
    }

    // Pages stop playing and running scripts while another screen is shown.
    DisposableEffect(tab) {
        tab.view?.onResume()
        onDispose {
            tab.view?.onPause()
            // Keep sign-ins when the app is closed right after.
            android.webkit.CookieManager.getInstance().flush()
        }
    }

    BackHandler(enabled = tab.canBack || !tab.isStart) {
        val v = tab.view
        if (v != null && v.canGoBack()) v.goBack() else open(tab, "")
    }

    val chooser = BrowserSignals.chooser
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        BrowserSignals.chooser?.first?.onReceiveValue(uris.toTypedArray())
        BrowserSignals.chooser = null
    }
    LaunchedEffect(chooser) {
        chooser?.let { (_, params) ->
            // Pages may ask for ".jpg" or "image/*,.pdf": the picker needs one MIME type.
            val types = params.acceptTypes.orEmpty().flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
            val type = types.singleOrNull { "/" in it } ?: types.firstOrNull { "/" in it }?.let { t -> if (types.all { it.substringBefore("/") == t.substringBefore("/") && "/" in it }) t.substringBefore("/") + "/*" else "*/*" } ?: "*/*"
            runCatching { filePicker.launch(type) }.onFailure {
                BrowserSignals.chooser?.first?.onReceiveValue(null)
                BrowserSignals.chooser = null
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // Address bar: a Safari-style pill showing the site; tap to type.
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.animation.AnimatedVisibility(!editing && !tab.isStart) {
                IconButton({ val v = tab.view; if (v != null && v.canGoBack()) v.goBack() else open(tab, "") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back"))
                }
            }
            AddressPill(
                tab = tab,
                adblock = adblock,
                editing = editing,
                address = address,
                onAddress = { address = it },
                onEditing = { on ->
                    editing = on
                    if (on) address = tab.url
                },
                onGo = {
                    open(tab, BrowserState.resolve(address))
                    focus.clearFocus()
                },
                modifier = Modifier.weight(1f),
            )
            if (editing) {
                androidx.compose.material3.TextButton({ focus.clearFocus() }) { Text(t("Cancel"), fontWeight = FontWeight.SemiBold) }
            } else {
                IconButton({ tabsOpen = true }) {
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.8.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (BrowserState.tabs.size > 99) "∞" else "${BrowserState.tabs.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, t("More")) }
                    BrowserMenu(tab, menu, { menu = false }, onHistory = { historyOpen = true })
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (tab.progress in 1..99) {
                val p by androidx.compose.animation.core.animateFloatAsState(tab.progress / 100f, label = "load")
                LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxSize(), trackColor = Color.Transparent, drawStopIndicator = {})
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab.isStart) {
                StartPage { open(tab, it) }
            } else {
                androidx.compose.runtime.key(tab.id) {
                    AndroidView(
                        factory = { c ->
                            FrameLayout(c).apply {
                                val v = BrowserState.webView(tab, (c as? Activity) ?: ctx)
                                (v.parent as? ViewGroup)?.removeView(v)
                                addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            }
                        },
                        onRelease = { frame -> frame.removeAllViews() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Always reachable, whatever the page does: a known video page,
                // a player on the page, or a stream it loaded.
                androidx.compose.animation.AnimatedVisibility(
                    tab.canDownload,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it },
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { mediaOpen = true },
                        icon = { Icon(Icons.Filled.Download, null) },
                        text = { Text(if (tab.media.size > 1) tf("Download video ({count})", "count" to tab.media.size) else t("Download video"), fontWeight = FontWeight.SemiBold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }

    BrowserSignals.pill?.let { p -> PillSheet(p.page, p.src, p.title) { BrowserSignals.pill = null } }
    if (mediaOpen) MediaSheet(tab) { mediaOpen = false }
    if (tabsOpen) TabsSheet { tabsOpen = false }
    if (historyOpen) HistorySheet({ historyOpen = false }) { open(tab, it) }
    Fullscreen()
}

private fun host(url: String) = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

/**
 * The address field. Idle it shows the site's name centred (with a lock on
 * https); tapped, it becomes a plain text field with the whole address selected.
 */
@Composable
private fun AddressPill(
    tab: Tab,
    adblock: Boolean,
    editing: Boolean,
    address: String,
    onAddress: (String) -> Unit,
    onEditing: (Boolean) -> Unit,
    onGo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    var field by remember { mutableStateOf(TextFieldValue(address)) }
    // Select everything when editing starts, so typing replaces the address.
    LaunchedEffect(editing) {
        if (editing) {
            field = TextFieldValue(address, TextRange(0, address.length))
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(address) { if (address != field.text) field = field.copy(text = address, selection = TextRange(address.length)) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHigh,
        modifier = modifier.height(44.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing || tab.isStart) {
                Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = field,
                    onValueChange = {
                        field = it
                        onAddress(it.text)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = SolidColor(scheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = { onGo() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { if (it.isFocused != editing) onEditing(it.isFocused) },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) Text(t("Search or type an address"), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            inner()
                        }
                    },
                )
                if (editing && field.text.isNotEmpty()) {
                    IconButton({ field = TextFieldValue(""); onAddress("") }, Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Cancel, t("Clear"), Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
                    }
                } else {
                    Spacer(Modifier.width(10.dp))
                }
            } else {
                // Idle: the site, centred, like Safari. Tapping anywhere starts editing.
                Row(
                    Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEditing(true) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (adblock && tab.blocked > 0) {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(scheme.primary.copy(alpha = 0.14f)).padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Shield, t("Ads blocked"), Modifier.size(12.dp), tint = scheme.primary)
                            Spacer(Modifier.width(3.dp))
                            Text(if (tab.blocked > 99) "99+" else "${tab.blocked}", style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (tab.url.startsWith("https://")) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(13.dp), tint = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(host(tab.url), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val loading = tab.progress in 1..99
                IconButton({ if (loading) tab.view?.stopLoading() else tab.view?.reload() }, Modifier.size(40.dp)) {
                    Icon(if (loading) Icons.Filled.Close else Icons.Filled.Refresh, if (loading) t("Stop") else t("Reload"), Modifier.size(19.dp))
                }
            }
        }
    }
}

private fun open(tab: Tab, url: String) {
    tab.url = url
    tab.started = url.isNotBlank()
    if (url.isBlank()) {
        tab.title = ""
        tab.media.clear()
        tab.hasVideo = false
        return
    }
    tab.view?.loadUrl(url)
}

/** Page actions: big tiles for the everyday ones, then a grouped list (like Safari's sheet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BrowserMenu(tab: Tab, expanded: Boolean, onDismiss: () -> Unit, onHistory: () -> Unit) {
    if (!expanded) return
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val desktop by Prefs.desktopMode.state.collectAsStateWithLifecycle()
    val adblock by Prefs.adblock.state.collectAsStateWithLifecycle()
    val marked = BrowserState.bookmarks.any { it.url == tab.url }
    val page = !tab.isStart
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = scheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                    SiteTile(tab.title.ifBlank { host(tab.url) }, tab.url, 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(tab.title.ifBlank { host(tab.url) }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(host(tab.url), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionTile(Icons.AutoMirrored.Filled.ArrowBack, t("Back"), tab.canBack, Modifier.weight(1f)) { onDismiss(); tab.view?.goBack() }
                ActionTile(Icons.AutoMirrored.Filled.ArrowForward, t("Forward"), tab.canForward, Modifier.weight(1f)) { onDismiss(); tab.view?.goForward() }
                ActionTile(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, t("Bookmark"), page, Modifier.weight(1f)) { onDismiss(); BrowserState.toggleBookmark(tab.url, tab.title) }
                ActionTile(Icons.Filled.Share, t("Share page"), page, Modifier.weight(1f)) { onDismiss(); Files.shareText(ctx, tab.url) }
            }
            if (page) {
                MenuGroup {
                    MenuRow(Icons.Filled.Movie, t("Download video on this page")) {
                        onDismiss()
                        UiState.media = MediaPrefill(tab.url, BrowserState.cookies(tab.url), tab.url, tab.title)
                        UiState.go(Screen.Video)
                    }
                    MenuRow(Icons.Filled.Link, t("Download all links…")) { onDismiss(); tab.view?.evaluateJavascript("window.__kuLinks&&window.__kuLinks()", null) }
                }
            }
            MenuGroup {
                MenuRow(Icons.Filled.Add, t("New tab")) { onDismiss(); BrowserState.newTab() }
                MenuRow(Icons.Filled.Home, t("Start page")) { onDismiss(); open(tab, "") }
                MenuRow(Icons.Filled.History, t("History")) { onDismiss(); onHistory() }
            }
            MenuGroup {
                MenuRow(Icons.Filled.Computer, t("Desktop site"), checked = desktop) { Prefs.desktopMode.value = !desktop; BrowserState.applyDesktopMode() }
                MenuRow(Icons.Filled.Shield, t("Block ads"), checked = adblock) { Prefs.adblock.value = !adblock; tab.view?.reload() }
                MenuRow(Icons.Filled.Settings, t("Browser settings")) {
                    onDismiss()
                    UiState.settingsSection = "browser"
                    UiState.go(Screen.Settings)
                }
            }
        }
    }
}

@Composable
private fun ActionTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val alpha = if (enabled) 1f else 0.38f
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerHighest,
        modifier = modifier.height(76.dp).clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = scheme.onSurface.copy(alpha = alpha))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = scheme.onSurface.copy(alpha = alpha), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MenuGroup(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, checked: Boolean? = null, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = if (checked != null) 6.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = scheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (checked != null) androidx.compose.material3.Switch(checked, { onClick() })
    }
}

@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    val blocked by Prefs.adsBlocked.state.collectAsStateWithLifecycle()
    val adblock by Prefs.adblock.state.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme
    val full: androidx.compose.foundation.lazy.grid.LazyGridItemSpanScope.() -> androidx.compose.foundation.lazy.grid.GridItemSpan = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }
    LazyVerticalGrid(
        GridCells.Adaptive(76.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = full) {
            Text(t("Favorites"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
        val sites = BrowserState.bookmarks.map { it.title to it.url } + QUICK.filter { q -> BrowserState.bookmarks.none { it.url == q.second } }
        items(sites, key = { it.second }) { (name, url) ->
            Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(url) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                SiteTile(name, url, 60)
                Spacer(Modifier.height(6.dp))
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
            }
        }
        item(span = full) {
            Surface(shape = RoundedCornerShape(18.dp), color = scheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(scheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Shield, null, tint = scheme.primary)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Privacy report"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (!adblock) t("Ad and tracker blocking is off.") else if (blocked > 0) tf("{count} ads and trackers blocked so far", "count" to blocked) else t("Tap the KuDownload button on any video to save it. Ads and trackers are blocked."),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        val recent = BrowserState.history.take(6)
        if (recent.isNotEmpty()) {
            item(span = full) {
                Text(t("Recently visited"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
            items(recent, key = { "h:" + it.url }, span = { full() }) { h ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(h.url) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SiteTile(h.title.ifBlank { host(h.url) }, h.url, 36)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.title.ifBlank { host(h.url) }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(host(h.url), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Tile colours for sites (picked by host, so a site keeps its colour). */
private val TILE_COLORS = listOf(
    Color(0xFFE5484D), Color(0xFFF76B15), Color(0xFFFFB224), Color(0xFF30A46C),
    Color(0xFF12A594), Color(0xFF0090FF), Color(0xFF3E63DD), Color(0xFF8E4EC6),
    Color(0xFFD6409F), Color(0xFF6F6E77),
)

/** Well-known sites keep their own colours. */
private val BRAND_COLORS = mapOf(
    "youtube.com" to listOf(Color(0xFFFF3B30), Color(0xFFD70015)),
    "youtu.be" to listOf(Color(0xFFFF3B30), Color(0xFFD70015)),
    "instagram.com" to listOf(Color(0xFFFEDA75), Color(0xFFD62976), Color(0xFF4F5BD5)),
    "tiktok.com" to listOf(Color(0xFF2B2B2B), Color(0xFF000000)),
    "facebook.com" to listOf(Color(0xFF1877F2), Color(0xFF0B5BD3)),
    "x.com" to listOf(Color(0xFF2B2B2B), Color(0xFF000000)),
    "twitter.com" to listOf(Color(0xFF2B2B2B), Color(0xFF000000)),
    "vimeo.com" to listOf(Color(0xFF1AB7EA), Color(0xFF0E8DB8)),
    "reddit.com" to listOf(Color(0xFFFF5700), Color(0xFFE04300)),
    "soundcloud.com" to listOf(Color(0xFFFF8800), Color(0xFFFF3300)),
)

/** A rounded-square tile with the site's initial (a stand-in for its icon). */
@Composable
private fun SiteTile(name: String, url: String, size: Int) {
    val key = host(url).ifBlank { name }
    val brand = BRAND_COLORS.entries.firstOrNull { (k, _) -> key == k || key.endsWith(".$k") }?.value
    val colors = brand ?: TILE_COLORS[(key.hashCode() and Int.MAX_VALUE) % TILE_COLORS.size].let { listOf(it, lerp(it, Color.Black, 0.18f)) }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.26f).dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (size >= 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
        )
    }
}

/** The KuDownload button was tapped on a video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PillSheet(page: String, src: String?, title: String, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title.ifBlank { host(page) }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FilledTonalButton(
                {
                    onClose()
                    UiState.media = MediaPrefill(page, BrowserState.cookies(page), page, title)
                    UiState.go(Screen.Video)
                },
                Modifier.fillMaxWidth(),
            ) { Text(t("Choose quality")) }
            if (src != null) {
                OutlinedButton(
                    {
                        onClose()
                        UiState.add = AddPrefill(url = src, referer = page, cookies = BrowserState.cookies(src), source = "browser")
                    },
                    Modifier.fillMaxWidth(),
                ) { Text(t("Download this video file")) }
            }
            androidx.compose.material3.TextButton(
                {
                    onClose()
                    UiState.remoteSend = digital.kuduy.kudownloader.ui.RemoteSend(listOf(src ?: page), referer = page, cookies = BrowserState.cookies(src ?: page))
                },
                Modifier.fillMaxWidth(),
            ) { Text(t("Download on a computer…")) }
            Text(t("Choose quality works on YouTube, Instagram, TikTok, X, Facebook and 1,000+ sites."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaSheet(tab: Tab, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tab.title.ifBlank { host(tab.url) }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    androidx.compose.material3.Button(
                        {
                            onClose()
                            UiState.media = MediaPrefill(tab.url, BrowserState.cookies(tab.url), tab.url, tab.title)
                            UiState.go(Screen.Video)
                        },
                        Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Icon(Icons.Filled.Download, null)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Choose quality"), fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        {
                            onClose()
                            UiState.remoteSend = digital.kuduy.kudownloader.ui.RemoteSend(listOf(tab.url), referer = tab.url, cookies = BrowserState.cookies(tab.url))
                        },
                        Modifier.fillMaxWidth(),
                    ) { Text(t("Download on a computer…")) }
                }
                if (tab.media.isNotEmpty()) {
                    HorizontalDivider()
                    Text(t("Streams on this page"), Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            items(tab.media.toList().asReversed(), key = { it.url }) { m -> MediaRow(m, onClose) }
        }
    }
}

@Composable
private fun MediaRow(m: FoundMedia, onClose: () -> Unit) {
    val stream = m.kind == "m3u8" || m.kind == "mpd"
    ClickRow(
        m.url.substringBefore('?').substringAfterLast('/').ifBlank { m.url },
        (if (stream) t("Stream") else m.kind.uppercase()) + " · " + host(m.url),
    ) {
        onClose()
        if (stream) {
            UiState.media = MediaPrefill(m.url, BrowserState.cookies(m.page), m.page, m.title)
            UiState.go(Screen.Video)
        } else {
            UiState.add = AddPrefill(url = m.url, referer = m.page, cookies = BrowserState.cookies(m.url), source = "browser")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabsSheet(onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onClose, containerColor = scheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tf("{count} tabs", "count" to BrowserState.tabs.size), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilledTonalButton({ BrowserState.newTab(); onClose() }, colors = ButtonDefaults.filledTonalButtonColors()) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("New tab"))
            }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(BrowserState.tabs.toList(), key = { it.id }) { t0 ->
                val selected = t0 == BrowserState.current
                val name = t0.title.ifBlank { if (t0.isStart) t("Start page") else host(t0.url) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = scheme.surfaceContainerHighest,
                    border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, scheme.primary) else null,
                    modifier = Modifier.fillMaxWidth().height(150.dp).clickable { BrowserState.current = t0; onClose() },
                ) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            IconButton({ BrowserState.close(t0) }, Modifier.size(36.dp)) { Icon(Icons.Filled.Close, t("Close tab"), Modifier.size(16.dp)) }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f).background(scheme.surfaceContainer), contentAlignment = Alignment.Center) {
                            if (t0.isStart) {
                                Icon(Icons.Filled.Home, null, Modifier.size(32.dp), tint = scheme.onSurfaceVariant)
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    SiteTile(name, t0.url, 44)
                                    Spacer(Modifier.height(8.dp))
                                    Text(host(t0.url), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(onClose: () -> Unit, onOpen: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t("History"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.TextButton({ BrowserState.clearHistory() }) { Text(t("Clear history")) }
                }
            }
            if (BrowserState.history.isEmpty()) item { Text(t("Nothing here yet."), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(BrowserState.history.toList(), key = { it.url + it.at }) { h ->
                ClickRow(h.title.ifBlank { h.url }, h.url) {
                    onClose()
                    onOpen(h.url)
                }
            }
        }
    }
}

/** Full-screen video from a page. */
@Composable
private fun Fullscreen() {
    val fs = BrowserSignals.fullscreen ?: return
    val activity = LocalContext.current as? Activity
    DisposableEffect(fs) {
        val w = activity?.window
        val c = w?.let { WindowCompat.getInsetsController(it, it.decorView) }
        c?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { c?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    BackHandler {
        fs.second.onCustomViewHidden()
        BrowserSignals.fullscreen = null
    }
    Box(Modifier.fillMaxSize()) {
    AndroidView(
        factory = { c ->
            FrameLayout(c).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                (fs.first.parent as? ViewGroup)?.removeView(fs.first)
                addView(fs.first, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            }
        },
        modifier = Modifier.fillMaxSize().background(Color.Black),
    )
        // Download while watching full screen.
        BrowserState.current?.let { tab ->
            androidx.compose.material3.FilledTonalButton(
                {
                    fs.second.onCustomViewHidden()
                    BrowserSignals.fullscreen = null
                    UiState.media = MediaPrefill(tab.url, BrowserState.cookies(tab.url), tab.url, tab.title)
                    UiState.go(Screen.Video)
                },
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.Black.copy(alpha = 0.55f), contentColor = Color.White),
            ) {
                Icon(Icons.Filled.Download, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(t("Download"))
            }
        }
    }
}

