package digital.kuduy.kudownloader.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.Prefs
import digital.kuduy.kudownloader.i18n.I18n
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** The desktop's accent colours (Settings › Appearance), same names. */
val ACCENTS = listOf(
    "blue" to Color(0xFF2563EB),
    "violet" to Color(0xFF7C3AED),
    "teal" to Color(0xFF0D9488),
    "green" to Color(0xFF16A34A),
    "orange" to Color(0xFFEA580C),
    "pink" to Color(0xFFDB2777),
    "red" to Color(0xFFDC2626),
    "graphite" to Color(0xFF52525B),
)

val Danger = Color(0xFFD13438)
val UploadColor = Color(0xFF16A34A)

/** `cyber`: the dark Cyberpunk palette is on (neon animals, HUD radar, glitching bars). */
data class KuColors(val success: Color, val warning: Color, val danger: Color, val upload: Color, val dark: Boolean, val cyber: Boolean = false)

val LocalKuColors = staticCompositionLocalOf { KuColors(Color(0xFF16A34A), Color(0xFFD97706), Danger, UploadColor, false) }

/** [c] moved [amount] of the way towards [towards]. */
fun tone(c: Color, towards: Color, amount: Float) = lerp(c, towards, amount)

/** Light palettes (same names as the desktop's): background → deepest container. */
private fun lightTones(palette: String): List<Color> = when (palette) {
    "paper" -> listOf(Color(0xFFF6F2EA), Color(0xFFFFFDF9), Color(0xFFF1ECE2), Color(0xFFECE6DA), Color(0xFFE6DFD2), Color(0xFFE0D8CA))
    "mist" -> listOf(Color(0xFFF1F4F9), Color.White, Color(0xFFEBEFF6), Color(0xFFE6EBF3), Color(0xFFDFE5EF), Color(0xFFD8DFEB))
    "mint" -> listOf(Color(0xFFF0F7F2), Color.White, Color(0xFFE8F2EB), Color(0xFFE2EEE6), Color(0xFFDBE9E0), Color(0xFFD3E3D9))
    "rose" -> listOf(Color(0xFFFAF1F3), Color.White, Color(0xFFF5E9EC), Color(0xFFF1E3E7), Color(0xFFECDCE1), Color(0xFFE6D4DA))
    "sakura" -> listOf(Color(0xFFFFF4F7), Color.White, Color(0xFFFDEBF0), Color(0xFFFBE3EA), Color(0xFFF7D9E2), Color(0xFFF2CFDA))
    "sora" -> listOf(Color(0xFFEEF6FD), Color.White, Color(0xFFE5F1FC), Color(0xFFDDECFA), Color(0xFFD3E6F8), Color(0xFFC9E0F5))
    "lavender" -> listOf(Color(0xFFF4F2FB), Color.White, Color(0xFFEDEAF7), Color(0xFFE8E4F4), Color(0xFFE1DCF0), Color(0xFFD9D3EB))
    else -> listOf(Color(0xFFF7F8FC), Color.White, Color(0xFFF2F4F9), Color(0xFFEEF0F6), Color(0xFFE8EBF2), Color(0xFFE2E5ED))
}

/** Anime light palettes bring their own accent, like Cyberpunk does. */
private val ANIME_ACCENTS = mapOf("sakura" to Color(0xFFE0578A), "sora" to Color(0xFF2E98E8))

