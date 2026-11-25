package com.example.glassplayer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

data class Track(
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val art: Bitmap?,
    val accent: Color,
)

class Player(private val ctx: Context) {
    val tracks = mutableStateListOf<Track>()
    var index by mutableIntStateOf(-1)
    var playing by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(false)
    private var mp: MediaPlayer? = null

    val current: Track? get() = tracks.getOrNull(index)

    fun add(uris: List<Uri>) {
        uris.forEach { tracks.add(load(it)) }
        if (index < 0 && tracks.isNotEmpty()) play(0)
    }

    private fun fileName(uri: Uri): String =
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (c.moveToFirst() && i >= 0) c.getString(i) else null
            }
        }.getOrNull()?.substringBeforeLast('.') ?: "Unknown track"

    private fun load(uri: Uri): Track {
        val r = MediaMetadataRetriever()
        var title: String? = null
        var artist: String? = null
        var dur = 0L
        var art: Bitmap? = null
        try {
            r.setDataSource(ctx, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            art = r.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        } catch (_: Exception) {
        } finally {
            r.release()
        }
        val name = title?.takeIf { it.isNotBlank() } ?: fileName(uri)
        val accent = art?.let { Color(Bitmap.createScaledBitmap(it, 1, 1, true).getPixel(0, 0)) }
            ?: Color.hsv((name.hashCode().mod(360)).toFloat(), 0.6f, 0.85f)
        return Track(uri, name, artist ?: "Unknown artist", dur, art, accent)
    }

    fun play(i: Int) {
        if (i !in tracks.indices) return
        mp?.release()
        index = i
        position = 0
        mp = MediaPlayer.create(ctx, tracks[i].uri)?.apply {
            setOnCompletionListener { next() }
            start()
        }
        playing = mp != null
    }

    fun toggle() {
        val m = mp ?: return
        if (m.isPlaying) { m.pause(); playing = false } else { m.start(); playing = true }
    }

    fun next() {
        if (tracks.isEmpty()) return
        val n = if (shuffle) tracks.indices.random() else index + 1
        when {
            n < tracks.size -> play(n)
            repeat -> play(0)
            else -> { mp?.pause(); mp?.seekTo(0); playing = false; position = 0 }
        }
    }

    fun prev() {
        if (tracks.isEmpty()) return
        if (position > 3000) seek(0) else play(if (index > 0) index - 1 else tracks.size - 1)
    }

    fun seek(ms: Long) { mp?.seekTo(ms.toInt()); position = ms }
    fun tick() { if (playing) position = mp?.currentPosition?.toLong() ?: 0L }
    fun release() { mp?.release(); mp = null }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val player = remember { Player(applicationContext) }
            App(player)
        }
    }
}

// ---- Liquid glass ----

fun Modifier.glass(shape: Shape): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(0.26f), Color.White.copy(0.07f))))
    .border(
        1.dp,
        Brush.linearGradient(listOf(Color.White.copy(0.75f), Color.White.copy(0.08f), Color.White.copy(0.3f))),
        shape,
    )

@Composable
fun Backdrop(accent: Color) {
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(0f, 1f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val c1 by animateColorAsState(accent, tween(900), label = "c1")
    val c2 = lerp(c1, Color(0xFF7C5CFF), 0.6f)
    val c3 = lerp(c1, Color(0xFF00D4FF), 0.5f)
    Canvas(Modifier.fillMaxSize().blur(70.dp)) {
        val m = size.minDimension
        drawCircle(c1, m * 0.6f, Offset(size.width * (0.15f + 0.6f * a), size.height * 0.22f))
        drawCircle(c2, m * 0.55f, Offset(size.width * (0.85f - 0.6f * a), size.height * 0.6f))
        drawCircle(c3, m * 0.5f, Offset(size.width * (0.3f + 0.4f * a), size.height * 0.95f))
    }
}

@Composable
fun App(p: Player) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { p.add(it) }
        LaunchedEffect(Unit) { while (true) { p.tick(); delay(250) } }
        DisposableEffect(Unit) { onDispose { p.release() } }

        Box(Modifier.fillMaxSize().background(Color(0xFF0B0B14))) {
            Backdrop(p.current?.accent ?: Color(0xFF5B6CFF))
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Library", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.glass(CircleShape).clickable { picker.launch(arrayOf("audio/*")) }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) { Text("Add songs", color = Color.White, fontWeight = FontWeight.Medium) }
                }
                NowPlaying(p)
                Playlist(p, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun NowPlaying(p: Player) {
    val t = p.current
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp)).padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(190.dp).clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(t?.accent ?: Color(0xFF5B6CFF), Color(0xFF1B1B2F)))),
            contentAlignment = Alignment.Center,
        ) {
            val art = t?.art
            if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text("♪", color = Color.White.copy(0.85f), fontSize = 72.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(t?.title ?: "Nothing playing", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t?.artist ?: "Add songs to start", color = Color.White.copy(0.7f), fontSize = 14.sp, maxLines = 1)

        val dur = (t?.durationMs ?: 0L).coerceAtLeast(1L)
        Slider(
            value = (p.position.toFloat() / dur).coerceIn(0f, 1f),
            onValueChange = { p.seek((it * dur).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(0.25f),
            ),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmt(p.position), color = Color.White.copy(0.7f), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(fmt(t?.durationMs ?: 0L), color = Color.White.copy(0.7f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Ctrl("⇄", p.shuffle) { p.shuffle = !p.shuffle }
            Ctrl("⏮", false) { p.prev() }
            Box(
                Modifier.size(68.dp).glass(CircleShape).clickable { p.toggle() },
                contentAlignment = Alignment.Center,
            ) { Text(if (p.playing) "❚❚" else "▶", color = Color.White, fontSize = 24.sp) }
            Ctrl("⏭", false) { p.next() }
            Ctrl("↻", p.repeat) { p.repeat = !p.repeat }
        }
    }
}

@Composable
fun Ctrl(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (active) Color.White else Color.White.copy(0.55f), fontSize = 22.sp) }
}

@Composable
fun Playlist(p: Player, modifier: Modifier) {
    if (p.tracks.isEmpty()) {
        Box(modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No songs yet. Tap Add songs to pick audio files from your phone.", color = Color.White.copy(0.75f))
        }
        return
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(p.tracks) { i, t ->
            val sel = i == p.index
            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))
                    .background(if (sel) Color.White.copy(0.16f) else Color.Transparent)
                    .clickable { p.play(i) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(t.accent.copy(0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    val art = t.art
                    if (art != null) Image(art.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("♪", color = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.artist, color = Color.White.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(fmt(t.durationMs), color = Color.White.copy(0.65f), fontSize = 12.sp)
            }
        }
    }
}

fun fmt(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
