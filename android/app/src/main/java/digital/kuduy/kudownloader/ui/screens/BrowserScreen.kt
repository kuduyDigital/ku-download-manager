package digital.kuduy.kudownloader.ui.screens

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.DataSaverOn
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.animation.animateContentSize
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
import digital.kuduy.kudownloader.browser.isVideoPage
import digital.kuduy.kudownloader.core.Files
import digital.kuduy.kudownloader.core.Ku
import kotlinx.coroutines.launch
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
    // On a video page for a moment: read its formats now, so the download
    // button shows them at once. Once per page (results are cached and shared);
    // pages you only pass through cost nothing.
    LaunchedEffect(tab.url) {
        val u = tab.url
        if (!isVideoPage(u)) return@LaunchedEffect
        kotlinx.coroutines.delay(1500)
        if (tab.url == u) Ku.prefetchMedia(u, BrowserState.cookies(u), u)
    }
    val focus = LocalFocusManager.current
    var address by remember(tab.id) { mutableStateOf(tab.url) }
    var editing by remember { mutableStateOf(false) }
    // Follow the page's address, but never while the user is typing.
    LaunchedEffect(tab.url, editing) { if (!editing) address = tab.url }
    var menu by remember { mutableStateOf(false) }
    var tabsOpen by remember { mutableStateOf(false) }
    var mediaOpen by remember { mutableStateOf(false) }
    var orbMenu by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var finding by remember(tab.id) { mutableStateOf(false) }
    var textSizeOpen by remember { mutableStateOf(false) }
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
            val st = runCatching { Ku.call("adblockStatus").jsonObject }.getOrNull()
            val loaded = st?.get("loaded")?.jsonPrimitive?.contentOrNull != "false"
            // New default lists (e.g. pop-up filters) since the last update: fetch them once.
            val stale = st?.get("stale")?.jsonPrimitive?.contentOrNull == "true"
            if (!loaded || stale) runCatching { Ku.call("adblockUpdate") }
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

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (finding && !tab.isStart) {
                FindBar(tab, Modifier.align(Alignment.TopCenter).zIndex(2f)) { finding = false }
            }
            if (tab.isStart) {
                StartPage { open(tab, it) }
            } else {
                // Rebuilt after a renderer crash (the tab then loads its page again).
                androidx.compose.runtime.key(tab.id, BrowserSignals.renderResets) {
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
                // Long-press menu open: a tap anywhere else closes it.
                if (orbMenu && tab.canDownload) {
                    Box(
                        Modifier.fillMaxSize().clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { orbMenu = false },
                    )
                }
                // Always reachable, whatever the page does: a known video page,
                // a player on the page, or a stream it loaded.
                androidx.compose.animation.AnimatedVisibility(
                    tab.canDownload,
                    Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 20.dp),
                    enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.4f),
                    exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(targetScale = 0.4f),
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        androidx.compose.animation.AnimatedVisibility(
                            orbMenu,
                            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom),
                            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom),
                        ) {
                            Column(Modifier.padding(end = 10.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OrbAction(t("Stream online"), Icons.Filled.PlayArrow) {
                                    orbMenu = false
                                    streamOnline(ctx, tab)
                                }
                                OrbAction(t("Send to PC"), Icons.Filled.Computer) {
                                    orbMenu = false
                                    UiState.remoteSend = digital.kuduy.kudownloader.ui.RemoteSend(listOf(tab.url), referer = tab.url, cookies = BrowserState.cookies(tab.url))
                                }
                            }
                        }
                        // Tap: download (a video page shows its qualities straight away,
                        // otherwise the streams it loaded). Long-press: stream or send to a PC.
                        DownloadOrb(count = tab.media.size, onLongClick = { orbMenu = !orbMenu }) {
                            if (orbMenu) {
                                orbMenu = false
                            } else if (tab.media.isEmpty() || isVideoPage(tab.url)) {
                                UiState.quality = MediaPrefill(tab.url, BrowserState.cookies(tab.url), tab.url, tab.title)
                            } else {
                                mediaOpen = true
                            }
                        }
                    }
                }
            }
        }

        // Safari-style bottom bar: the address pill floats in thumb reach;
        // scrolling down a page shrinks it to the site name (tap to bring it back).
        val compact = BrowserSignals.compact && !editing && !tab.isStart && !finding
        Surface(color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.97f), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.animateContentSize()) {
                androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                LoadingBar(tab.progress)
                if (compact) {
                    Row(
                        Modifier.fillMaxWidth().height(30.dp).clickable { BrowserSignals.compact = false },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (tab.url.startsWith("https://")) {
                            Icon(Icons.Filled.Lock, null, Modifier.size(11.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(host(tab.url), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                } else {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(start = 8.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.animation.AnimatedVisibility(!editing && !tab.isStart) {
                        IconButton({ val v = tab.view; if (v != null && v.canGoBack()) v.goBack() else open(tab, "") }, Modifier.size(38.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Back"), Modifier.size(22.dp))
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
                        androidx.compose.material3.TextButton({ focus.clearFocus(); editing = false }) { Text(t("Cancel"), fontWeight = FontWeight.SemiBold) }
                    } else {
                        IconButton({ tabsOpen = true }, Modifier.size(38.dp)) {
                            Box(
                                Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).border(1.8.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(if (BrowserState.tabs.size > 99) "∞" else "${BrowserState.tabs.size}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                        Box {
                            IconButton({ menu = true }, Modifier.size(38.dp)) { Icon(Icons.Filled.MoreVert, t("More"), Modifier.size(22.dp)) }
                            BrowserMenu(tab, menu, { menu = false }, onHistory = { historyOpen = true }, onFind = { finding = true }, onTextSize = { textSizeOpen = true })
                        }
                    }
                }
                }
            }
        }
    }

    BrowserSignals.pill?.let { p -> PillSheet(p.page, p.src, p.title) { BrowserSignals.pill = null } }
    if (mediaOpen) MediaSheet(tab) { mediaOpen = false }
    if (tabsOpen) TabsSheet { tabsOpen = false }
    if (historyOpen) HistorySheet({ historyOpen = false }) { open(tab, it) }
    if (textSizeOpen) TextSizeDialog { textSizeOpen = false }
    Fullscreen()
}

private fun host(url: String) = runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

/**
 * The browser's download button: a small circle in the corner with a soft
 * pulsing ring, so it is noticed without covering the page. A badge counts
 * the videos found when there is more than one.
 */
/** A labelled mini button above the download button (long-press menu). */
@Composable
private fun OrbAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerHigh.copy(alpha = 0.88f),
        contentColor = scheme.onSurface,
        shadowElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.primary.copy(alpha = 0.35f)),
    ) {
        Row(Modifier.height(44.dp).padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = scheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * Watch without downloading: a video the page already loaded plays as is;
 * otherwise yt-dlp finds a link with sound, and the phone's video player
 * (VLC, MX Player, the built-in one) opens it.
 */
private fun streamOnline(ctx: android.content.Context, tab: Tab) {
    val direct = tab.media.lastOrNull()?.takeIf { !isVideoPage(tab.url) }
    val page = tab.url
    val title = tab.title
    val cookies = BrowserState.cookies(page)
    Ku.scope.launch {
        val url = direct?.url ?: run {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { UiState.toast(t("Getting the stream…")) }
            runCatching { Ku.analyze(page, false, cookies, page).streamUrl }.getOrNull()
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if (url == null) {
                UiState.toast(t("No stream found on this page. Try Download instead."))
                return@withContext
            }
            val path = url.substringBefore('?').lowercase()
            val type = when {
                path.endsWith(".m3u8") || "m3u8" in url -> "application/x-mpegURL"
                path.endsWith(".mpd") -> "application/dash+xml"
                else -> "video/*"
            }
            val play = android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(android.net.Uri.parse(url), type)
                .putExtra("title", title)
            val chooser = android.content.Intent.createChooser(play, t("Play with")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val ok = runCatching { ctx.startActivity(chooser) }.isSuccess
            if (!ok) UiState.toast(t("No video player found. Install one (like VLC) to stream."))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun DownloadOrb(count: Int, onLongClick: () -> Unit, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Pulses three times when it appears, then rests (no animation running
    // while you read or scroll the page).
    val pulse = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        repeat(3) {
            pulse.snapTo(0f)
            pulse.animateTo(1f, androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        // Two rings growing out and fading, half a beat apart.
        androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val r = 26.dp.toPx()
            val p = pulse.value
            if (p < 1f) {
                for (phase in listOf(p, (p + 0.5f) % 1f)) {
                    drawCircle(scheme.primary.copy(alpha = (1f - phase) * 0.35f), radius = r + phase * 10.dp.toPx())
                }
            }
        }
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val circle = androidx.compose.foundation.shape.CircleShape
        val accent = scheme.primary
        // Frosted glass in the theme's accent: translucent body, a light rim,
        // a sheen on the top half and a soft glow in the same colour.
        Surface(
            shape = circle,
            color = Color.Transparent,
            contentColor = Color.White,
            modifier = Modifier.size(52.dp)
                .shadow(14.dp, circle, ambientColor = accent, spotColor = accent)
                .clip(circle)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(digital.kuduy.kudownloader.ui.tone(accent, Color.White, 0.25f).copy(alpha = 0.82f), accent.copy(alpha = 0.70f))))
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(0f to Color.White.copy(alpha = 0.28f), 0.5f to Color.White.copy(alpha = 0.04f), 0.5f to Color.Transparent))
                .border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.65f), Color.White.copy(alpha = 0.12f))), circle)
                .combinedClickable(
                    onLongClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onClick = onClick,
                ),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Download, t("Download video"), Modifier.size(24.dp)) }
        }
        if (count > 1) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 6.dp).size(20.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(scheme.error).border(2.dp, scheme.surface, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (count > 9) "9+" else "$count", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Cyberpunk theme: a segmented neon bar (Cyberpunk 2077 style). Now and then
 * it glitches: slices jump sideways and split into magenta and cyan.
 */
@Composable
private fun CyberLoadingBar(p: () -> Float, alpha: () -> Float, loading: Boolean) {
    val cyan = Color(0xFF05D9E8)
    val magenta = Color(0xFFFF2A6D)
    val yellow = Color(0xFFF9F002)
    // Glitch state: a horizontal shift and a colour split, for a few frames at a time.
    var shift by remember { mutableStateOf(0f) }
    var split by remember { mutableStateOf(0f) }
    LaunchedEffect(loading) {
        val rnd = kotlin.random.Random
        while (loading) {
            kotlinx.coroutines.delay(rnd.nextLong(500, 1400))
            repeat(rnd.nextInt(2, 5)) {
                shift = rnd.nextFloat() * 24f - 12f
                split = rnd.nextFloat() * 6f + 2f
                kotlinx.coroutines.delay(rnd.nextLong(40, 90))
            }
            shift = 0f
            split = 0f
        }
    }
    Box(Modifier.fillMaxWidth().height(4.dp)) {
        if (loading || alpha() > 0.01f) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val alpha = alpha()
                val w = size.width * p()
                val h = size.height
                drawRect(cyan.copy(alpha = 0.10f * alpha), size = size)
                // Segments: 14 px blocks with 3 px gaps, like a HUD meter.
                val seg = 14f
                val gap = 3f
                fun blocks(color: Color, dx: Float) {
                    var x = 0f
                    while (x < w) {
                        val bw = minOf(seg, w - x)
                        drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(x + dx, 0f), size = androidx.compose.ui.geometry.Size(bw, h))
                        x += seg + gap
                    }
                }
                if (split > 0f) {
                    blocks(magenta.copy(alpha = 0.8f * alpha), shift - split)
                    blocks(cyan.copy(alpha = 0.8f * alpha), shift + split)
                }
                blocks(cyan.copy(alpha = alpha), shift)
                // The leading edge burns yellow.
                if (w > 6f) drawRect(yellow.copy(alpha = alpha), topLeft = androidx.compose.ui.geometry.Offset(w - 6f + shift, 0f), size = androidx.compose.ui.geometry.Size(6f, h))
            }
        }
    }
}

/**
 * Page loading bar (Helium / Chrome style): a 3 dp accent strip under the
 * address bar with a moving sheen, that finishes to the end and fades out.
 */
@Composable
private fun LoadingBar(progress: Int) {
    val loading = progress in 0..99
    val target = if (loading) progress.coerceAtLeast(8) / 100f else 1f
    // A new load starts from the left instead of shrinking back from the end.
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(loading, target) {
        if (loading && anim.value >= 0.999f) anim.snapTo(0f)
        anim.animateTo(target, androidx.compose.animation.core.tween(if (loading) 350 else 200))
    }
    val alpha = androidx.compose.animation.core.animateFloatAsState(if (loading) 1f else 0f, androidx.compose.animation.core.tween(if (loading) 120 else 450, delayMillis = if (loading) 0 else 150), label = "fade")
    if (digital.kuduy.kudownloader.ui.LocalKuColors.current.cyber) {
        CyberLoadingBar({ anim.value }, { alpha.value }, loading)
        return
    }
    val accent = MaterialTheme.colorScheme.primary
    // The sheen only exists while loading: an idle browser runs no animation
    // at all. Values are read in the draw phase (a redraw, not a re-layout).
    val sheen: androidx.compose.runtime.State<Float> = if (loading) {
        androidx.compose.animation.core.rememberInfiniteTransition(label = "sheen")
            .animateFloat(-0.3f, 1.3f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1100, easing = androidx.compose.animation.core.LinearEasing)), label = "x")
    } else {
        remember { androidx.compose.runtime.mutableFloatStateOf(2f) }
    }
    Box(Modifier.fillMaxWidth().height(3.dp)) {
        if (loading || alpha.value > 0.01f) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val a = alpha.value
                val w = size.width * anim.value
                drawRect(accent.copy(alpha = 0.18f * a), size = size)
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(accent.copy(alpha = 0.75f * a), accent.copy(alpha = a))),
                    size = androidx.compose.ui.geometry.Size(w, size.height),
                )
                val cx = size.width * sheen.value
                if (cx < w) {
                    drawRect(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f * a), Color.Transparent), startX = cx - 60f, endX = cx + 60f),
                        size = androidx.compose.ui.geometry.Size(w, size.height),
                    )
                }
            }
        }
    }
}
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
    var hadFocus by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue(address)) }
    // Select everything when editing starts, so typing replaces the address.
    LaunchedEffect(editing) {
        if (editing) {
            field = TextFieldValue(address, TextRange(0, address.length))
            // Never crash if the field is not attached yet (fast tab switches).
            runCatching { focusRequester.requestFocus() }
        }
    }
    LaunchedEffect(address) { if (address != field.text) field = field.copy(text = address, selection = TextRange(address.length)) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHighest,
        shadowElevation = 2.dp,
        modifier = modifier.height(42.dp),
    ) {
        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 1.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged {
                        if (it.isFocused) {
                            hadFocus = true
                            if (!editing) onEditing(true)
                        } else if (hadFocus) {
                            // Only a real loss of focus ends editing, not the first
                            // "unfocused" report of a field that was just shown.
                            hadFocus = false
                            if (editing) onEditing(false)
                        }
                    },
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
                // Idle: ad-block shield, lock and site, from the left. Tapping
                // anywhere starts editing.
                Row(
                    Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onEditing(true) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (adblock) {
                        Row(
                            Modifier.clip(RoundedCornerShape(8.dp)).background(scheme.primary.copy(alpha = 0.14f)).padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Shield, t("Ads blocked"), Modifier.size(13.dp), tint = scheme.primary)
                            if (tab.blocked > 0) {
                                Spacer(Modifier.width(3.dp))
                                Text(if (tab.blocked > 99) "99+" else "${tab.blocked}", style = MaterialTheme.typography.labelSmall, color = scheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (tab.private) {
                        Icon(Icons.Filled.VisibilityOff, t("Private tab"), Modifier.size(15.dp), tint = scheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (tab.url.startsWith("https://")) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(13.dp), tint = scheme.onSurfaceVariant)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(host(tab.url), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
                }
                val loading = tab.progress in 1..99
                IconButton({ if (loading) tab.view?.stopLoading() else tab.view?.reload() }, Modifier.size(36.dp)) {
                    Icon(if (loading) Icons.Filled.Close else Icons.Filled.Refresh, if (loading) t("Stop") else t("Reload"), Modifier.size(18.dp))
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
private fun BrowserMenu(tab: Tab, expanded: Boolean, onDismiss: () -> Unit, onHistory: () -> Unit, onFind: () -> Unit, onTextSize: () -> Unit) {
    if (!expanded) return
    val ctx = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    val desktop by Prefs.desktopMode.state.collectAsStateWithLifecycle()
    val dataSaver by Prefs.dataSaver.state.collectAsStateWithLifecycle()
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
                        UiState.quality = MediaPrefill(tab.url, BrowserState.cookies(tab.url), tab.url, tab.title)
                    }
                    MenuRow(Icons.Filled.Link, t("Download all links…")) { onDismiss(); tab.view?.evaluateJavascript("window.__kuLinks&&window.__kuLinks()", null) }
                    MenuRow(Icons.Filled.Image, t("Download all images…")) { onDismiss(); downloadImages(tab) }
                }
                MenuGroup {
                    MenuRow(Icons.Filled.Search, t("Find in page")) { onDismiss(); onFind() }
                    MenuRow(Icons.Filled.Translate, t("Translate page")) { onDismiss(); translate(tab) }
                    MenuRow(Icons.Filled.TextFields, t("Text size")) { onDismiss(); onTextSize() }
                    MenuRow(Icons.Filled.PictureAsPdf, t("Save as PDF")) { onDismiss(); savePdf(ctx, tab) }
                    MenuRow(Icons.Filled.AddToHomeScreen, t("Add to Home screen")) { onDismiss(); pinToHome(ctx, tab) }
                }
            }
            MenuGroup {
                MenuRow(Icons.Filled.Add, t("New tab")) { onDismiss(); BrowserState.newTab() }
                if (BrowserState.privateSupported) MenuRow(Icons.Filled.VisibilityOff, t("New private tab")) { onDismiss(); BrowserState.newTab(private = true) }
                MenuRow(Icons.Filled.Home, t("Start page")) { onDismiss(); open(tab, "") }
                MenuRow(Icons.Filled.History, t("History")) { onDismiss(); onHistory() }
            }
            MenuGroup {
                MenuRow(Icons.Filled.Computer, t("Desktop site"), checked = desktop) { Prefs.desktopMode.value = !desktop; BrowserState.applyDesktopMode() }
                MenuRow(Icons.Filled.Shield, t("Block ads"), checked = adblock) { Prefs.adblock.value = !adblock; tab.view?.reload() }
                MenuRow(Icons.Filled.DataSaverOn, t("Data saver (no images)"), checked = dataSaver) {
                    Prefs.dataSaver.value = !dataSaver
                    BrowserState.applyPageSettingsAll(reload = false)
                    // Turning it off: show the images of the open page.
                    if (dataSaver) tab.view?.reload()
                }
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

/**
 * A rounded-square tile with the site's real icon (from DuckDuckGo's icon
 * service, cached after the first load). Until it loads, or when there is
 * none, the site's initial on its colour.
 */
@Composable
private fun SiteTile(name: String, url: String, size: Int) {
    val key = host(url).ifBlank { name }
    val brand = BRAND_COLORS.entries.firstOrNull { (k, _) -> key == k || key.endsWith(".$k") }?.value
    val colors = brand ?: TILE_COLORS[(key.hashCode() and Int.MAX_VALUE) % TILE_COLORS.size].let { listOf(it, lerp(it, Color.Black, 0.18f)) }
    var loaded by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(key.isBlank() || '.' !in key) }
    val shape = RoundedCornerShape((size * 0.26f).dp)
    Box(
        Modifier.size(size.dp).clip(shape)
            .background(if (loaded) androidx.compose.ui.graphics.SolidColor(Color.White) else androidx.compose.ui.graphics.Brush.linearGradient(colors))
            .then(if (loaded) Modifier.border(1.dp, Color.Black.copy(alpha = 0.08f), shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Text(
                name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = if (size >= 48) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            )
        }
        if (!failed) {
            val ctx = LocalContext.current
            coil.compose.AsyncImage(
                model = remember(key) {
                    coil.request.ImageRequest.Builder(ctx)
                        .data("https://icons.duckduckgo.com/ip3/${key.removePrefix("m.")}.ico")
                        .crossfade(true)
                        .build()
                },
                contentDescription = null,
                onSuccess = { loaded = true },
                onError = { failed = true },
                modifier = Modifier.size((size * 0.58f).dp).clip(RoundedCornerShape((size * 0.12f).dp)),
            )
        }
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
                    UiState.quality = MediaPrefill(page, BrowserState.cookies(page), page, title)
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
                            UiState.quality = MediaPrefill(tab.url, BrowserState.cookies(tab.url), tab.url, tab.title)
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
            UiState.quality = MediaPrefill(m.url, BrowserState.cookies(m.page), m.page, m.title)
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
                    UiState.quality = MediaPrefill(tab.url, BrowserState.cookies(tab.url), tab.url, tab.title)
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

/** Find in page: matches highlighted by the WebView, with a count and next / previous. */
@Composable
private fun FindBar(tab: Tab, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }
    var current by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    DisposableEffect(tab.view) {
        val v = tab.view
        v?.setFindListener { active, count, done -> if (done) { current = if (count > 0) active + 1 else 0; total = count } }
        onDispose {
            v?.clearMatches()
            v?.setFindListener(null)
        }
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) {
        if (query.isEmpty()) {
            tab.view?.clearMatches()
            current = 0
            total = 0
        } else {
            kotlinx.coroutines.delay(150)
            tab.view?.findAllAsync(query)
        }
    }
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = scheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(Modifier.height(48.dp).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, null, Modifier.size(18.dp), tint = scheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text(t("Find in page"), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                androidx.compose.foundation.text.BasicTextField(
                    query,
                    { query = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.primary),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { tab.view?.findNext(true) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (query.isNotEmpty()) {
                Text(if (total == 0) t("No matches") else "$current/$total", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
            }
            IconButton({ tab.view?.findNext(false) }, enabled = total > 0, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.KeyboardArrowUp, t("Previous")) }
            IconButton({ tab.view?.findNext(true) }, enabled = total > 0, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.KeyboardArrowDown, t("Next")) }
            IconButton(onClose, Modifier.size(40.dp)) { Icon(Icons.Filled.Close, t("Close")) }
        }
    }
}

/** Page text size, 50–200 %, for every tab; remembered. */
@Composable
private fun TextSizeDialog(onClose: () -> Unit) {
    val zoom by Prefs.textZoom.state.collectAsStateWithLifecycle()
    fun set(v: Int) {
        Prefs.textZoom.value = v.coerceIn(50, 200)
        BrowserState.applyPageSettingsAll(reload = false)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onClose,
        title = { Text(t("Text size")) },
        text = {
            Column {
                Text("$zoom%", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
                androidx.compose.material3.Slider(
                    value = zoom.toFloat(),
                    onValueChange = { set((it / 10f).roundToInt() * 10) },
                    valueRange = 50f..200f,
                    steps = 14,
                )
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClose) { Text(t("Done")) } },
        dismissButton = { androidx.compose.material3.TextButton({ set(100) }) { Text(t("Reset")) } },
    )
}

/** Every image on the page (64 px and up), into the link picker. */
private fun downloadImages(tab: Tab) {
    val js = """(function(){var s=new Set();document.querySelectorAll('img').forEach(function(i){var u=i.currentSrc||i.src;if(u&&/^https?:/i.test(u)&&(i.naturalWidth||64)>=64)s.add(u)});""" +
        """var a=Array.from(s).map(function(u){return{url:u,text:''}});if(a.length)KuBridge.links('${tab.token}',JSON.stringify(a));return a.length})()"""
    tab.view?.evaluateJavascript(js) { n -> if (n == "0" || n == "null") UiState.toast(t("No images on this page.")) }
}

/** The page through Google Translate, into the app's language. */
private fun translate(tab: Tab) {
    val lang = java.util.Locale.getDefault().language.ifBlank { "en" }
    val u = "https://translate.google.com/translate?sl=auto&tl=$lang&u=" + java.net.URLEncoder.encode(tab.url, "UTF-8")
    open(tab, u)
}

/** The system print dialog; "Save as PDF" is its first choice. */
private fun savePdf(ctx: android.content.Context, tab: Tab) {
    val v = tab.view ?: return
    val pm = ctx.getSystemService(android.content.Context.PRINT_SERVICE) as? android.print.PrintManager ?: return
    val name = tab.title.ifBlank { host(tab.url) }.take(80)
    runCatching { pm.print(name, v.createPrintDocumentAdapter(name), null) }.onFailure { UiState.toast(t("Could not open the print dialog.")) }
}

/** A Home-screen icon that opens this site in KuDownloader's browser. */
private fun pinToHome(ctx: android.content.Context, tab: Tab) {
    if (!androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) {
        UiState.toast(t("Your launcher can't add shortcuts."))
        return
    }
    val intent = android.content.Intent(ctx, digital.kuduy.kudownloader.MainActivity::class.java)
        .setAction(digital.kuduy.kudownloader.MainActivity.ACTION_OPEN_SITE)
        .setData(android.net.Uri.parse(tab.url))
    val icon = tab.view?.favicon?.takeIf { it.width >= 16 }?.let { androidx.core.graphics.drawable.IconCompat.createWithBitmap(android.graphics.Bitmap.createScaledBitmap(it, 96, 96, true)) }
        ?: androidx.core.graphics.drawable.IconCompat.createWithResource(ctx, digital.kuduy.kudownloader.R.mipmap.ic_launcher)
    val info = androidx.core.content.pm.ShortcutInfoCompat.Builder(ctx, "site-" + tab.url.hashCode())
        .setShortLabel(tab.title.ifBlank { host(tab.url) }.take(24))
        .setLongLabel(tab.title.ifBlank { host(tab.url) }.take(60))
        .setIcon(icon)
        .setIntent(intent)
        .build()
    runCatching { androidx.core.content.pm.ShortcutManagerCompat.requestPinShortcut(ctx, info, null) }
        .onFailure { UiState.toast(t("Your launcher can't add shortcuts.")) }
}
