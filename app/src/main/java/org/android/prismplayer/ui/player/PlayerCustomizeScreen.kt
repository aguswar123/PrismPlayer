package org.android.prismplayer.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import org.android.prismplayer.data.model.Song
import org.android.prismplayer.ui.utils.LikeIcon
import org.android.prismplayer.ui.utils.LyricsIcon
import org.android.prismplayer.ui.utils.MoreIcon
import org.android.prismplayer.ui.utils.PlayerBg
import org.android.prismplayer.ui.utils.PlayerBoxes
import org.android.prismplayer.ui.utils.PlayerLayoutConfig
import org.android.prismplayer.ui.utils.PlayerStylePreferences
import org.android.prismplayer.ui.utils.ProgressType
import org.android.prismplayer.ui.utils.QueueIcon
import org.android.prismplayer.ui.utils.SkipIcon

private val PanelBg = Color(0xFF121212)
private val PanelLine = Color(0xFF2A2A2A)
private val PanelRed = Color(0xFFD71921)
private val PanelMuted = Color(0xFF8A8A8A)

private val ACCENT_CHOICES = listOf(
    0xFFD71921.toInt(), 0xFFFF7A1A.toInt(), 0xFFFFC21A.toInt(), 0xFF2ECC71.toInt(), 0xFF1ABC9C.toInt(),
    0xFF3D8BFF.toInt(), 0xFF9B59B6.toInt(), 0xFFFF5FA2.toInt(), 0xFFFFFFFF.toInt(), 0
)

/**
 * Full-screen layout editor for the Modern player.
 * Drag boxes, resize with the yellow dot, lock with the tag, and use the panel for everything else.
 * Everything is saved to the app's "prism_config" preferences as you change it.
 */
@Composable
fun PlayerCustomizeScreen(
    onClose: () -> Unit,
    song: Song? = null,
    bitmap: Bitmap? = null,
    glowColor: Color = Color(0xFF2A6F78)
) {
    val context = LocalContext.current
    val prefs = remember { PlayerStylePreferences.get(context) }
    var cfg by remember { mutableStateOf(prefs.config.value) }
    var panelOpen by remember { mutableStateOf(false) }
    var editMode by remember { mutableStateOf(true) }
    var previewPlaying by remember { mutableStateOf(true) }
    var previewProgress by remember { mutableFloatStateOf(0.32f) }

    BackHandler(onBack = onClose)

    val previewSong = remember(song) {
        song ?: Song(
            id = 1L,
            title = "Preview Song",
            artist = "Artist Name",
            albumName = "Album Name",
            albumId = 1L,
            duration = 214000,
            path = "preview.flac",
            folderName = "",
            dateAdded = 0L,
            songArtUri = "dummy",
            year = 2023,
            trackNumber = 1,
            dateModified = 0L,
            genre = ""
        )
    }

    fun change(block: PlayerLayoutConfig.() -> PlayerLayoutConfig) {
        cfg = cfg.block()
        prefs.save(cfg)
    }

    // For sliders: update the screen live, write to storage when the finger lifts.
    fun live(block: PlayerLayoutConfig.() -> PlayerLayoutConfig) {
        cfg = cfg.block()
    }

    fun commit() = prefs.save(cfg)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        ModernPlayerContent(
            config = cfg,
            song = previewSong,
            bitmap = bitmap,
            glowColor = glowColor,
            isPlaying = previewPlaying,
            progress = previewProgress,
            currentTime = "1:08",
            totalTime = "3:34",
            repeatMode = 0,
            isShuffleEnabled = false,
            isLiked = false,
            editMode = editMode,
            onToggleLike = {},
            onPlayPause = { previewPlaying = !previewPlaying },
            onNext = {},
            onPrev = {},
            onToggleRepeat = {},
            onToggleShuffle = {},
            onClose = {},
            onSeek = { previewProgress = it },
            onSeekDrag = {},
            onOpenQueue = {},
            onOpenLyrics = {},
            onOpenOptions = {},
            onHoldPlay = {},
            onBoxesChange = { cfg = cfg.copy(boxes = it) },
            onBoxesCommit = { commit() }
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            if (panelOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .background(PanelBg)
                        .border(1.dp, PanelLine)
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    EditorPanel(
                        cfg = cfg,
                        change = ::change,
                        live = ::live,
                        commit = ::commit,
                        onResetLayout = {
                            prefs.resetLayoutOnly()
                            cfg = prefs.config.value
                        },
                        onResetAll = {
                            prefs.resetAll()
                            cfg = prefs.config.value
                        }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelBg)
                    .border(1.dp, PanelLine)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BarButton("DONE", false, Modifier.weight(1f), onClose)
                BarButton(if (panelOpen) "SETTINGS ▼" else "SETTINGS ▲", panelOpen, Modifier.weight(1.4f)) { panelOpen = !panelOpen }
                BarButton(if (editMode) "DRAG: ON" else "DRAG: OFF", editMode, Modifier.weight(1.2f)) { editMode = !editMode }
            }
        }
    }
}

