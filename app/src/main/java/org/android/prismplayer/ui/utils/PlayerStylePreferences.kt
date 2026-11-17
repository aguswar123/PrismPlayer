package org.android.prismplayer.ui.utils

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class PlayerStyle { DEFAULT, MODERN }
enum class PlayerBg { ART, DARK, ACCENT }
enum class ProgressType { WAVE, LINE, PILL }
enum class LikeIcon { HEART, STAR, BOOKMARK }
enum class LyricsIcon { LINES, BUBBLE }
enum class QueueIcon { LIST, NOTE }
enum class MoreIcon { DOTS, VDOTS }
enum class SkipIcon { SKIP, RW_FF }

/** Box position/size as fractions (0..1) of the player canvas. */
data class BoxState(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val locked: Boolean = false
)

object PlayerBoxes {
    const val BACK = "back"
    const val SOURCE = "source"
    const val ART = "art"
    const val INFO = "info"
    const val LIKE = "like"
    const val PROG = "prog"
    const val SHUF = "shuf"
    const val PREV = "prev"
    const val PLAY = "play"
    const val NEXT = "next"
    const val REP = "rep"
    const val BADGE = "badge"
    const val LYRICS = "lyrics"
    const val QUEUE = "queue"
    const val MORE = "more"

    val ALL = listOf(BACK, SOURCE, ART, INFO, LIKE, PROG, SHUF, PREV, PLAY, NEXT, REP, BADGE, LYRICS, QUEUE, MORE)

    val LABELS = mapOf(
        BACK to "BACK", SOURCE to "PLAYING_FROM", ART to "ARTWORK", INFO to "TITLE", LIKE to "LIKE",
        PROG to "PROGRESS", SHUF to "SHUFFLE", PREV to "PREV", PLAY to "PLAY", NEXT to "NEXT",
        REP to "REPEAT", BADGE to "FORMAT", LYRICS to "LYRICS", QUEUE to "QUEUE", MORE to "MORE"
    )

    private fun b(x: Float, y: Float, w: Float, h: Float) = BoxState(x / 300f, y / 540f, w / 300f, h / 540f)

    val DEFAULTS: Map<String, BoxState> = mapOf(
        BACK to b(12f, 8f, 30f, 30f),
        SOURCE to b(90f, 8f, 120f, 30f),
        ART to b(24f, 46f, 252f, 252f),
        INFO to b(16f, 306f, 210f, 48f),
        LIKE to b(240f, 312f, 44f, 36f),
        PROG to b(16f, 362f, 268f, 58f),
        SHUF to b(16f, 446f, 36f, 32f),
        PREV to b(68f, 440f, 44f, 44f),
        PLAY to b(118f, 430f, 64f, 64f),
        NEXT to b(188f, 440f, 44f, 44f),
        REP to b(248f, 446f, 36f, 32f),
        BADGE to b(16f, 504f, 92f, 26f),
        MORE to b(178f, 502f, 30f, 30f),
        LYRICS to b(216f, 502f, 30f, 30f),
        QUEUE to b(254f, 502f, 30f, 30f)
    )
}

