package com.royalplus.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * Full Royal Design V20 catalog (25 sports symbols). Keeping the ID list here
 * prevents arbitrary asset paths and allows the complete collection to be
 * checked at build time, even though the current Sports screen is football-only.
 */
internal enum class RoyalV20SportsIconId(val fileName: String, val label: String, val ball: Boolean = false) {
    TODAY_MATCHES("today_matches", "مباريات اليوم"),
    LIVE_SCORES("live_scores", "النتائج المباشرة"),
    MATCH_RESULTS("match_results", "نتائج المباريات"),
    FIXTURES("fixtures", "جدول المباريات"),
    STANDINGS("standings", "الترتيب"),
    FOOTBALL("football", "كرة القدم", true),
    BASKETBALL("basketball", "كرة السلة", true),
    TENNIS("tennis", "التنس", true),
    VOLLEYBALL("volleyball", "الكرة الطائرة", true),
    CRICKET("cricket", "الكريكيت", true),
    TROPHY("trophy", "الكأس"),
    TEAMS("teams", "الفرق"),
    PLAYERS("players", "اللاعبون"),
    STADIUM("stadium", "الملعب"),
    GOAL("goal", "المرمى"),
    WHISTLE("whistle", "صافرة الحكم"),
    YELLOW_CARD("yellow_card", "البطاقة الصفراء"),
    RED_CARD("red_card", "البطاقة الحمراء"),
    MEDAL("medal", "الميدالية"),
    STATISTICS("statistics", "الإحصائيات"),
    BRACKET("bracket", "جدول البطولة"),
    TRANSFER("transfer", "انتقالات اللاعبين"),
    SPORTS_NEWS("sports_news", "أخبار الرياضة"),
    REPLAY("replay", "إعادة اللقطة"),
    STOPWATCH("stopwatch", "ساعة المباراة")
}

internal object RoyalV20SportsIconPreferences {
    const val PREFERENCES_NAME = "royal_v20_sports_icon_preferences"
    const val KEY_ANIMATED = "sports_icons_animated"
    const val DEFAULT_ANIMATED = true

    fun isAnimated(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANIMATED, DEFAULT_ANIMATED)

    fun setAnimated(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ANIMATED, enabled).apply()
    }
}

/**
 * Decode the pinned original V20 SVG to Android bitmap using AndroidSVG.
 * No WebView, browser runtime, HTML, network call, or SVG SMIL playback.
 */
private object RoyalV20SportsBitmapCache {
    private const val EDGE_PX = 384
    private const val MAX_CACHE_BYTES = 8 * 1024 * 1024

    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, icon: RoyalV20SportsIconId, animated: Boolean): Bitmap? {
        val style = if (animated) "animated" else "static"
        val key = "$style/${icon.fileName}"
        cache.get(key)?.let { return it }
        return synchronized(cache) {
            cache.get(key) ?: runCatching {
                val svg = SVG.getFromAsset(context.assets, "sports_v20/$style/${icon.fileName}.svg")
                val bitmap = Bitmap.createBitmap(EDGE_PX, EDGE_PX, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(AndroidCanvas(bitmap))
                cache.put(key, bitmap)
                bitmap
            }.getOrNull()
        }
    }
}

@Composable
private fun rememberV20SportsAnimationEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    val preferences = remember(context) {
        context.getSharedPreferences(RoyalV20SportsIconPreferences.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var enabled by remember(preferences) {
        mutableStateOf(preferences.getBoolean(
            RoyalV20SportsIconPreferences.KEY_ANIMATED,
            RoyalV20SportsIconPreferences.DEFAULT_ANIMATED
        ))
    }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == null || key == RoyalV20SportsIconPreferences.KEY_ANIMATED) {
                enabled = prefs.getBoolean(
                    RoyalV20SportsIconPreferences.KEY_ANIMATED,
                    RoyalV20SportsIconPreferences.DEFAULT_ANIMATED
                )
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return enabled
}

@Composable
internal fun RoyalV20SportsIcon(
    icon: RoyalV20SportsIconId,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current.applicationContext
    val animated = rememberV20SportsAnimationEnabled()
    var image by remember(icon, animated) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(context, icon, animated) {
        image = withContext(Dispatchers.IO) {
            RoyalV20SportsBitmapCache.load(context, icon, animated)?.asImageBitmap()
        }
    }

    Box(modifier.semantics {
        this.contentDescription = contentDescription ?: icon.label
    }) {
        if (image != null) {
            Image(
                bitmap = image!!,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Canvas(Modifier.fillMaxSize()) {
                val edge = size.minDimension
                drawCircle(Color(0xFF102A44), edge * 0.46f, Offset(size.width / 2f, size.height / 2f))
                drawCircle(Color(0xFFD6B86D), edge * 0.28f,
                    Offset(size.width / 2f, size.height / 2f), style = Stroke(edge * 0.035f))
            }
        }
        if (animated) {
            RoyalV20SportsMotionOverlay(icon, Modifier.fillMaxSize())
        }
    }
}

/**
 * Native motion layer for Android 8. AndroidSVG preserves the V20 artwork
 * exactly but deliberately does not execute SVG SMIL; this overlay supplies
 * lightweight motion while leaving every original path and gradient intact.
 */
@Composable
private fun RoyalV20SportsMotionOverlay(icon: RoyalV20SportsIconId, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "royal-v20-sports-motion")
    val phase by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1_900, easing = LinearEasing), RepeatMode.Restart),
        label = "royal-v20-sports-phase"
    )
    Canvas(modifier) {
        val u = size.minDimension.coerceAtLeast(1f)
        val center = Offset(size.width / 2f, size.height / 2f)
        val gold = Color(0xFFFFD36A)
        val cyan = Color(0xFF74D9D0)
        if (icon.ball) {
            val radius = u * 0.235f
            drawArc(
                color = cyan.copy(alpha = 0.82f),
                startAngle = phase * 360f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                style = Stroke(width = u * 0.018f, cap = StrokeCap.Round)
            )
        } else {
            val pulse = 0.45f + 0.45f * sin(phase * 2f * PI).toFloat()
            drawCircle(
                color = gold.copy(alpha = 0.50f * pulse),
                radius = u * (0.035f + 0.012f * pulse),
                center = Offset(size.width * 0.78f, size.height * 0.22f)
            )
            drawCircle(
                color = cyan.copy(alpha = 0.20f * pulse),
                radius = u * (0.10f + 0.025f * pulse),
                center = center,
                style = Stroke(width = u * 0.008f)
            )
        }
        val gleamX = size.width * (0.22f + 0.56f * phase)
        drawLine(
            color = Color.White.copy(alpha = 0.42f),
            start = Offset(gleamX, size.height * 0.105f),
            end = Offset(gleamX + u * 0.07f, size.height * 0.105f),
            strokeWidth = u * 0.012f,
            cap = StrokeCap.Round
        )
    }
}