@Composable
private fun BarButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) PanelRed else Color(0xFF222222))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun EditorPanel(
    cfg: PlayerLayoutConfig,
    change: (PlayerLayoutConfig.() -> PlayerLayoutConfig) -> Unit,
    live: (PlayerLayoutConfig.() -> PlayerLayoutConfig) -> Unit,
    commit: () -> Unit,
    onResetLayout: () -> Unit,
    onResetAll: () -> Unit
) {
    Section("LAYOUT // DRAG BOXES") {
        ToggleRow("SHOW_BOXES + GUIDES", cfg.showBoxes) { v -> change { copy(showBoxes = v) } }
        ToggleRow("AUTO_ADJUST (SNAP)", cfg.autoSnap) { v -> change { copy(autoSnap = v) } }
        ToggleRow("GRID 8dp", cfg.grid) { v -> change { copy(grid = v) } }
        SliderRow("SNAP_DISTANCE", cfg.snapDist, 2, 20, { v -> live { copy(snapDist = v) } }, commit)
        Hint("Drag a box to move it. Drag the yellow dot to resize. Tap the tag to lock it. Pink lines show where it will snap when you let go.")
    }

    Section("LOCK_BOXES") {
        PlayerBoxes.ALL.forEach { id ->
            ToggleRow(PlayerBoxes.LABELS.getValue(id), cfg.box(id).locked) { v ->
                change { copy(boxes = boxes + (id to box(id).copy(locked = v))) }
            }
        }
    }

    Section("ICONS") {
        SliderRow("ALL_ICON_SIZE", cfg.iconSize, 10, 30, { v -> live { copy(iconSize = v) } }, commit)
        ChoiceRow("ICON_COLOR", listOf("WHITE", "ACCENT"), if (cfg.iconAccent) 1 else 0) { i -> change { copy(iconAccent = i == 1) } }
        ChoiceRow("LIKE", listOf("HEART", "STAR", "MARK"), cfg.likeIcon.ordinal) { i -> change { copy(likeIcon = LikeIcon.values()[i]) } }
        ChoiceRow("LYRICS", listOf("LINES", "BUBBLE"), cfg.lyricsIcon.ordinal) { i -> change { copy(lyricsIcon = LyricsIcon.values()[i]) } }
        ChoiceRow("QUEUE", listOf("LIST", "NOTE"), cfg.queueIcon.ordinal) { i -> change { copy(queueIcon = QueueIcon.values()[i]) } }
        ChoiceRow("MORE", listOf("• • •", "⋮"), cfg.moreIcon.ordinal) { i -> change { copy(moreIcon = MoreIcon.values()[i]) } }
        ChoiceRow("SKIP", listOf("SKIP", "RW/FF"), cfg.skipIcon.ordinal) { i -> change { copy(skipIcon = SkipIcon.values()[i]) } }
    }

    Section("CORNER_RADIUS") {
        SliderRow("ARTWORK", cfg.artRadius, 0, 40, { v -> live { copy(artRadius = v) } }, commit)
        SliderRow("PLAY_BTN_%", cfg.playRadius, 0, 50, { v -> live { copy(playRadius = v) } }, commit)
        SliderRow("FORMAT_BADGE", cfg.badgeRadius, 0, 12, { v -> live { copy(badgeRadius = v) } }, commit)
        SliderRow("PROGRESS", cfg.progRadius, 0, 8, { v -> live { copy(progRadius = v) } }, commit)
    }

    Section("BACKGROUND") {
        ChoiceRow("STYLE", listOf("ART", "DARK", "ACCENT"), cfg.bg.ordinal) { i -> change { copy(bg = PlayerBg.values()[i]) } }
        Text("ACCENT_COLOR (LAST = ALBUM COLOR)", color = PanelMuted, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            ACCENT_CHOICES.forEach { c ->
                val color = if (c == 0) Color(0xFF2A6F78) else Color(c)
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (cfg.accent == c) 2.dp else 1.dp,
                            color = if (cfg.accent == c) Color.White else PanelLine,
                            shape = CircleShape
                        )
                        .clickable { change { copy(accent = c) } },
                    contentAlignment = Alignment.Center
                ) {
                    if (c == 0) Text("A", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Section("ARTWORK") {
        SliderRow("SIZE_IN_BOX_%", cfg.artSize, 60, 100, { v -> live { copy(artSize = v) } }, commit)
        ToggleRow("SHADOW", cfg.artShadow) { v -> change { copy(artShadow = v) } }
    }

    Section("PROGRESS_BAR") {
        ChoiceRow("TYPE", listOf("WAVE", "LINE", "PILL"), cfg.progType.ordinal) { i -> change { copy(progType = ProgressType.values()[i]) } }
        SliderRow("WAVE_BARS", cfg.bars, 24, 90, { v -> live { copy(bars = v) } }, commit)
        ChoiceRow("COLOR", listOf("WHITE", "ACCENT"), if (cfg.progAccent) 1 else 0) { i -> change { copy(progAccent = i == 1) } }
        Hint("Waveform is a pattern made from the song id for now, not the real audio shape.")
    }

    Section("PLAY_BUTTON (resize its box for size)") {
        ChoiceRow("COLOR", listOf("WHITE", "ACCENT"), if (cfg.playAccent) 1 else 0) { i -> change { copy(playAccent = i == 1) } }
    }

    Section("TEXT") {
        SliderRow("TITLE_SIZE", cfg.titleSize, 16, 30, { v -> live { copy(titleSize = v) } }, commit)
        ChoiceRow("TITLE_ALIGN", listOf("LEFT", "CENTER"), if (cfg.titleCenter) 1 else 0) { i -> change { copy(titleCenter = i == 1) } }
        ChoiceRow("FONT", listOf("SANS", "MONO"), if (cfg.mono) 1 else 0) { i -> change { copy(mono = i == 1) } }
    }

    Section("SHOW / HIDE") {
        ToggleRow("LIKE_BUTTON", cfg.showLike) { v -> change { copy(showLike = v) } }
        ToggleRow("TIME_LABELS", cfg.showTimes) { v -> change { copy(showTimes = v) } }
        ToggleRow("PLAYING_FROM", cfg.showSource) { v -> change { copy(showSource = v) } }
        ToggleRow("FORMAT_BADGE", cfg.showBadge) { v -> change { copy(showBadge = v) } }
        ToggleRow("LYRICS_BUTTON", cfg.showLyrics) { v -> change { copy(showLyrics = v) } }
        ToggleRow("QUEUE_BUTTON", cfg.showQueue) { v -> change { copy(showQueue = v) } }
        ToggleRow("MORE_BUTTON", cfg.showMore) { v -> change { copy(showMore = v) } }
    }

    Section("RESET") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BarButton("RESET_POSITIONS", false, Modifier.weight(1f), onResetLayout)
            BarButton("RESET_ALL", false, Modifier.weight(1f), onResetAll)
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, PanelLine, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text(title, color = PanelMuted, fontSize = 10.sp, letterSpacing = 1.5.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = PanelMuted, fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = PanelRed, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    onFinished: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(120.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            onValueChangeFinished = onFinished,
            valueRange = min.toFloat()..max.toFloat(),
            colors = SliderDefaults.colors(thumbColor = PanelRed, activeTrackColor = PanelRed),
            modifier = Modifier.weight(1f)
        )
        Text(
            value.toString(),
            color = PanelMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(28.dp)
        )
    }
}

@Composable
private fun ChoiceRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, PanelLine, RoundedCornerShape(8.dp))
        ) {
            options.forEachIndexed { i, text ->
                Box(
                    modifier = Modifier
                        .background(if (i == selected) PanelRed else Color.Transparent)
                        .clickable { onSelect(i) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                ) {
                    Text(
                        text,
                        color = if (i == selected) Color.White else PanelMuted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