data class PlayerLayoutConfig(
    val style: PlayerStyle = PlayerStyle.DEFAULT,
    val bg: PlayerBg = PlayerBg.ART,
    /** ARGB color. 0 = use the album color. */
    val accent: Int = 0xFFD71921.toInt(),
    val artRadius: Int = 8,
    val artSize: Int = 100,
    val artShadow: Boolean = true,
    val progType: ProgressType = ProgressType.WAVE,
    val bars: Int = 56,
    val progAccent: Boolean = false,
    val progRadius: Int = 2,
    val playRadius: Int = 50,
    val badgeRadius: Int = 3,
    val likeIcon: LikeIcon = LikeIcon.HEART,
    val lyricsIcon: LyricsIcon = LyricsIcon.LINES,
    val queueIcon: QueueIcon = QueueIcon.LIST,
    val moreIcon: MoreIcon = MoreIcon.DOTS,
    val skipIcon: SkipIcon = SkipIcon.SKIP,
    val iconSize: Int = 20,
    val iconAccent: Boolean = false,
    val playAccent: Boolean = false,
    val titleSize: Int = 22,
    val titleCenter: Boolean = false,
    val mono: Boolean = false,
    val showLike: Boolean = true,
    val showBadge: Boolean = true,
    val showLyrics: Boolean = true,
    val showQueue: Boolean = true,
    val showMore: Boolean = false,
    val showTimes: Boolean = true,
    val showSource: Boolean = true,
    val showBoxes: Boolean = true,
    val autoSnap: Boolean = true,
    val grid: Boolean = false,
    val snapDist: Int = 8,
    val boxes: Map<String, BoxState> = PlayerBoxes.DEFAULTS
) {
    fun box(key: String): BoxState = boxes[key] ?: PlayerBoxes.DEFAULTS.getValue(key)

    fun isVisible(key: String): Boolean = when (key) {
        PlayerBoxes.LIKE -> showLike
        PlayerBoxes.SOURCE -> showSource
        PlayerBoxes.BADGE -> showBadge
        PlayerBoxes.LYRICS -> showLyrics
        PlayerBoxes.QUEUE -> showQueue
        PlayerBoxes.MORE -> showMore
        else -> true
    }
}

