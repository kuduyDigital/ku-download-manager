package digital.kuduy.kudownloader.browser

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import digital.kuduy.kudownloader.core.BrowserCookie
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.Prefs
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.net.URI
import java.util.UUID

@Serializable
data class Bookmark(val url: String, val title: String, val at: Long = System.currentTimeMillis())

/** A media stream a page loaded (found by watching its requests). */
data class FoundMedia(val url: String, val kind: String, val page: String, val title: String)

class Tab(val id: String = UUID.randomUUID().toString(), val private: Boolean = false) {
    var url by mutableStateOf("")
    var title by mutableStateOf("")
    var progress by mutableIntStateOf(100)
    var canBack by mutableStateOf(false)
    var canForward by mutableStateOf(false)
    var blocked by mutableIntStateOf(0)
    val media = mutableStateListOf<FoundMedia>()
    /** The page shows a <video> (reported by the page script). */
    var hasVideo by mutableStateOf(false)
    /** The page that opened this tab as a pop-up, until its first page has loaded. */
    @Volatile var opener: String? = null
    /** Something was opened in this tab (a popup gets content before any address). */
    var started by mutableStateOf(false)
    var view: WebView? = null
    /** Back/forward list of a tab whose WebView was let go to save memory. */
    var saved: android.os.Bundle? = null
    var lastUsed: Long = System.currentTimeMillis()
    /** The address whose element-hiding rules were applied (once per page). */
    @Volatile var cosmeticFor: String? = null
    /** Secret the page script must pass back, so other scripts can't drive the bridge. */
    val token: String = UUID.randomUUID().toString().replace("-", "")
    val isStart get() = !started && url.isBlank()
    /** Offer "Download video": a known video page, a player on the page, or media it loaded. */
    val canDownload get() = started && (hasVideo || media.isNotEmpty() || isVideoPage(url))
}

/**
 * Browser tabs and their WebViews. They live outside the composition (and
 * survive screen switches); each WebView sits in a context wrapper that is
 * pointed at the current activity.
 */
object BrowserState {
    val tabs = mutableStateListOf<Tab>()
    var current by mutableStateOf<Tab?>(null)
    val bookmarks = mutableStateListOf<Bookmark>()
    val history = mutableStateListOf<Bookmark>()
    private var loaded = false

    const val MOBILE_UA_SUFFIX = " KuDownloader"

    fun load() {
        if (loaded) return
        loaded = true
        runCatching { bookmarks.addAll(Ku.json.decodeFromString<List<Bookmark>>(Prefs.bookmarks.value)) }
        runCatching { history.addAll(Ku.json.decodeFromString<List<Bookmark>>(Prefs.history.value)) }
        if (tabs.isEmpty()) newTab()
    }

    fun newTab(url: String? = null, private: Boolean = false): Tab {
        val t = Tab(private = private)
        tabs.add(t)
        current = t
        if (url != null) t.url = url
        return t
    }

    fun close(t: Tab) {
        val i = tabs.indexOf(t)
        tabs.remove(t)
        t.view?.let {
            it.stopLoading()
            it.destroy()
        }
        t.view = null
        if (tabs.isEmpty()) newTab()
        if (current == t) current = tabs.getOrNull((i - 1).coerceAtLeast(0)) ?: tabs.first()
        // Last private tab gone: forget its cookies and site data.
        if (t.private && tabs.none { it.private }) clearPrivate()
    }

    private const val PRIVATE_PROFILE = "ku-private"