private fun lightScheme(accent0: Color, palette: String): ColorScheme {
    val accent = ANIME_ACCENTS[palette] ?: accent0
    val (bg, lowest, low, mid, high) = lightTones(palette)
    val highest = lightTones(palette)[5]
    return lightColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = tone(accent, Color.White, 0.84f),
        onPrimaryContainer = tone(accent, Color.Black, 0.55f),
        secondary = tone(accent, Color(0xFF64748B), 0.55f),
        onSecondary = Color.White,
        secondaryContainer = tone(accent, Color.White, 0.9f),
        onSecondaryContainer = tone(accent, Color.Black, 0.6f),
        tertiary = Color(0xFF16A34A),
        background = bg,
        onBackground = Color(0xFF15171C),
        surface = bg,
        onSurface = Color(0xFF15171C),
        surfaceVariant = high,
        onSurfaceVariant = Color(0xFF5B6170),
        surfaceContainerLowest = lowest,
        surfaceContainerLow = low,
        surfaceContainer = mid,
        surfaceContainerHigh = high,
        surfaceContainerHighest = highest,
        outline = Color(0xFFC3C8D4),
        outlineVariant = Color(0xFFDDE1EA),
        error = Danger,
    )
}

/**
 * Cyberpunk glitch for progress bars and the network graph: one shared state,
 * nudged by a single loop in [KuTheme] (not one animation per list row).
 * Read it while drawing, so a glitch only redraws, never re-lays out.
 */
