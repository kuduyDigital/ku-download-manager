package digital.kuduy.kudownloader

import android.app.Application
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.Prefs
import digital.kuduy.kudownloader.i18n.I18n
import digital.kuduy.kudownloader.service.KuService
import digital.kuduy.kudownloader.service.NetworkWatch
import digital.kuduy.kudownloader.service.ScheduleAlarm
import digital.kuduy.kudownloader.service.Notifier

class KuApp : Application() {
    override fun onCreate() {
        super.onCreate()
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