class PlayerStylePreferences private constructor(context: Context) {
    // Same file the rest of the app already uses for its config values.
    private val prefs: SharedPreferences =
        context.getSharedPreferences("prism_config", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(load())
    val config: StateFlow<PlayerLayoutConfig> = _config.asStateFlow()

    fun save(cfg: PlayerLayoutConfig) {
        _config.value = cfg
        prefs.edit().putString(KEY, toJson(cfg).toString()).apply()
    }

    fun update(block: (PlayerLayoutConfig) -> PlayerLayoutConfig) = save(block(_config.value))

    fun setStyle(style: PlayerStyle) = update { it.copy(style = style) }

    fun resetLayoutOnly() = update { it.copy(boxes = PlayerBoxes.DEFAULTS) }

    fun resetAll() = update { PlayerLayoutConfig(style = it.style) }

    private fun load(): PlayerLayoutConfig {
        val raw = prefs.getString(KEY, null) ?: return PlayerLayoutConfig()
        return try {
            fromJson(JSONObject(raw))
        } catch (e: Exception) {
            PlayerLayoutConfig()
        }
    }

    companion object {
        private const val KEY = "player_layout_v1"

        @Volatile
        private var instance: PlayerStylePreferences? = null

        fun get(context: Context): PlayerStylePreferences =
            instance ?: synchronized(this) {
                instance ?: PlayerStylePreferences(context.applicationContext).also { instance = it }
            }

        private inline fun <reified T : Enum<T>> JSONObject.enumOr(key: String, def: T): T =
            try {
                enumValueOf<T>(optString(key, def.name))
            } catch (e: IllegalArgumentException) {
                def
            }

        private fun toJson(c: PlayerLayoutConfig): JSONObject = JSONObject().apply {
            put("style", c.style.name)
            put("bg", c.bg.name)
            put("accent", c.accent)
            put("artRadius", c.artRadius)
            put("artSize", c.artSize)
            put("artShadow", c.artShadow)
            put("progType", c.progType.name)
            put("bars", c.bars)
            put("progAccent", c.progAccent)
            put("progRadius", c.progRadius)
            put("playRadius", c.playRadius)
            put("badgeRadius", c.badgeRadius)
            put("likeIcon", c.likeIcon.name)
            put("lyricsIcon", c.lyricsIcon.name)
            put("queueIcon", c.queueIcon.name)
            put("moreIcon", c.moreIcon.name)
            put("skipIcon", c.skipIcon.name)
            put("iconSize", c.iconSize)
            put("iconAccent", c.iconAccent)
            put("playAccent", c.playAccent)
            put("titleSize", c.titleSize)
            put("titleCenter", c.titleCenter)
            put("mono", c.mono)
            put("showLike", c.showLike)
            put("showBadge", c.showBadge)
            put("showLyrics", c.showLyrics)
            put("showQueue", c.showQueue)
            put("showMore", c.showMore)
            put("showTimes", c.showTimes)
            put("showSource", c.showSource)
            put("showBoxes", c.showBoxes)
            put("autoSnap", c.autoSnap)
            put("grid", c.grid)
            put("snapDist", c.snapDist)
            val boxesJson = JSONObject()
            c.boxes.forEach { (k, v) ->
                boxesJson.put(
                    k,
                    JSONObject().put("x", v.x.toDouble()).put("y", v.y.toDouble())
                        .put("w", v.w.toDouble()).put("h", v.h.toDouble()).put("locked", v.locked)
                )
            }
            put("boxes", boxesJson)
        }

        private fun fromJson(j: JSONObject): PlayerLayoutConfig {
            val d = PlayerLayoutConfig()
            val boxes = PlayerBoxes.DEFAULTS.toMutableMap()
            j.optJSONObject("boxes")?.let { bj ->
                PlayerBoxes.ALL.forEach { key ->
                    bj.optJSONObject(key)?.let { o ->
                        val def = PlayerBoxes.DEFAULTS.getValue(key)
                        boxes[key] = BoxState(
                            x = o.optDouble("x", def.x.toDouble()).toFloat(),
                            y = o.optDouble("y", def.y.toDouble()).toFloat(),
                            w = o.optDouble("w", def.w.toDouble()).toFloat(),
                            h = o.optDouble("h", def.h.toDouble()).toFloat(),
                            locked = o.optBoolean("locked", false)
                        )
                    }
                }
            }
            return PlayerLayoutConfig(
                style = j.enumOr("style", d.style),
                bg = j.enumOr("bg", d.bg),
                accent = j.optInt("accent", d.accent),
                artRadius = j.optInt("artRadius", d.artRadius),
                artSize = j.optInt("artSize", d.artSize),
                artShadow = j.optBoolean("artShadow", d.artShadow),
                progType = j.enumOr("progType", d.progType),
                bars = j.optInt("bars", d.bars),
                progAccent = j.optBoolean("progAccent", d.progAccent),
                progRadius = j.optInt("progRadius", d.progRadius),
                playRadius = j.optInt("playRadius", d.playRadius),
                badgeRadius = j.optInt("badgeRadius", d.badgeRadius),
                likeIcon = j.enumOr("likeIcon", d.likeIcon),
                lyricsIcon = j.enumOr("lyricsIcon", d.lyricsIcon),
                queueIcon = j.enumOr("queueIcon", d.queueIcon),
                moreIcon = j.enumOr("moreIcon", d.moreIcon),
                skipIcon = j.enumOr("skipIcon", d.skipIcon),
                iconSize = j.optInt("iconSize", d.iconSize),
                iconAccent = j.optBoolean("iconAccent", d.iconAccent),
                playAccent = j.optBoolean("playAccent", d.playAccent),
                titleSize = j.optInt("titleSize", d.titleSize),
                titleCenter = j.optBoolean("titleCenter", d.titleCenter),
                mono = j.optBoolean("mono", d.mono),
                showLike = j.optBoolean("showLike", d.showLike),
                showBadge = j.optBoolean("showBadge", d.showBadge),
                showLyrics = j.optBoolean("showLyrics", d.showLyrics),
                showQueue = j.optBoolean("showQueue", d.showQueue),
                showMore = j.optBoolean("showMore", d.showMore),
                showTimes = j.optBoolean("showTimes", d.showTimes),
                showSource = j.optBoolean("showSource", d.showSource),
                showBoxes = j.optBoolean("showBoxes", d.showBoxes),
                autoSnap = j.optBoolean("autoSnap", d.autoSnap),
                grid = j.optBoolean("grid", d.grid),
                snapDist = j.optInt("snapDist", d.snapDist),
                boxes = boxes
            )
        }
    }
}