    /** Private tabs get their own cookies and storage (a separate WebView profile). */
    val privateSupported: Boolean get() = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    private fun privateProfile() = runCatching { androidx.webkit.ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE) }.getOrNull()

    private fun clearPrivate() {
        if (!privateSupported) return
        privateProfile()?.let { p ->
            runCatching { p.cookieManager.removeAllCookies(null) }
            runCatching { p.webStorage.deleteAllData() }
        }
    }

    /** Cookies of the tab's profile (private tabs keep theirs apart). */
    fun cookieManager(t: Tab?): CookieManager =
        (if (t?.private == true && privateSupported) privateProfile()?.cookieManager else null) ?: CookieManager.getInstance()

    /** Text size and data saver, for one page or all of them. */
    fun applyPageSettings(v: WebView) {
        v.settings.textZoom = Prefs.textZoom.value
        v.settings.blockNetworkImage = Prefs.dataSaver.value
    }

    fun applyPageSettingsAll(reload: Boolean) {
        tabs.forEach { t -> t.view?.let { applyPageSettings(it); if (reload) it.reload() } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun webView(t: Tab, ctx: Context): WebView {
        t.view?.let { v ->
            (v.context as? MutableContextWrapper)?.baseContext = ctx
            return v
        }
        val v = WebView(MutableContextWrapper(ctx))
        // Before anything else touches the WebView (a profile can't change later).
        if (t.private && privateSupported) runCatching { privateProfile(); androidx.webkit.WebViewCompat.setProfile(v, PRIVATE_PROFILE) }
        v.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = userAgent(v.settings.userAgentString)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) && Build.VERSION.SDK_INT >= 33) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(v.settings, true)
        }
        applyPageSettings(v)
        cookieManager(t).setAcceptThirdPartyCookies(v, true)
        // A page you are not looking at gives way first when memory runs low.
        if (Build.VERSION.SDK_INT >= 26) v.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, true)
        v.addJavascriptInterface(PageBridge(t), "KuBridge")
        v.webViewClient = KuWebClient(t)
        v.webChromeClient = KuChromeClient(t)
        v.setDownloadListener(KuDownloads(t))
        // Scrolling down shrinks the address bar, up (or the top) brings it back.
        // Only a change of direction touches state (no work per scroll step).
        // The page view resizes when the bar does, which nudges the scroll
        // position; events right after a change are ignored so the bar can't
        // bounce (e.g. at the bottom of a page).
        var lastToggle = 0L
        v.setOnScrollChangeListener { _, _, y, _, oldY ->
            val c = BrowserSignals.compact
            val want = when {
                y <= 0 -> false
                y - oldY > 12 -> true
                y - oldY < -12 -> false
                else -> c
            }
            if (want != c) {
                val now = android.os.SystemClock.uptimeMillis()
                if (now - lastToggle > 400 || y <= 0) {
                    lastToggle = now
                    BrowserSignals.compact = want
                }
            }
        }
        t.view = v
        val restored = t.saved?.let { v.restoreState(it) } != null
        t.saved = null
        if (!restored && t.url.isNotBlank()) v.loadUrl(t.url)
        return v
    }

    /**
     * Each WebView holds tens of MB. Keep the current tab and the [keep] most
     * recently used ones alive; the others remember their history and load
     * again when shown.
     */
    fun trim(keep: Int = 2) {
        current?.lastUsed = System.currentTimeMillis()
        tabs.filter { it != current && it.view != null }
            .sortedByDescending { it.lastUsed }
            .drop(keep)
            .forEach { t ->
                val v = t.view ?: return@forEach
                t.saved = android.os.Bundle().also { b -> runCatching { v.saveState(b) } }
                (v.parent as? android.view.ViewGroup)?.removeView(v)
                v.stopLoading()
                v.destroy()
                t.view = null
            }
    }

    private var baseUa: String? = null

    fun userAgent(default: String): String {
        if (baseUa == null) baseUa = default
        val ua = baseUa ?: default
        return if (Prefs.desktopMode.value) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        } else ua
    }

    fun applyDesktopMode() {
        tabs.forEach { t ->
            t.view?.let {
                it.settings.userAgentString = userAgent(it.settings.userAgentString)
                it.reload()
            }
        }
    }

    fun addHistory(url: String, title: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        history.removeAll { it.url == url }
        history.add(0, Bookmark(url, title))
        while (history.size > 500) history.removeAt(history.lastIndex)
        saveHistorySoon()
    }

    private var historyJob: kotlinx.coroutines.Job? = null

    /** History is written once browsing pauses for a moment, off the main thread. */
    private fun saveHistorySoon() {
        val snapshot = history.toList()
        historyJob?.cancel()
        historyJob = Ku.scope.launch {
            kotlinx.coroutines.delay(2000)
            Prefs.history.value = Ku.json.encodeToString(snapshot)
        }
    }

    fun toggleBookmark(url: String, title: String) {
        if (bookmarks.any { it.url == url }) bookmarks.removeAll { it.url == url } else bookmarks.add(0, Bookmark(url, title.ifBlank { url }))
        Prefs.bookmarks.value = Ku.json.encodeToString(bookmarks.toList())
    }

    fun clearHistory() {
        history.clear()
        Prefs.history.value = "[]"
    }

    fun clearData() {
        clearHistory()
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        tabs.forEach { it.view?.clearCache(true) }
    }

    /** The page's cookies for [url], in the engine's shape (sent with downloads). */
    fun cookies(url: String): List<BrowserCookie> {
        val raw = cookieManager(current).getCookie(url) ?: return emptyList()
        val host = runCatching { URI(url).host }.getOrNull() ?: return emptyList()
        val secure = url.startsWith("https:")
        return raw.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@mapNotNull null
            BrowserCookie(name = part.substring(0, i).trim(), value = part.substring(i + 1).trim(), domain = host, path = "/", secure = secure, hostOnly = true)
        }
    }

    /** Typed text → address: a URL as is, anything else searched. */
    fun resolve(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return s
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(s) || s.startsWith("about:")) return s
        if (!s.contains(' ') && (s.contains('.') || s.startsWith("localhost")) && !s.endsWith('.')) return "https://$s"
        val engine = digital.kuduy.kudownloader.ui.screens.SEARCH_ENGINES.firstOrNull { it.key == Prefs.searchEngine.value }
            ?: digital.kuduy.kudownloader.ui.screens.SEARCH_ENGINES.first()
        return engine.url.replace("%s", java.net.URLEncoder.encode(s, "UTF-8"))
    }
}

