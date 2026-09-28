package digital.kuduy.kudownloader

import digital.kuduy.kudownloader.browser.isVideoPage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The browser asks this on every redraw: it must be right and fast. */
class VideoPageTest {
    @Test
    fun recognisesVideoPages() {
        assertTrue(isVideoPage("https://m.youtube.com/watch?v=jNQXAC9IVRw"))
        assertTrue(isVideoPage("https://www.youtube.com/shorts/abc123"))
        assertTrue(isVideoPage("https://youtu.be/jNQXAC9IVRw"))
        assertTrue(isVideoPage("https://www.tiktok.com/@someone/video/7312345678"))
        assertTrue(isVideoPage("https://www.instagram.com/reel/C1abcDEF/"))
        assertTrue(isVideoPage("https://x.com/someone/status/1790000000000000000"))
        assertTrue(isVideoPage("https://vimeo.com/76979871"))
        assertTrue(isVideoPage("https://www.reddit.com/r/videos/comments/abc/title/"))
        assertTrue(isVideoPage("https://www.pinterest.co.uk/pin/123456/"))
        assertFalse(isVideoPage("https://m.youtube.com/results?search_query=lofi+hip+hop"))
        assertFalse(isVideoPage("https://www.bbc.com/news"))
        assertFalse(isVideoPage("https://notyoutube.com/watch?v=x"))
        assertFalse(isVideoPage(""))
    }

    @Test
    fun longAddressesAreInstant() {
        // Long search and tracking addresses made the old pattern backtrack for seconds.
        val long = "https://m.youtube.com/results?search_query=" + "a".repeat(400) + "&sp=" + "x.y-z".repeat(300)
        val weird = "https://" + "a-b.".repeat(2000) + "example.org/" + "p".repeat(3000)
        val start = System.nanoTime()
        repeat(200) {
            assertFalse(isVideoPage("$long&i=$it"))
            assertFalse(isVideoPage("$weird?i=$it"))
        }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("400 checks took $ms ms", ms < 1000)
    }
}
