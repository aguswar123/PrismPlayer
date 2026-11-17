package org.android.prismplayer.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil.compose.AsyncImage
import org.android.prismplayer.data.model.Song
import org.android.prismplayer.ui.utils.BoxState
import org.android.prismplayer.ui.utils.LikeIcon
import org.android.prismplayer.ui.utils.LyricsIcon
import org.android.prismplayer.ui.utils.MoreIcon
import org.android.prismplayer.ui.utils.PlayerBg
import org.android.prismplayer.ui.utils.PlayerBoxes
import org.android.prismplayer.ui.utils.PlayerLayoutConfig
import org.android.prismplayer.ui.utils.ProgressType
import org.android.prismplayer.ui.utils.QueueIcon
import org.android.prismplayer.ui.utils.SkipIcon
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MARGIN_DP = 16f
private const val GRID_DP = 8f
private const val HOLD_TO_OPEN_MS = 3000L
private val EDIT_YELLOW = Color(0xFFFFD84D)
private val GUIDE_PINK = Color(0xFFFF3B6B)

/**
 * "Modern" player. Every element is its own box, positioned freely on the screen.
 * With [editMode] on, boxes can be dragged, resized and locked, with snapping guides.
 */
@Composable
fun ModernPlayerContent(
    config: PlayerLayoutConfig,
    song: Song,
    bitmap: Bitmap?,
    glowColor: Color,
    isPlaying: Boolean,
    progress: Float,
    currentTime: String,
    totalTime: String,
    repeatMode: Int,
    isShuffleEnabled: Boolean,
    isLiked: Boolean,
    editMode: Boolean,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onClose: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekDrag: (Float) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenOptions: () -> Unit,
    onHoldPlay: () -> Unit,
    modifier: Modifier = Modifier,
    onBoxesChange: (Map<String, BoxState>) -> Unit = {},
    onBoxesCommit: () -> Unit = {}
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val accent = if (config.accent == 0) glowColor else Color(config.accent)
    val iconTint = if (config.iconAccent) accent else Color.White.copy(alpha = 0.85f)
    val font = if (config.mono) FontFamily.Monospace else FontFamily.Default

    val currentConfig by rememberUpdatedState(config)
    val currentOnChange by rememberUpdatedState(onBoxesChange)
    val currentOnCommit by rememberUpdatedState(onBoxesCommit)
    var guideX by remember { mutableStateOf<Float?>(null) }
    var guideY by remember { mutableStateOf<Float?>(null) }

    val formatLabel = remember(song.path) {
        val ext = song.path.substringAfterLast('.', "").uppercase()
        if (ext.length in 2..5) ext else "AUDIO"
    }

    Box(modifier = modifier.fillMaxSize()) {
        ModernBackground(config, glowColor, accent)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            val cw = constraints.maxWidth.toFloat()
            val ch = constraints.maxHeight.toFloat()
            val marginPx = with(density) { MARGIN_DP.dp.toPx() }
            val gridPx = with(density) { GRID_DP.dp.toPx() }
            val thresholdPx = with(density) { currentConfig.snapDist.dp.toPx() }
            val minWf = with(density) { 28.dp.toPx() } / cw
            val minHf = with(density) { 20.dp.toPx() } / ch

            fun guidesFor(key: String, cfg: PlayerLayoutConfig, resize: Boolean) {
                if (!cfg.autoSnap) {
                    guideX = null
                    guideY = null
                    return
                }
                val r = computeSnap(key, cfg, cw, ch, resize, thresholdPx, gridPx, marginPx)
                guideX = r.lineX
                guideY = r.lineY
            }

            fun onMove(key: String, dx: Float, dy: Float) {
                val cfg = currentConfig
                val b = cfg.box(key)
                val nb = b.copy(
                    x = (b.x + dx / cw).coerceIn(0f, (1f - b.w).coerceAtLeast(0f)),
                    y = (b.y + dy / ch).coerceIn(0f, (1f - b.h).coerceAtLeast(0f))
                )
                val boxes = cfg.boxes + (key to nb)
                currentOnChange(boxes)
                guidesFor(key, cfg.copy(boxes = boxes), false)
            }

            fun onResize(key: String, dx: Float, dy: Float) {
                val cfg = currentConfig
                val b = cfg.box(key)
                val nb = b.copy(
                    w = (b.w + dx / cw).coerceIn(minWf, (1f - b.x).coerceAtLeast(minWf)),
                    h = (b.h + dy / ch).coerceIn(minHf, (1f - b.y).coerceAtLeast(minHf))
                )
                val boxes = cfg.boxes + (key to nb)
                currentOnChange(boxes)
                guidesFor(key, cfg.copy(boxes = boxes), true)
            }

            fun onRelease(key: String, resize: Boolean) {
                val cfg = currentConfig
                if (cfg.autoSnap) {
                    val r = computeSnap(key, cfg, cw, ch, resize, thresholdPx, gridPx, marginPx)
                    val b = cfg.box(key)
                    val nb = if (resize) {
                        b.copy(
                            w = (b.w + r.dx / cw).coerceIn(minWf, (1f - b.x).coerceAtLeast(minWf)),
                            h = (b.h + r.dy / ch).coerceIn(minHf, (1f - b.y).coerceAtLeast(minHf))
                        )
                    } else {
                        b.copy(
                            x = (b.x + r.dx / cw).coerceIn(0f, (1f - b.w).coerceAtLeast(0f)),
                            y = (b.y + r.dy / ch).coerceIn(0f, (1f - b.h).coerceAtLeast(0f))
                        )
                    }
                    currentOnChange(cfg.boxes + (key to nb))
                }
                guideX = null
                guideY = null
                currentOnCommit()
            }

            fun onToggleLock(key: String) {
                val cfg = currentConfig
                val b = cfg.box(key)
                currentOnChange(cfg.boxes + (key to b.copy(locked = !b.locked)))
                currentOnCommit()
            }

            fun iconSize(b: BoxState, factor: Float) = with(density) {
                maxOf(min(b.w * cw, b.h * ch) * factor * config.iconSize / 20f, 10.dp.toPx()).toDp()
            }

            for (id in PlayerBoxes.ALL) {
                if (!config.isVisible(id)) continue
                val b = config.box(id)
                key(id) {
                    EditableBox(
                        id = id,
                        label = PlayerBoxes.LABELS.getValue(id),
                        box = b,
                        cw = cw,
                        ch = ch,
                        editMode = editMode,
                        showOutline = config.showBoxes,
                        onMove = ::onMove,
                        onResize = ::onResize,
                        onRelease = ::onRelease,
                        onToggleLock = ::onToggleLock
                    ) {
                        when (id) {
                            PlayerBoxes.BACK -> IconTap(
                                icon = Icons.Outlined.KeyboardArrowDown,
                                tint = Color.White.copy(0.75f),
                                size = iconSize(b, 0.7f),
                                enabled = !editMode,
                                onClick = onClose
                            )

                            PlayerBoxes.SOURCE -> Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "PLAYING FROM",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 8.sp,
                                    letterSpacing = 2.sp,
                                    fontFamily = font,
                                    maxLines = 1
                                )
                                Text(
                                    song.albumName.ifBlank { song.title }.uppercase(),
                                    color = Color.White.copy(0.85f),
                                    fontSize = 9.sp,
                                    letterSpacing = 1.sp,
                                    fontFamily = font,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            PlayerBoxes.ART -> {
                                val side = with(density) {
                                    (min(b.w * cw, b.h * ch) * config.artSize / 100f).toDp()
                                }
                                val shape = RoundedCornerShape(config.artRadius.dp)
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Box(
                                        modifier = Modifier
                                            .size(side)
                                            .shadow(if (config.artShadow) 16.dp else 0.dp, shape)
                                            .clip(shape)
                                            .background(Color.White.copy(0.08f))
                                    ) {
                                        ModernArt(bitmap = bitmap, uri = song.songArtUri)
                                    }
                                }
                            }

                            PlayerBoxes.INFO -> Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = if (config.titleCenter) Alignment.CenterHorizontally else Alignment.Start
                            ) {
                                Text(
                                    song.title,
                                    color = Color.White,
                                    fontSize = config.titleSize.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = font,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = if (config.titleCenter) TextAlign.Center else TextAlign.Start
                                )
                                Text(
                                    listOf(song.artist, song.albumName).filter { it.isNotBlank() }.joinToString(" - "),
                                    color = Color.White.copy(0.65f),
                                    fontSize = 13.sp,
                                    fontFamily = font,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = if (config.titleCenter) TextAlign.Center else TextAlign.Start
                                )
                            }

                            PlayerBoxes.LIKE -> IconTap(
                                icon = likeVector(config.likeIcon, isLiked),
                                tint = if (isLiked) accent else iconTint,
                                size = iconSize(b, 0.6f),
                                enabled = !editMode,
                                onClick = onToggleLike
                            )

                            PlayerBoxes.PROG -> Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center
                            ) {
                                ModernProgress(
                                    type = config.progType,
                                    progress = progress,
                                    bars = config.bars,
                                    seed = (song.id % 97L).toFloat(),
                                    activeColor = if (config.progAccent) accent else Color.White,
                                    radius = config.progRadius,
                                    interactive = !editMode,
                                    onSeeking = onSeekDrag,
                                    onSeek = onSeek,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                                if (config.showTimes) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(currentTime, color = Color.White.copy(0.65f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                        Text(totalTime, color = Color.White.copy(0.65f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }

                            PlayerBoxes.SHUF -> IconTap(
                                icon = Icons.Outlined.Shuffle,
                                tint = if (isShuffleEnabled) accent else iconTint.copy(alpha = 0.5f),
                                size = iconSize(b, 0.6f),
                                enabled = !editMode,
                                onClick = onToggleShuffle
                            )

                            PlayerBoxes.PREV -> IconTap(
                                icon = if (config.skipIcon == SkipIcon.SKIP) Icons.Outlined.SkipPrevious else Icons.Outlined.FastRewind,
                                tint = iconTint,
                                size = iconSize(b, 0.7f),
                                enabled = !editMode,
                                onClick = onPrev
                            )

                            PlayerBoxes.NEXT -> IconTap(
                                icon = if (config.skipIcon == SkipIcon.SKIP) Icons.Outlined.SkipNext else Icons.Outlined.FastForward,
                                tint = iconTint,
                                size = iconSize(b, 0.7f),
                                enabled = !editMode,
                                onClick = onNext
                            )

                            PlayerBoxes.REP -> IconTap(
                                icon = if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Outlined.RepeatOne else Icons.Outlined.Repeat,
                                tint = if (repeatMode == Player.REPEAT_MODE_OFF) iconTint.copy(alpha = 0.5f) else accent,
                                size = iconSize(b, 0.6f),
                                enabled = !editMode,
                                onClick = onToggleRepeat
                            )

                            PlayerBoxes.PLAY -> {
                                val tapCb by rememberUpdatedState(onPlayPause)
                                val holdCb by rememberUpdatedState(onHoldPlay)
                                var playMod = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(percent = config.playRadius.coerceIn(0, 50)))
                                    .background(if (config.playAccent) accent else Color.White)
                                if (!editMode) {
                                    playMod = playMod.tapOrHold(
                                        holdMillis = HOLD_TO_OPEN_MS,
                                        onTap = { tapCb() },
                                        onHold = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            holdCb()
                                        }
                                    )
                                }
                                Box(playMod, contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                        contentDescription = null,
                                        tint = if (config.playAccent) Color.White else Color(0xFF111111),
                                        modifier = Modifier.size(iconSize(b, 0.45f))
                                    )
                                }
                            }

                            PlayerBoxes.BADGE -> Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(1.dp, Color.White.copy(0.4f), RoundedCornerShape(config.badgeRadius.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                val fs = with(density) { (b.h * ch * 0.38f).toDp().value }.coerceIn(7f, 16f)
                                Text(
                                    formatLabel,
                                    color = Color.White.copy(0.8f),
                                    fontSize = fs.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }

                            PlayerBoxes.LYRICS -> IconTap(
                                icon = if (config.lyricsIcon == LyricsIcon.LINES) Icons.Outlined.Subject else Icons.Outlined.ChatBubbleOutline,
                                tint = iconTint,
                                size = iconSize(b, 0.6f),
                                enabled = !editMode,
                                onClick = onOpenLyrics
                            )

                            PlayerBoxes.QUEUE -> IconTap(
                                icon = if (config.queueIcon == QueueIcon.LIST) Icons.Outlined.QueueMusic else Icons.Outlined.MusicNote,
                                tint = iconTint,
                                size = iconSize(b, 0.6f),
                                enabled = !editMode,
                                onClick = onOpenQueue
                            )

                            PlayerBoxes.MORE -> IconTap(
                                icon = if (config.moreIcon == MoreIcon.DOTS) Icons.Outlined.MoreHoriz else Icons.Outlined.MoreVert,
                                tint = iconTint,
                                size = iconSize(b, 0.6f),
                                enabled = !editMode,
                                onClick = onOpenOptions
                            )

                            else -> {}
                        }
                    }
                }
            }

            // Guides, center lines, margins and grid (editor only)
            if (editMode) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (config.grid) {
                        var gx = 0f
                        while (gx <= size.width) {
                            drawLine(Color.White.copy(0.07f), Offset(gx, 0f), Offset(gx, size.height), 1f)
                            gx += gridPx
                        }
                        var gy = 0f
                        while (gy <= size.height) {
                            drawLine(Color.White.copy(0.07f), Offset(0f, gy), Offset(size.width, gy), 1f)
                            gy += gridPx
                        }
                    }
                    if (config.showBoxes) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        drawLine(Color.White.copy(0.25f), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1f)
                        drawLine(Color.White.copy(0.25f), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
                        drawRect(
                            Color.White.copy(0.18f),
                            topLeft = Offset(marginPx, marginPx / 2f),
                            size = Size(size.width - marginPx * 2f, size.height - marginPx),
                            style = Stroke(1f, pathEffect = dash)
                        )
                    }
                    guideX?.let { drawLine(GUIDE_PINK, Offset(it, 0f), Offset(it, size.height), 2f) }
                    guideY?.let { drawLine(GUIDE_PINK, Offset(0f, it), Offset(size.width, it), 2f) }
                }
            }
        }
    }
}