/**
 * Video pages by site: the address is split into host and path first, then a
 * short anchored pattern checks the path. (The old single pattern had an
 * unescaped `.` inside a repeated group; on long addresses it backtracked for
 * seconds, and it ran on the main thread on every redraw — the browser froze.)
 */
private val VIDEO_PATHS: List<Pair<String, Regex>> = listOf(
    "youtube.com" to Regex("^/(?:watch|shorts/|live/|embed/)"),
    "youtu.be" to Regex("^/[^/]"),
    "tiktok.com" to Regex("^/@[^/]+/video/"),
    "instagram.com" to Regex("^/(?:reel|reels|p|tv)/"),
    "facebook.com" to Regex("/videos/|/reel/|/watch"),
    "fb.watch" to Regex("^/[^/]"),
    "x.com" to Regex("^/[^/]+/status/"),
    "twitter.com" to Regex("^/[^/]+/status/"),
    "vimeo.com" to Regex("^/\\d"),
    "dailymotion.com" to Regex("^/video/"),
    "twitch.tv" to Regex("^/(?:videos/|[^/]+/clip/)"),
    "reddit.com" to Regex("^/r/[^/]+/comments/"),
    "soundcloud.com" to Regex("^/[^/]+/[^/]+"),
    "bilibili.com" to Regex("^/video/"),
)

@Volatile private var lastVideoCheck: Pair<String, Boolean> = "" to false

/** Pages yt-dlp knows as a single video or track (cached for the last address). */
fun isVideoPage(url: String): Boolean {
    lastVideoCheck.let { (u, r) -> if (u == url) return r }
    val result = runCatching {
        if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return@runCatching false
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return@runCatching false
        val path = uri.rawPath ?: ""
        if ((host.startsWith("pinterest.") || host.contains(".pinterest.")) && path.startsWith("/pin/")) return@runCatching true
        VIDEO_PATHS.any { (domain, pattern) -> (host == domain || host.endsWith(".$domain")) && pattern.containsMatchIn(path) }
    }.getOrDefault(false)
    lastVideoCheck = url to result
    return result
}
