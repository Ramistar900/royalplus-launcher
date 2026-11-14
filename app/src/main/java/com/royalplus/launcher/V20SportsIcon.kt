package com.royalplus.launcher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royal.adaptive.LocalRoyalAdaptiveProfile
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The complete 25-icon Royal Design V20 sports catalog. */
internal enum class RoyalV20SportsIconId(val fileName: String, val label: String) {
    TODAY_MATCHES("today_matches", "مباريات اليوم"),
    LIVE_SCORES("live_scores", "النتائج المباشرة"),
    MATCH_RESULTS("match_results", "نتائج المباريات"),
    FIXTURES("fixtures", "جدول المباريات"),
    STANDINGS("standings", "الترتيب"),
    FOOTBALL("football", "كرة القدم"),
    BASKETBALL("basketball", "كرة السلة"),
    TENNIS("tennis", "التنس"),
    VOLLEYBALL("volleyball", "الكرة الطائرة"),
    CRICKET("cricket", "الكريكيت"),
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

/**
 * Sizes V20 icons from the actual Compose window profile. Resolution/window
 * width gives a small extra boost on wide TV/desktop layouts, while all sizes
 * remain bounded for compact phones and small television launch areas.
 */
@Composable
internal fun royalV20SportsIconSize(base: Dp, max: Dp = 96.dp): Dp {
    val profile = LocalRoyalAdaptiveProfile.current
    val widthBoost = ((profile.windowWidthDp.value - 1280f) / 2560f)
        .coerceIn(0f, 0.15f)
    val scaled = profile.iconSize(base).value * (1f + widthBoost)
    return scaled.coerceIn(base.value, max.value).dp
}

private object RoyalV20SportsBitmapCache {
    private const val EDGE_PX = 512
    private const val MAX_CACHE_BYTES = 12 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun load(context: Context, icon: RoyalV20SportsIconId): Bitmap? {
        val key = icon.fileName
        cache.get(key)?.let { return it }
        return synchronized(cache) {
            cache.get(key) ?: runCatching {
                val svg = SVG.getFromAsset(context.assets, "sports_v20/static/${icon.fileName}.svg")
                val bitmap = Bitmap.createBitmap(EDGE_PX, EDGE_PX, Bitmap.Config.ARGB_8888)
                svg.renderToCanvas(AndroidCanvas(bitmap))
                cache.put(key, bitmap)
                bitmap
            }.getOrNull()
        }
    }
}

/** Always renders the original V20 static artwork; sports motion is disabled. */
@Composable
internal fun RoyalV20SportsIcon(
    icon: RoyalV20SportsIconId,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val context = LocalContext.current.applicationContext
    var image by remember(icon) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    LaunchedEffect(context, icon) {
        image = withContext(Dispatchers.IO) {
            RoyalV20SportsBitmapCache.load(context, icon)?.asImageBitmap()
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
                drawCircle(
                    Color(0xFFD6B86D),
                    edge * 0.28f,
                    Offset(size.width / 2f, size.height / 2f),
                    style = Stroke(edge * 0.035f)
                )
            }
        }
    }
}