@Composable
private fun ModernBackground(config: PlayerLayoutConfig, glow: Color, accent: Color) {
    val brush: Brush = when (config.bg) {
        PlayerBg.ART -> Brush.verticalGradient(
            listOf(
                lerp(glow, Color.Black, 0.55f),
                lerp(glow, Color.Black, 0.72f),
                lerp(glow, Color.Black, 0.88f)
            )
        )
        PlayerBg.DARK -> SolidColor(Color(0xFF0B0B0B))
        PlayerBg.ACCENT -> Brush.verticalGradient(listOf(accent.copy(alpha = 0.8f), Color.Black))
    }
    Box(modifier = Modifier.fillMaxSize().background(brush))
}

@Composable
private fun ModernArt(bitmap: Bitmap?, uri: String?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else if (!uri.isNullOrEmpty() && !uri.startsWith("dummy")) {
        AsyncImage(
            model = uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Outlined.MusicNote,
                null,
                tint = Color.White.copy(0.25f),
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

private fun likeVector(icon: LikeIcon, liked: Boolean) = when (icon) {
    LikeIcon.HEART -> if (liked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder
    LikeIcon.STAR -> if (liked) Icons.Rounded.Star else Icons.Outlined.StarBorder
    LikeIcon.BOOKMARK -> if (liked) Icons.Rounded.Bookmark else Icons.Outlined.BookmarkBorder
}

@Composable
private fun IconTap(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

/** Positioned box. In edit mode: drag to move, yellow dot to resize, tag to lock. */
@Composable
private fun EditableBox(
    id: String,
    label: String,
    box: BoxState,
    cw: Float,
    ch: Float,
    editMode: Boolean,
    showOutline: Boolean,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, Float, Float) -> Unit,
    onRelease: (String, Boolean) -> Unit,
    onToggleLock: (String) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val wDp = with(density) { (box.w * cw).toDp() }
    val hDp = with(density) { (box.h * ch).toDp() }
    val canDrag = editMode && !box.locked

    var m = Modifier
        .offset { IntOffset((box.x * cw).roundToInt(), (box.y * ch).roundToInt()) }
        .size(wDp, hDp)
    if (editMode && showOutline) {
        m = m.drawBehind {
            drawRect(EDIT_YELLOW.copy(alpha = 0.08f))
            drawRect(
                EDIT_YELLOW,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            )
        }
    }
    if (editMode && box.locked) m = m.alpha(0.75f)
    if (canDrag) {
        m = m.pointerInput(id, cw, ch) {
            detectDragGestures(
                onDragEnd = { onRelease(id, false) },
                onDragCancel = { onRelease(id, false) }
            ) { change, drag ->
                change.consume()
                onMove(id, drag.x, drag.y)
            }
        }
    }

    Box(modifier = m) {
        content()
        if (editMode && showOutline) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .wrapContentSize(align = Alignment.TopStart, unbounded = true)
                    .background(EDIT_YELLOW, RoundedCornerShape(bottomEnd = 4.dp))
                    .clickable { onToggleLock(id) }
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            ) {
                Text(
                    text = (if (box.locked) "\uD83D\uDD12 " else "\uD83D\uDD13 ") + label,
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        if (canDrag) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .background(EDIT_YELLOW, CircleShape)
                    .pointerInput(id, cw, ch) {
                        detectDragGestures(
                            onDragEnd = { onRelease(id, true) },
                            onDragCancel = { onRelease(id, true) }
                        ) { change, drag ->
                            change.consume()
                            onResize(id, drag.x, drag.y)
                        }
                    }
            )
        }
    }
}

/** Waveform / line / pill seek bar. The waveform is a pattern generated from the song id (not decoded audio yet). */
@Composable
private fun ModernProgress(
    type: ProgressType,
    progress: Float,
    bars: Int,
    seed: Float,
    activeColor: Color,
    radius: Int,
    interactive: Boolean,
    onSeeking: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val shown = (if (dragging) dragValue else progress).coerceIn(0f, 1f)
    val inactive = Color.White.copy(alpha = 0.25f)

    var m = modifier.onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
    if (interactive) {
        m = m
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val v = (off.x / widthPx).coerceIn(0f, 1f)
                    onSeeking(v)
                    onSeek(v)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        dragValue = (off.x / widthPx).coerceIn(0f, 1f)
                        onSeeking(dragValue)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragValue)
                    },
                    onDragCancel = { dragging = false }
                ) { change, _ ->
                    change.consume()
                    dragValue = (change.position.x / widthPx).coerceIn(0f, 1f)
                    onSeeking(dragValue)
                }
            }
    }

    Canvas(modifier = m) {
        when (type) {
            ProgressType.WAVE -> {
                val n = bars.coerceIn(8, 120)
                val slot = size.width / n
                val barW = slot * 0.55f
                val corner = CornerRadius(radius.dp.toPx())
                for (i in 0 until n) {
                    val v = 0.25f + 0.75f * abs(sin(i * 0.9f + seed) * cos(i * 0.37f + seed * 0.5f))
                    val h = maxOf(v * size.height, 4.dp.toPx())
                    val cx = i * slot + slot / 2f
                    val played = (i + 0.5f) / n <= shown
                    drawRoundRect(
                        color = if (played) activeColor else inactive,
                        topLeft = Offset(cx - barW / 2f, (size.height - h) / 2f),
                        size = Size(barW, h),
                        cornerRadius = corner
                    )
                }
            }

            ProgressType.LINE, ProgressType.PILL -> {
                val th = if (type == ProgressType.PILL) 8.dp.toPx() else 4.dp.toPx()
                val r = if (type == ProgressType.PILL) th / 2f else radius.dp.toPx()
                val top = (size.height - th) / 2f
                drawRoundRect(inactive, Offset(0f, top), Size(size.width, th), CornerRadius(r))
                drawRoundRect(activeColor, Offset(0f, top), Size(size.width * shown, th), CornerRadius(r))
            }
        }
    }
}

