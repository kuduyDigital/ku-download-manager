package digital.kuduy.kudownloader.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import digital.kuduy.kudownloader.core.Fmt
import digital.kuduy.kudownloader.core.Ku
import digital.kuduy.kudownloader.core.MediaInfo
import digital.kuduy.kudownloader.i18n.t
import digital.kuduy.kudownloader.i18n.tf
import digital.kuduy.kudownloader.service.KuService
import digital.kuduy.kudownloader.ui.MediaPrefill
import digital.kuduy.kudownloader.ui.Notice
import digital.kuduy.kudownloader.ui.Screen
import digital.kuduy.kudownloader.ui.UiState
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.put

/**
 * Pick a quality right where the video is (the browser), without leaving the
 * page. Uses the default container, audio format and subtitle settings; "More
 * options" opens the full Video downloader for the rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySheet(req: MediaPrefill, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    val tools by Ku.toolsState.collectAsStateWithLifecycle()
    var info by remember(req.url) { mutableStateOf(Ku.cachedMedia(req.url)) }
    var error by remember(req.url) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var mode by remember { mutableStateOf("video") }
    var height by remember { mutableStateOf<Int?>(null) }
    var audioFormat by remember { mutableStateOf(Ku.settingString("audioFormat", "m4a")) }
    var busy by remember { mutableStateOf(false) }

    fun defaults(i: MediaInfo) {
        val preferred = Ku.settingLong("videoHeight", 1080).toInt()
        height = i.video.firstOrNull { it.height <= preferred }?.height ?: i.video.lastOrNull()?.height
        if (i.video.isEmpty() && i.audio.isNotEmpty()) mode = "audio"
    }
    LaunchedEffect(req.url, attempt) {
        info?.let { defaults(it); return@LaunchedEffect }
        error = null
        try {
            val i = Ku.analyze(req.url, false, req.cookies, req.referer)
            info = i
            defaults(i)
        } catch (e: Exception) {
            error = digital.kuduy.kudownloader.i18n.te(e.message ?: e.toString())
        }
    }

    fun moreOptions() {
        onClose()
        UiState.media = req.copy(info = info)
        UiState.go(Screen.Video)
    }

    fun download() {
        val i = info ?: return
        scope.launch {
            busy = true
            try {
                val media = buildJsonObject {
                    put("mode", mode)
                    if (mode == "video") height?.let { put("height", it) }
                    if (mode == "audio") put("audioBitrate", Ku.settingLong("audioBitrate", 320).toInt())
                    if (mode == "video") put("container", Ku.settingString("videoContainer", "mp4")) else if (i.ffmpegAvailable) put("container", audioFormat)
                    val subs = Ku.settingBool("subtitles", false) && mode == "video"
                    put("subtitles", subs)
                    put("subLangs", Ku.settingString("subLangs", "en"))
                    put("embedSubtitles", subs)
                    put("writeThumbnail", false)
                    put("embedThumbnail", Ku.settingBool("embedThumbnail", false))
                    put("playlist", false)
                }
                val size = if (mode == "video") i.video.firstOrNull { it.height == height }?.size else i.audio.firstOrNull()?.size
                val body = buildJsonObject {
                    put("url", i.webpageUrl.ifBlank { req.url })
                    put("media", media)
                    put("cookies", Ku.json.encodeToJsonElement(req.cookies))
                    req.referer?.let { put("referer", it) }
                    put("title", i.title)
                    i.description?.let { put("description", it) }
                    i.thumbnail?.let { put("thumbnail", it) }
                    size?.let { put("sizeHint", it) }
                    put("source", "browser")
                }
                Ku.addMedia(body)
                KuService.ensure(ctx)
                UiState.toast(tf("Downloading {name}", "name" to i.title))
                onClose()
            } catch (e: Exception) {
                UiState.toast(e.message ?: t("Could not start the download"))
            } finally {
                busy = false
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = scheme.surfaceContainerLow) {
        val i = info
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // What is being downloaded.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                    val thumb = i?.thumbnail
                    if (thumb != null) AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                    else Icon(Icons.Filled.Videocam, null, tint = scheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(i?.title ?: req.title?.takeIf { it.isNotBlank() } ?: req.url, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val facts = listOfNotNull(i?.uploader, i?.duration?.let { Fmt.clock(it) })
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Copy the title or caption (once it is known).
                if (i != null) CopyTitleButton(i.title, i.description)
            }

            when {
                tools?.videoReady != true -> {
                    Notice(t("Video tools are not installed yet"), scheme.tertiary)
                    Button({ moreOptions() }, Modifier.fillMaxWidth()) { Text(t("Install video tools")) }
                }
                error != null -> {
                    Notice(error ?: "")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ attempt++ }, Modifier.weight(1f)) { Text(t("Try again")) }
                        OutlinedButton({ moreOptions() }, Modifier.weight(1f)) { Text(t("More options")) }
                    }
                }
                i == null -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(2.dp)))
                        Text(t("Reading available formats…"), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
                else -> {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(mode == "video", { mode = "video" }, SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Filled.Videocam, null, Modifier.size(18.dp)) }, enabled = i.video.isNotEmpty()) { Text(t("Video")) }
                        SegmentedButton(mode == "audio", { mode = "audio" }, SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Filled.MusicNote, null, Modifier.size(18.dp)) }) { Text(t("Audio only")) }
                    }
                    if (mode == "video") {
                        LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(i.video.size) { idx ->
                                val v = i.video[idx]
                                val label = buildString {
                                    append(v.label.ifBlank { "${v.height}p" })
                                    v.fps?.takeIf { it > 30 }?.let { append(" ${it.toInt()}fps") }
                                    if (v.hdr) append(" HDR")
                                }
                                QualityRow(label, listOf(v.ext.uppercase(), v.vcodec).filter { it.isNotBlank() }.joinToString(" · "), v.size, height == v.height) { height = v.height }
                            }
                        }
                    } else {
                        val formats = audioFormats().filter { i.ffmpegAvailable || it.first == "best" || it.first == "m4a" }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            formats.forEach { (id, label) ->
                                QualityRow(label, null, if (id == formats.first().first) i.audio.firstOrNull()?.size else null, audioFormat == id) { audioFormat = id }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton({ moreOptions() }) {
                            Icon(Icons.Filled.Tune, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("More options"))
                        }
                        Spacer(Modifier.weight(1f))
                        Button({ download() }, Modifier.height(48.dp), enabled = !busy && (mode == "audio" || height != null || i.video.isEmpty()), contentPadding = PaddingValues(horizontal = 24.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.onPrimary)
                            else Icon(Icons.Filled.Download, null, Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(t("Download"), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityRow(title: String, subtitle: String?, size: Long?, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainerHigh,
        border = if (selected) BorderStroke(1.5.dp, scheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            size?.let { Text("≈ ${Fmt.size(it)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant) }
            if (selected) {
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = scheme.primary)
            } else {
                Spacer(Modifier.width(30.dp))
            }
        }
    }
}

