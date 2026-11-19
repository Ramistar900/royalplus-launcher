package com.royalplus.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val ROYAL_PLUS_LATEST_RELEASE_URL =
    "https://github.com/Ramistar900/royalplus-launcher/releases/latest"
private const val ROYAL_PLUS_RELEASE_API_URL =
    "https://api.github.com/repos/Ramistar900/royalplus-launcher/releases/latest"

private data class RoyalReleaseCheck(
    val latestBuildNumber: Long? = null,
    val releaseUrl: String = ROYAL_PLUS_LATEST_RELEASE_URL,
    val noPublishedRelease: Boolean = false,
    val failed: Boolean = false
)

@Composable
internal fun RoyalShareUpdatesSection(
    language: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val installedVersionCode = remember(context) { installedRoyalVersionCode(context) }
    var refreshCounter by remember { mutableStateOf(0) }
    var releaseCheck by remember { mutableStateOf<RoyalReleaseCheck?>(null) }

    LaunchedEffect(refreshCounter) {
        releaseCheck = withContext(Dispatchers.IO) { fetchLatestRoyalRelease() }
    }

    val qrImage = remember {
        runCatching { createRoyalPlusReleaseQr().asImageBitmap() }.getOrNull()
    }
    val isEnglish = language == "en"
    val isTurkish = language == "tr"
    val updateAvailable = releaseCheck?.latestBuildNumber?.let { it > installedVersionCode } == true
    val statusText = when {
        releaseCheck == null -> when {
            isEnglish -> "Checking for official updates…"
            isTurkish -> "Resmî güncellemeler kontrol ediliyor…"
            else -> "جارٍ التحقق من التحديثات الرسمية…"
        }
        releaseCheck?.noPublishedRelease == true -> when {
            isEnglish -> "No official release has been published yet."
            isTurkish -> "Henüz resmî bir sürüm yayımlanmadı."
            else -> "لا يوجد إصدار رسمي منشور حتى الآن."
        }
        releaseCheck?.failed == true -> when {
            isEnglish -> "Could not check now. Check the internet connection and retry."
            isTurkish -> "Şu anda kontrol edilemedi. İnternet bağlantısını kontrol edip yeniden deneyin."
            else -> "تعذر التحقق الآن. افحص اتصال الإنترنت ثم أعد المحاولة."
        }
        releaseCheck?.latestBuildNumber == null -> when {
            isEnglish -> "The published release number could not be verified."
            isTurkish -> "Yayımlanan sürüm numarası doğrulanamadı."
            else -> "تعذر التحقق من رقم الإصدار المنشور."
        }
        updateAvailable -> when {
            isEnglish -> "New update available • Build #${releaseCheck?.latestBuildNumber}"
            isTurkish -> "Yeni güncelleme var • Yapı #${releaseCheck?.latestBuildNumber}"
            else -> "يوجد تحديث جديد • الإصدار #${releaseCheck?.latestBuildNumber}"
        }
        else -> when {
            isEnglish -> "You are using the latest version • Build #${releaseCheck?.latestBuildNumber}"
            isTurkish -> "En güncel sürümü kullanıyorsunuz • Yapı #${releaseCheck?.latestBuildNumber}"
            else -> "أنت تستخدم أحدث إصدار • الإصدار #${releaseCheck?.latestBuildNumber}"
        }
    }

    val cardShape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF0B1119))
            .border(1.dp, Color(0x99D9B65B), cardShape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            when {
                isEnglish -> "Share ROYAL+ & official updates"
                isTurkish -> "ROYAL+ paylaşımı ve resmî güncellemeler"
                else -> "مشاركة ROYAL+ والتحديثات الرسمية"
            },
            color = Color(0xFFE5C36A),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            when {
                isEnglish -> "Scan the code to open the latest published release page. The address always leads to the newest official release."
                isTurkish -> "En son yayımlanan resmî sürüm sayfasını açmak için kodu tarayın. Bağlantı her zaman en yeni resmî sürüme yönelir."
                else -> "امسح الرمز لفتح صفحة أحدث إصدار منشور. الرابط ثابت ويقود دائماً إلى أحدث إصدار رسمي."
            },
            color = Color(0xFFCFD5DE),
            fontSize = 12.sp
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (qrImage != null) {
                Image(
                    bitmap = qrImage,
                    contentDescription = if (isEnglish) "ROYAL+ latest release QR code" else if (isTurkish) "ROYAL+ en son sürüm QR kodu" else "رمز QR لأحدث إصدار من ROYAL+",
                    modifier = Modifier.size(174.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(7.dp)
                )
            } else {
                Text("QR", color = Color(0xFF0B1119), modifier = Modifier.background(Color.White).padding(34.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    statusText,
                    color = if (updateAvailable) Color(0xFFFFD166) else Color(0xFFB9DCD8),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                RoyalShareUpdateAction(
                    title = when {
                        isEnglish -> "Share app"
                        isTurkish -> "Uygulamayı paylaş"
                        else -> "مشاركة التطبيق"
                    },
                    onClick = {
                        try {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "ROYAL+ Launcher\n$ROYAL_PLUS_LATEST_RELEASE_URL"
                                )
                            }
                            context.startActivity(
                                Intent.createChooser(
                                    sendIntent,
                                    if (isEnglish) "Share ROYAL+" else if (isTurkish) "ROYAL+'ı paylaş" else "مشاركة ROYAL+"
                                )
                            )
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                if (isEnglish) "No sharing app is available." else if (isTurkish) "Paylaşım uygulaması bulunamadı." else "لا يوجد تطبيق متاح للمشاركة.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
                RoyalShareUpdateAction(
                    title = if (updateAvailable) {
                        if (isEnglish) "Open new update" else if (isTurkish) "Yeni güncellemeyi aç" else "فتح التحديث الجديد"
                    } else {
                        if (isEnglish) "Open release page" else if (isTurkish) "Sürüm sayfasını aç" else "فتح صفحة الإصدارات"
                    },
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(releaseCheck?.releaseUrl ?: ROYAL_PLUS_LATEST_RELEASE_URL))
                            )
                        } catch (_: Exception) {
                            Toast.makeText(context, if (isEnglish) "Could not open the release page." else if (isTurkish) "Sürüm sayfası açılamadı." else "تعذر فتح صفحة الإصدار.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                RoyalShareUpdateAction(
                    title = when {
                        isEnglish -> "Check again"
                        isTurkish -> "Yeniden kontrol et"
                        else -> "إعادة التحقق"
                    },
                    onClick = {
                        releaseCheck = null
                        refreshCounter += 1
                    }
                )
            }
        }
        Text(
            when {
                isEnglish -> "The QR code opens the official Releases page; test drafts are excluded from update checks."
                isTurkish -> "QR kodu resmî Releases sayfasını açar; test taslakları güncelleme kontrolüne dâhil edilmez."
                else -> "رمز QR يفتح صفحة الإصدارات الرسمية؛ ولا تُحتسب النسخ التجريبية المسودة كتحديث."
            },
            color = Color(0xFF8F9BAA),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun RoyalShareUpdateAction(title: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier.clip(shape)
            .background(Color(0xFF15232B))
            .border(1.dp, Color(0x77D9B65B), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = Color(0xFFE5C36A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun installedRoyalVersionCode(context: Context): Long {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toLong()
    } catch (_: Exception) {
        1L
    }
}

private fun fetchLatestRoyalRelease(): RoyalReleaseCheck {
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL(ROYAL_PLUS_RELEASE_API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ROYAL-Plus-Launcher")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
            RoyalReleaseCheck(noPublishedRelease = true)
        } else if (responseCode !in 200..299) {
            RoyalReleaseCheck(failed = true)
        } else {
            val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            val tag = payload.optString("tag_name", "")
            val buildNumber = Regex("royalplus-(\\d+)$", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1)?.toLongOrNull()
            val releaseUrl = payload.optString("html_url", ROYAL_PLUS_LATEST_RELEASE_URL)
                .takeIf { it.startsWith("https://github.com/") }
                ?: ROYAL_PLUS_LATEST_RELEASE_URL
            RoyalReleaseCheck(
                latestBuildNumber = buildNumber,
                releaseUrl = releaseUrl
            )
        }
    } catch (_: Exception) {
        RoyalReleaseCheck(failed = true)
    } finally {
        connection?.disconnect()
    }
}

private fun createRoyalPlusReleaseQr(): Bitmap {
    val size = 420
    val matrix = MultiFormatWriter().encode(
        ROYAL_PLUS_LATEST_RELEASE_URL,
        BarcodeFormat.QR_CODE,
        size,
        size
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}
