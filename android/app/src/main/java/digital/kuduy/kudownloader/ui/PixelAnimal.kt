package digital.kuduy.kudownloader.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import digital.kuduy.kudownloader.i18n.t

val AVATARS = SPRITES.keys.toList()

val ANIMAL_NAMES = mapOf(
    "cat" to "Cat", "fox" to "Fox", "frog" to "Frog", "panda" to "Panda", "bunny" to "Bunny", "penguin" to "Penguin",
    "pig" to "Pig", "chick" to "Chick", "dog" to "Dog", "bear" to "Bear", "koala" to "Koala", "owl" to "Owl",
    "monkey" to "Monkey", "tiger" to "Tiger", "mouse" to "Mouse", "cow" to "Cow",
)

fun animalName(a: String) = t(ANIMAL_NAMES[a] ?: a)

/** Same pick as the desktop (FNV-1a), so a device keeps its animal everywhere. */
fun hashPick(fingerprint: String, n: Int): Int {
    var h = 2166136261L.toInt()
    for (c in fingerprint) h = (h xor c.code) * 16777619
    return ((h.toLong() and 0xFFFFFFFFL) % n).toInt()
}

fun avatarOf(avatar: String, fingerprint: String) = avatar.takeIf { it in SPRITES } ?: AVATARS[hashPick(fingerprint, AVATARS.size)]

private val CYBER_CYAN = Color(0xFF05D9E8)
private val CYBER_MAGENTA = Color(0xFFFF2A6D)
private val CYBER_YELLOW = Color(0xFFF9F002)

/** Cyberpunk theme: fur darkened and pushed towards violet (same formula as the desktop). */
private fun cyberFur(c: Color): Color = Color(
    red = ((c.red * 255 * 0.55f + 40) / 255).coerceIn(0f, 1f),
    green = ((c.green * 255 * 0.45f + 10) / 255).coerceIn(0f, 1f),
    blue = ((c.blue * 255 * 0.6f + 70) / 255).coerceIn(0f, 1f),
)

/** A round avatar with an 8-bit animal that bobs and blinks. */
@Composable
fun PixelAnimal(animal: String, size: Dp = 64.dp, seed: Int = 0, still: Boolean = false) {
    val s = SPRITES[animal] ?: SPRITES.getValue("cat")
    val dark = LocalKuColors.current.dark
    val phase = (seed % 17) * 230
    val anim = rememberInfiniteTransition(label = "px")
    val bob by anim.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1400, delayMillis = 0, easing = LinearEasing), RepeatMode.Reverse, initialStartOffset = androidx.compose.animation.core.StartOffset(phase)),
        label = "bob",
    )
    val blink by anim.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                0f at 0
                0f at 3900
                1f at 3950
                1f at 4100
                0f at 4150
            },
            initialStartOffset = androidx.compose.animation.core.StartOffset(phase * 3),
        ),
        label = "blink",
    )
    val cyber = LocalKuColors.current.cyber
    // Cyberpunk: a visor line sweeping down, and now and then a glitch (the
    // face jumps sideways and splits into magenta and cyan).
    val visor by anim.animateFloat(
        -0.05f, 1.05f,
        infiniteRepeatable(tween(2600, easing = LinearEasing), initialStartOffset = androidx.compose.animation.core.StartOffset(phase * 2)),
        label = "visor",
    )
    val glitch by anim.animateFloat(
        0f, 0f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 5000
                0f at 0
                0f at 4500
                0.6f at 4520
                -0.8f at 4600
                0.4f at 4680
                0f at 4760
            },
            initialStartOffset = androidx.compose.animation.core.StartOffset(phase * 4),
        ),
        label = "glitch",
    )
    val bg = if (cyber) Color(0xFF120A26) else if (dark) s.bgDark else s.bgLight
    Box(
        Modifier.size(size).clip(CircleShape).background(bg)
            .then(if (cyber) Modifier.border(1.dp, CYBER_CYAN.copy(alpha = 0.6f), CircleShape) else Modifier),
    ) {
        Canvas(Modifier.size(size)) {
            val rows = s.half.size
            // 12 wide, `rows` tall, inside a 15.5 × 15 box like the desktop's viewBox.
            val unit = this.size.width / 15.5f
            val left = 1.75f * unit
            val top = ((15 - rows) / 2f) * unit + if (still) 0f else (bob - 0.5f) * unit * 0.5f
            val eyesShut = !still && blink > 0.5f
            val jolt = if (cyber && !still) glitch * unit else 0f
            fun colour(ch: Char): Color {
                val raw = s.pal[if (ch == 'k' && eyesShut) s.lid else ch] ?: return Color.Magenta
                if (!cyber) return raw
                return when {
                    ch == 'o' -> CYBER_CYAN
                    ch == 'k' && !eyesShut -> CYBER_MAGENTA
                    else -> cyberFur(raw)
                }
            }
            fun face(dx: Float, tint: Color?) {
                s.half.forEachIndexed { y, row ->
                    val full = row + row.reversed()
                    full.forEachIndexed { x, ch ->
                        if (ch == '.') return@forEachIndexed
                        drawRect(tint ?: colour(ch), Offset(left + x * unit + dx, top + y * unit), Size(unit * 1.02f, unit * 1.02f))
                    }
                }
            }
            if (jolt != 0f) {
                face(jolt - unit * 0.6f, CYBER_MAGENTA.copy(alpha = 0.55f))
                face(jolt + unit * 0.6f, CYBER_CYAN.copy(alpha = 0.55f))
            }
            face(jolt, null)
            if (cyber && !still) {
                val y = this.size.height * visor
                drawRect(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, CYBER_YELLOW.copy(alpha = 0.9f), Color.Transparent)),
                    Offset(this.size.width * 0.08f, y),
                    Size(this.size.width * 0.84f, 1.5.dp.toPx()),
                )
            }
        }
    }
}

/** The device's system as a small pixel badge. */
@Composable
fun OsBadge(os: String, size: Dp = 20.dp) {
    val o = OS_SPRITES[os] ?: return
    val fg = MaterialTheme.colorScheme.onSurface
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Canvas(Modifier.size(size)) {
            val w = o.rows.maxOf { it.length }
            val h = o.rows.size
            val unit = this.size.width / 11f
            val left = (11 - w) / 2f * unit
            val top = (11 - h) / 2f * unit
            o.rows.forEachIndexed { y, row ->
                row.forEachIndexed { x, ch ->
                    if (ch == '.') return@forEachIndexed
                    val c = if (o.pal.containsKey(ch)) o.pal[ch] ?: fg else return@forEachIndexed
                    drawRect(c, Offset(left + x * unit, top + y * unit), Size(unit * 1.02f, unit * 1.02f))
                }
            }
        }
    }
}

fun osName(os: String) = OS_SPRITES[os]?.name ?: os.replaceFirstChar { it.uppercase() }
