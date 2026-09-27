package digital.kuduy.kudownloader

import android.app.Application
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.Prefs
import digital.kuduy.kudownloader.i18n.I18n
import digital.kuduy.kudownloader.service.KuService
import digital.kuduy.kudownloader.service.NetworkWatch
import digital.kuduy.kudownloader.service.ScheduleAlarm
import digital.kuduy.kudownloader.service.Notifier

/** The last crash, written by the handler below and offered on the next start. */
const val CRASH_FILE = "last-crash.txt"

class KuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Keep the reason of a crash, so the next start can offer to share it.
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val report = buildString {
                    append("KuDownloader ").append(BuildConfig.VERSION_NAME)
                    append(" · Android ").append(android.os.Build.VERSION.RELEASE)
                    append(" · ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n")
                    append("Thread: ").append(thread.name).append("\n\n")
                    append(error.stackTraceToString().take(12_000))
                }
                java.io.File(filesDir, CRASH_FILE).writeText(report)
            }
            previous?.uncaughtException(thread, error)
        }
        Prefs.init(this)
        I18n.load(this)
        Notifier.channels(this)
        Ku.start(this)
        // Keeps downloads (and KuAirSend) alive in the background and posts
        // completion notices; the service starts itself only while needed.
        KuService.watch(this)
        NetworkWatch.start(this)
        ScheduleAlarm.watch(this)
    }

    /** In the background (downloads keep running): free the browser's idle pages. */
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            android.os.Handler(mainLooper).post { runCatching { digital.kuduy.kudownloader.browser.BrowserState.trim(keep = 0) } }
        }
    }
}
