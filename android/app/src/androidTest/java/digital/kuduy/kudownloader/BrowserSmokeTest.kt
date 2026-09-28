package digital.kuduy.kudownloader

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import digital.kuduy.kudownloader.browser.BrowserState
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.Prefs
import digital.kuduy.kudownloader.ui.MediaPrefill
import digital.kuduy.kudownloader.ui.Screen
import digital.kuduy.kudownloader.ui.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real browser on a device or emulator the way people use it:
 * YouTube search, a video page (background format check), the quality sheet,
 * several tabs, the keyboard. The app must still be running after each step;
 * a crash fails the test and the log (logcat) shows why.
 */
@RunWith(AndroidJUnit4::class)
class BrowserSmokeTest {
    private val inst = InstrumentationRegistry.getInstrumentation()

    private fun main(block: () -> Unit) = inst.runOnMainSync(block)

    private fun waitFor(seconds: Int, what: String, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            var ok = false
            main { ok = cond() }
            if (ok) return
            Thread.sleep(500)
        }
        throw AssertionError("Timed out waiting for $what")
    }

    private fun alive(scenario: ActivityScenario<MainActivity>, step: String) {
        assertEquals("app closed after: $step", Lifecycle.State.RESUMED, scenario.state)
        println("KU-SMOKE ok: $step")
        println("KU-LAG $step: main thread max ${lagMax.getAndSet(0)} ms, stalls over 200 ms: ${lagStalls.getAndSet(0)}")
    }

    // ── App freezes: how late a tick posted to the main thread every 50 ms runs.
    private val lagMax = java.util.concurrent.atomic.AtomicLong(0)
    private val lagStalls = java.util.concurrent.atomic.AtomicInteger(0)

    private fun watchMainThread(): Thread = Thread {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        while (!Thread.currentThread().isInterrupted) {
            val posted = android.os.SystemClock.uptimeMillis()
            val done = java.util.concurrent.CountDownLatch(1)
            handler.post {
                val late = android.os.SystemClock.uptimeMillis() - posted
                lagMax.accumulateAndGet(late) { a, b -> maxOf(a, b) }
                if (late > 200) lagStalls.incrementAndGet()
                done.countDown()
            }
            try {
                // Stuck for more than 250 ms: record what the main thread is
                // doing, every 250 ms while it stays stuck (a sampling profile).
                var samples = 0
                while (!done.await(250, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    if (samples++ < 12) {
                        val stack = android.os.Looper.getMainLooper().thread.stackTrace
                        val top = stack.take(18).joinToString(" <- ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
                        println("KU-STALL +${android.os.SystemClock.uptimeMillis() - posted}ms $top")
                    }
                }
                Thread.sleep(50)
            } catch (_: InterruptedException) { break }
        }
    }.apply { isDaemon = true; start() }

    // ── Page freezes: the page's own long tasks (JavaScript/layout over 50 ms).
    private val longTaskProbe = """
        (function(){ if (window.__kuLT) return; window.__kuLT = {n:0, total:0, max:0};
          try { new PerformanceObserver(function(l){ l.getEntries().forEach(function(e){ var t=window.__kuLT; t.n++; t.total+=e.duration; if(e.duration>t.max) t.max=e.duration; }); }).observe({type:'longtask', buffered:true}); } catch(e) {} })();
    """.trimIndent()

    private fun pageLongTasks(step: String) {
        val latch = java.util.concurrent.CountDownLatch(1)
        var result = "?"
        main { BrowserState.current?.view?.evaluateJavascript("JSON.stringify(window.__kuLT||null)") { r -> result = r; latch.countDown() } ?: latch.countDown() }
        latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
        println("KU-PAGE $step: long tasks $result")
    }

    /** Load a page, let it settle, scroll through it like a reader, report. */
    private fun browse(scenario: ActivityScenario<MainActivity>, url: String) {
        open(url)
        waitFor(60, "$url to load") { (BrowserState.current?.progress ?: 0) >= 100 && BrowserState.current?.url?.isNotBlank() == true }
        js(longTaskProbe)
        Thread.sleep(3000)
        repeat(12) { i ->
            js("window.scrollBy({top: ${if (i % 4 == 3) -700 else 600}, behavior: 'smooth'})")
            Thread.sleep(400)
        }
        Thread.sleep(1500)
        pageLongTasks(url)
        alive(scenario, "browse $url")
    }

    private fun open(url: String) = main {
        UiState.go(Screen.Browser)
        UiState.browserUrl = url
    }

    private fun js(code: String) = main { BrowserState.current?.view?.evaluateJavascript(code, null) }

    private fun shell(cmd: String): String {
        val pfd = inst.uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    /** Memory of this (the app's) process every second, to see what grows. */
    private fun sampleMemory(): Thread = Thread {
        while (!Thread.currentThread().isInterrupted) {
            val rss = runCatching { java.io.File("/proc/self/status").readLines().firstOrNull { it.startsWith("VmRSS") }?.substringAfter(":")?.trim() }.getOrNull()
            val rt = Runtime.getRuntime()
            println("KU-MEM rss=$rss java=${(rt.totalMemory() - rt.freeMemory()) / 1_048_576}MB native=${android.os.Debug.getNativeHeapAllocatedSize() / 1_048_576}MB threads=${Thread.activeCount()}")
            try { Thread.sleep(1000) } catch (_: InterruptedException) { break }
        }
    }.apply { isDaemon = true; start() }

    @Test
    fun youtubeSearchVideoAndTabs() {
        val sampler = sampleMemory()
        val watchdog = watchMainThread()
        val ctx = inst.targetContext
        Prefs.init(ctx)
        Prefs.welcomed.value = true
        Prefs.seenVersion.value = BuildConfig.VERSION_NAME.substringBefore('-')

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(180, "KuCore to start") { Ku.phase.value == Ku.Phase.Ready }
            alive(scenario, "start")

            // 0. Ordinary heavy sites (news, articles): lag is not only YouTube.
            for (site in listOf("https://www.bbc.com/news", "https://edition.cnn.com/", "https://www.theverge.com/", "https://en.wikipedia.org/wiki/Android_(operating_system)")) {
                browse(scenario, site)
            }

            // 1. YouTube search results.
            open("https://m.youtube.com/results?search_query=lofi+hip+hop")
            waitFor(60, "YouTube to load") { BrowserState.current?.url?.contains("youtube") == true && (BrowserState.current?.progress ?: 0) >= 100 }
            Thread.sleep(8000)
            alive(scenario, "youtube search results")

            // 2. Type in YouTube's own search box (keyboard, suggestions).
            js(
                """
                (function(){
                  var b=document.querySelector('button[aria-label*="Search"],ytm-topbar-menu-button-renderer button,.topbar-menu-button-avatar-button');
                  if(b) b.click();
                  setTimeout(function(){
                    var i=document.querySelector('input[type="search"],input.searchbox-input,input[name="search"]');
                    if(!i) return;
                    i.focus();
                    var q='music video'; var n=0;
                    var t=setInterval(function(){ i.value=q.slice(0,++n); i.dispatchEvent(new Event('input',{bubbles:true})); if(n>=q.length){ clearInterval(t); i.form&&i.form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})); } }, 150);
                  }, 800);
                })();
                """.trimIndent(),
            )
            Thread.sleep(12000)
            alive(scenario, "typing in youtube search")

            // 2b. Scroll the results like a person would, and report how smooth
            // it was (Android's own frame statistics for this app).
            shell("dumpsys gfxinfo ${ctx.packageName} reset")
            repeat(24) { i ->
                js("window.scrollBy({top: ${if (i % 6 == 5) -900 else 450}, behavior: 'smooth'})")
                Thread.sleep(350)
            }
            Thread.sleep(1500)
            shell("dumpsys gfxinfo ${ctx.packageName}").lines()
                .filter { it.contains("Total frames rendered") || it.contains("Janky frames") || it.contains("50th percentile") || it.contains("90th percentile") || it.contains("99th percentile") }
                .forEach { println("KU-FRAMES ${it.trim()}") }
            alive(scenario, "scrolling youtube")

            // 3. A video page: the background format check starts after 2 s.
            open("https://m.youtube.com/watch?v=jNQXAC9IVRw")
            waitFor(60, "the video page") { BrowserState.current?.url?.contains("watch") == true }
            Thread.sleep(20000)
            alive(scenario, "youtube video page")

            // 4. The quality sheet over the page.
            main {
                val t = BrowserState.current!!
                UiState.quality = MediaPrefill(t.url, BrowserState.cookies(t.url), t.url, t.title)
            }
            Thread.sleep(25000)
            alive(scenario, "quality sheet")
            main { UiState.quality = null }

            // 5. Several tabs (older ones give up their WebView), then back.
            val sites = listOf("https://m.youtube.com/", "https://www.wikipedia.org/", "https://m.facebook.com/", "https://www.reddit.com/", "https://x.com/")
            for (s in sites) {
                main { BrowserState.newTab() }
                open(s)
                Thread.sleep(6000)
                alive(scenario, "tab $s")
            }
            main { BrowserState.current = BrowserState.tabs.first() }
            Thread.sleep(8000)
            alive(scenario, "back to the first tab")

            // 6. Leave the browser and come back (pages pause and resume).
            main { UiState.go(Screen.Downloads) }
            Thread.sleep(2000)
            main { UiState.go(Screen.Browser) }
            Thread.sleep(5000)
            alive(scenario, "switch screens")

            var tabs = 0
            main { tabs = BrowserState.tabs.size }
            assertTrue(tabs >= 5)
        }
        sampler.interrupt()
        watchdog.interrupt()
    }
}