/** Tap = [onTap]. Keep the finger down for [holdMillis] = [onHold]. */
private fun Modifier.tapOrHold(holdMillis: Long, onTap: () -> Unit, onHold: () -> Unit): Modifier =
    pointerInput(holdMillis) {
        awaitEachGesture {
            awaitFirstDown()
            var released = false
            val up = withTimeoutOrNull(holdMillis) {
                val r = waitForUpOrCancellation()
                released = true
                r
            }
            if (up != null) {
                onTap()
            } else if (!released) {
                onHold()
                waitForUpOrCancellation()
            }
        }
    }

private data class SnapResult(val dx: Float, val dy: Float, val lineX: Float?, val lineY: Float?)

private fun bestSnap(moving: List<Float>, targets: List<Float>, threshold: Float): Pair<Float, Float>? {
    var best: Pair<Float, Float>? = null
    for (m in moving) {
        for (t in targets) {
            val d = t - m
            val cur = best
            if (abs(d) <= threshold && (cur == null || abs(d) < abs(cur.first))) {
                best = Pair(d, t)
            }
        }
    }
    return best
}

private fun computeSnap(
    key: String,
    cfg: PlayerLayoutConfig,
    cw: Float,
    ch: Float,
    resize: Boolean,
    threshold: Float,
    gridPx: Float,
    marginPx: Float
): SnapResult {
    val b = cfg.box(key)
    val l = b.x * cw
    val t = b.y * ch
    val w = b.w * cw
    val h = b.h * ch
    val tx = mutableListOf(marginPx, cw / 2f, cw - marginPx, 0f, cw)
    val ty = mutableListOf(marginPx, ch / 2f, ch - marginPx, 0f, ch)
    for (o in PlayerBoxes.ALL) {
        if (o == key || !cfg.isVisible(o)) continue
        val q = cfg.box(o)
        val ql = q.x * cw
        val qt = q.y * ch
        val qw = q.w * cw
        val qh = q.h * ch
        tx.add(ql); tx.add(ql + qw / 2f); tx.add(ql + qw)
        ty.add(qt); ty.add(qt + qh / 2f); ty.add(qt + qh)
    }
    val mx = if (resize) listOf(l + w) else listOf(l, l + w / 2f, l + w)
    val my = if (resize) listOf(t + h) else listOf(t, t + h / 2f, t + h)
    var bx = bestSnap(mx, tx, threshold)
    var by = bestSnap(my, ty, threshold)
    if (cfg.grid && gridPx > 0f) {
        if (bx == null) {
            val e = mx.first()
            val g = (e / gridPx).roundToInt() * gridPx
            bx = Pair(g - e, g)
        }
        if (by == null) {
            val e = my.first()
            val g = (e / gridPx).roundToInt() * gridPx
            by = Pair(g - e, g)
        }
    }
    return SnapResult(bx?.first ?: 0f, by?.first ?: 0f, bx?.second, by?.second)
}
