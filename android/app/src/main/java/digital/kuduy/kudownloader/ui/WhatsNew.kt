package digital.kuduy.kudownloader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import digital.kuduy.kudownloader.BuildConfig
import digital.kuduy.kudownloader.core.Prefs
import digital.kuduy.kudownloader.i18n.t

/** What changed in each version (newest first), shown once after an update. */
private val NOTES = listOf(
    "0.2.13" to listOf(
        "A lighter, smoother browser: less work while you scroll, and ad blocking that no longer slows pages down over time.",
        "Video qualities are read when you tap Download, not in the background on every video page (easier on the battery).",
        "The download button pulses a few times, then rests.",
    ),
    "0.2.12" to listOf(
        "The browser no longer closes the app when a heavy page (like YouTube) runs out of memory: the page reloads instead.",
        "A smaller download button: a pulsing circle in the corner.",
        "Tidier address bar, and choices open as a sheet instead of a dropdown at the edge.",
        "If the app ever closes unexpectedly, you can share a report so it can be fixed.",
    ),
    "0.2.11" to listOf(
        "Choose the video quality right in the browser; it also shows up much faster.",
        "A new page loading bar, and the address can be edited on web pages again.",
        "Cyberpunk theme: neon KuAirSend animals, a radar scanner and a glitching loading bar.",
    ),
    "0.2.10" to listOf(
        "Fixed screen titles sitting under the status bar on some phones.",
        "Buttons no longer squeeze on small screens.",
        "This window: see what's new after every update.",
    ),
    "0.2.9" to listOf(
        "A new browser: favourites, a privacy report, tab cards and a page menu.",
        "The keyboard no longer covers fields on web pages.",
        "The browser uses less memory with many tabs open.",
    ),
)

private fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

private fun newer(a: String, b: String): Boolean {
    val pa = parts(a)
    val pb = parts(b)
    for (i in 0 until maxOf(pa.size, pb.size)) {
        val x = pa.getOrElse(i) { 0 }
        val y = pb.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

/**
 * The notes to show now (and remember this version), or null. A fresh install
 * shows nothing: the welcome dialog explains the app instead.
 */
fun pendingWhatsNew(): List<Pair<String, List<String>>>? {
    val current = BuildConfig.VERSION_NAME.substringBefore('-')
    val seen = Prefs.seenVersion.value
    if (seen == current) return null
    val upgraded = Prefs.welcomed.value
    Prefs.seenVersion.value = current
    if (seen.isEmpty() && !upgraded) return null
    val list = NOTES.filter { (v, _) -> !newer(v, current) && if (seen.isEmpty()) v == current else newer(v, seen) }
    return list.ifEmpty { null }
}

@Composable
fun WhatsNewDialog(notes: List<Pair<String, List<String>>>, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDone,
        icon = { Icon(Icons.Filled.AutoAwesome, null, tint = scheme.primary) },
        title = { Text(t("What's new"), fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                notes.forEach { (version, items) ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${t("Version")} $version", style = MaterialTheme.typography.labelLarge, color = scheme.primary, fontWeight = FontWeight.SemiBold)
                        items.forEach { item ->
                            Row {
                                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(scheme.primary))
                                Spacer(Modifier.width(10.dp))
                                Text(item, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onDone) { Text(t("Got it"), fontWeight = FontWeight.SemiBold) } },
    )
}
