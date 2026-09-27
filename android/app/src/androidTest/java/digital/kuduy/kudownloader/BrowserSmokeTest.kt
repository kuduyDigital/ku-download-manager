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
    }

    private fun open(url: String) = main {
        UiState.go(Screen.Browser)
        UiState.browserUrl = url
    }

    private fun js(code: String) = main { BrowserState.current?.view?.evaluateJavascript(code, null) }

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
        val ctx = inst.targetContext
        Prefs.init(ctx)
        Prefs.welcomed.value = true
        Prefs.seenVersion.value = BuildConfig.VERSION_NAME.substringBefore('-')

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitFor(180, "KuCore to start") { Ku.phase.value == Ku.Phase.Ready }
            alive(scenario, "start")

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
    }
}