object CyberGlitch {
    /** Sideways jump, in dp (0 = steady). */
    var shift by androidx.compose.runtime.mutableFloatStateOf(0f)
    /** Colour-split distance, in dp (0 = none). */
    var split by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/** The app's accent as "r,g,b", for the page script's download pill. */
object PageAccent {
    @Volatile var rgb: String = "37,99,235"
}

/** Cyberpunk brings its own neon accent, whatever accent is picked. */
private val CYBER_ACCENT = Color(0xFFFF2A6D)

private fun darkScheme(accent0: Color, palette: String): ColorScheme {
    val accent = if (palette == "cyberpunk") CYBER_ACCENT else accent0
    val (bg, s1, s2, s3, s4) = when (palette) {
        "black" -> listOf(Color.Black, Color(0xFF0A0A0B), Color(0xFF121214), Color(0xFF1A1A1D), Color(0xFF232327))
        "midnight" -> listOf(Color(0xFF0B1020), Color(0xFF10172A), Color(0xFF151D33), Color(0xFF1B243D), Color(0xFF222C47))
        "forest" -> listOf(Color(0xFF0C1310), Color(0xFF111A16), Color(0xFF15201B), Color(0xFF1B2822), Color(0xFF22302A))
        "plum" -> listOf(Color(0xFF130E1A), Color(0xFF1A1323), Color(0xFF20182B), Color(0xFF271E34), Color(0xFF2F253E))
        "mocha" -> listOf(Color(0xFF15110D), Color(0xFF1C1713), Color(0xFF231D18), Color(0xFF2B241E), Color(0xFF342C25))
        "nord" -> listOf(Color(0xFF1E222A), Color(0xFF242933), Color(0xFF2B303B), Color(0xFF323846), Color(0xFF3B4252))
        "ocean" -> listOf(Color(0xFF081416), Color(0xFF0D1B1E), Color(0xFF112226), Color(0xFF16292D), Color(0xFF1C3136))
        "cyberpunk" -> listOf(Color(0xFF0B0717), Color(0xFF0F0A1F), Color(0xFF140D29), Color(0xFF1B1236), Color(0xFF231842))
        else -> listOf(Color(0xFF111318), Color(0xFF16181E), Color(0xFF1B1E25), Color(0xFF22252D), Color(0xFF2A2D36))
    }
    val light = tone(accent, Color.White, 0.25f)
    return darkColorScheme(
        primary = light,
        onPrimary = Color(0xFF0B1020),
        primaryContainer = tone(accent, bg, 0.62f),
        onPrimaryContainer = tone(accent, Color.White, 0.8f),
        secondary = tone(accent, Color(0xFF94A3B8), 0.6f),
        onSecondary = Color.Black,
        secondaryContainer = tone(accent, bg, 0.75f),
        onSecondaryContainer = tone(accent, Color.White, 0.82f),
        tertiary = Color(0xFF4ADE80),
        background = bg,
        onBackground = Color(0xFFE7E9EE),
        surface = bg,
        onSurface = Color(0xFFE7E9EE),
        surfaceVariant = s3,
        onSurfaceVariant = Color(0xFFA7ADBA),
        surfaceContainerLowest = bg,
        surfaceContainerLow = s1,
        surfaceContainer = s2,
        surfaceContainerHigh = s3,
        surfaceContainerHighest = s4,
        outline = Color(0xFF454A56),
        outlineVariant = Color(0xFF30343D),
        error = Color(0xFFF1707B),
    )
}

@Composable
fun KuTheme(content: @Composable () -> Unit) {
    val settings by Ku.settings.collectAsStateWithLifecycle()
    val dynamic by Prefs.dynamicColor.state.collectAsStateWithLifecycle()
    val theme = (settings["theme"] as? JsonPrimitive)?.contentOrNull ?: "system"
    val accentName = (settings["accent"] as? JsonPrimitive)?.contentOrNull ?: "blue"
    val palette = (settings["darkPalette"] as? JsonPrimitive)?.contentOrNull ?: "default"
    val lightPalette = (settings["lightPalette"] as? JsonPrimitive)?.contentOrNull ?: "default"
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accent = ACCENTS.firstOrNull { it.first == accentName }?.second ?: ACCENTS[0].second
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx).let { if (palette == "black") it.copy(background = Color.Black, surface = Color.Black) else it } else dynamicLightColorScheme(ctx)
        dark -> darkScheme(accent, palette)
        else -> lightScheme(accent, lightPalette)
    }
    // The browser's in-page download pill uses the same accent.
    PageAccent.rgb = scheme.primary.let { "${(it.red * 255).toInt()},${(it.green * 255).toInt()},${(it.blue * 255).toInt()}" }
    val ku = KuColors(
        success = if (dark) Color(0xFF4ADE80) else Color(0xFF16A34A),
        warning = if (dark) Color(0xFFFBBF24) else Color(0xFFD97706),
        danger = Danger,
        upload = if (dark) Color(0xFF4ADE80) else UploadColor,
        dark = dark,
        cyber = dark && palette == "cyberpunk" && !(dynamic && Build.VERSION.SDK_INT >= 31),
    )
    if (ku.cyber) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val rnd = kotlin.random.Random
            while (true) {
                kotlinx.coroutines.delay(rnd.nextLong(1800, 4200))
                repeat(rnd.nextInt(2, 5)) {
                    CyberGlitch.shift = rnd.nextFloat() * 6f - 3f
                    CyberGlitch.split = rnd.nextFloat() * 2.5f + 1f
                    kotlinx.coroutines.delay(rnd.nextLong(45, 90))
                }
                CyberGlitch.shift = 0f
                CyberGlitch.split = 0f
            }
        }
    }
    CompositionLocalProvider(
        LocalKuColors provides ku,
        LocalLayoutDirection provides if (I18n.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
    ) {
        MaterialTheme(colorScheme = scheme, typography = KuType, content = content)
    }
}

/** Headings a touch bolder and tighter than Material's defaults (a calmer, premium look). */
private val KuType: Typography = Typography().let { b ->
    fun androidx.compose.ui.text.TextStyle.tight(weight: Int, spacing: Float) =
        copy(fontWeight = androidx.compose.ui.text.font.FontWeight(weight), letterSpacing = androidx.compose.ui.unit.TextUnit(spacing, androidx.compose.ui.unit.TextUnitType.Sp))
    b.copy(
        headlineLarge = b.headlineLarge.tight(700, -0.6f),
        headlineMedium = b.headlineMedium.tight(700, -0.5f),
        headlineSmall = b.headlineSmall.tight(700, -0.3f),
        titleLarge = b.titleLarge.tight(600, -0.2f),
        titleMedium = b.titleMedium.tight(600, -0.1f),
        titleSmall = b.titleSmall.tight(600, 0f),
        labelLarge = b.labelLarge.tight(600, 0.1f),
        labelSmall = b.labelSmall.tight(600, 0.2f),
    )
}
