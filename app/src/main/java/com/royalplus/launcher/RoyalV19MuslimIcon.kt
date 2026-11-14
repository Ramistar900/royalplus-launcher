package com.royalplus.launcher
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caverock.androidsvg.SVG
import com.royal.adaptive.LocalRoyalAdaptiveProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The full V19 Muslim icon catalog in static form only. */
internal enum class RoyalV19MuslimIconId(val fileName:String,val isPng:Boolean,val label:String) {
 CHARITY("charity.svg",false,"الصدقة"), DHIKR("dhikr.svg",false,"الذكر"), EID("eid.svg",false,"العيد"),
 EVENING_AZKAR("evening_azkar.svg",false,"أذكار المساء"), FASTING("fasting.svg",false,"الصيام"),
 HADITH("hadith.svg",false,"الحديث"), HIJRI_CALENDAR("hijri_calendar.svg",false,"التقويم الهجري"),
 ISLAMIC_EVENTS("islamic_events.svg",false,"المناسبات الإسلامية"), MORNING_AZKAR("morning_azkar.svg",false,"أذكار الصباح"),
 MOSQUE("mosque.svg",false,"المسجد"), PRAYER_TIMES("prayer_times.svg",false,"مواقيت الصلاة"),
 QIBLA("qibla.svg",false,"القبلة"), QURAN_AUDIO("quran_audio.svg",false,"استماع القرآن"),
 QURAN_BOOKMARK("quran_bookmark.svg",false,"علامة القرآن"), RAMADAN("ramadan.svg",false,"رمضان"),
 TASBIH("tasbih.svg",false,"المسبحة"), WUDU("wudu.svg",false,"الوضوء"), ZAKAT("zakat.svg",false,"الزكاة"),
 PRAYER_ICON("file_000000001218820a8a39eaae196b66e0.png",true,"أيقونة الصلاة"),
 DUA_ICON("file_000000007dc0820aa6c0f334ad42bca4.png",true,"أيقونة الدعاء"),
 QURAN_ICON("quran_icon.png",true,"أيقونة القرآن الكريم"), QARIS_ICON("qaris_icon.png",true,"أيقونة القراء");
 val assetPath:String get()="muslim_v19/static/$fileName"
}
private object RoyalV19MuslimBitmapCache {
 private const val EDGE=512
 private val cache=object:LruCache<String,Bitmap>(18*1024*1024) {
  override fun sizeOf(key:String,value:Bitmap)=value.byteCount
 }
 fun load(context:Context,id:RoyalV19MuslimIconId):Bitmap? {
  cache.get(id.assetPath)?.let{return it}
  return synchronized(cache) {
   cache.get(id.assetPath)?:runCatching {
    val bmp=if(id.isPng) context.assets.open(id.assetPath).use{BitmapFactory.decodeStream(it)}
    else SVG.getFromAsset(context.assets,id.assetPath).let{svg->
     Bitmap.createBitmap(EDGE,EDGE,Bitmap.Config.ARGB_8888).also{svg.renderToCanvas(AndroidCanvas(it))}
    }
    bmp?.also{cache.put(id.assetPath,it)}
   }.getOrNull()
  }
 }
}
@Composable internal fun royalV19MuslimIconSize(base:Dp,maxSize:Dp=80.dp):Dp {
 val profile=LocalRoyalAdaptiveProfile.current
 val widthBoost=((profile.windowWidthDp.value-1280f)/2560f).coerceIn(-0.12f,0.15f)
 return (profile.iconSize(base).value*(1f+widthBoost)).coerceIn(base.value*0.76f,maxSize.value).dp
}
@Composable internal fun RoyalV19MuslimIcon(icon:RoyalV19MuslimIconId,modifier:Modifier=Modifier,contentDescription:String?=null) {
 val context=LocalContext.current.applicationContext
 var image by remember(icon){mutableStateOf<ImageBitmap?>(null)}
 LaunchedEffect(context,icon){image=withContext(Dispatchers.IO){RoyalV19MuslimBitmapCache.load(context,icon)?.asImageBitmap()}}
 Box(modifier.semantics{this.contentDescription=contentDescription?:icon.label}) {
  image?.let{Image(bitmap=it,contentDescription=null,modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
 }
}