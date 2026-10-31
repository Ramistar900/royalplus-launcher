package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class WeatherVisual { SUNNY, CLOUDY, OVERCAST, RAINY, FOGGY, SNOWY, STORMY, UNKNOWN }

private fun weatherVisual(weather: WeatherUiState): WeatherVisual {
    val code = weather.weatherCode
    return when {
        code == 0 || code == 1 -> WeatherVisual.SUNNY
        code == 2 -> WeatherVisual.CLOUDY
        code == 3 -> WeatherVisual.OVERCAST
        code == 45 || code == 48 -> WeatherVisual.FOGGY
        code in 51..67 || code in 80..82 -> WeatherVisual.RAINY
        code in 71..77 || code in 85..86 -> WeatherVisual.SNOWY
        code in 95..99 -> WeatherVisual.STORMY
        weather.description.contains("مشمس") || weather.description.contains("صافية") -> WeatherVisual.SUNNY
        weather.description.contains("ضباب") -> WeatherVisual.FOGGY
        weather.description.contains("مطر") || weather.description.contains("أمطار") || weather.description.contains("رذاذ") -> WeatherVisual.RAINY
        weather.description.contains("ثلج") -> WeatherVisual.SNOWY
        weather.description.contains("غائم كليا") || weather.description.contains("غائم كلياً") ||
            weather.description.contains("غائم بالكامل") || weather.description.contains("overcast") -> WeatherVisual.OVERCAST
        weather.description.contains("غائم جزئيا") || weather.description.contains("غائم جزئياً") ||
            weather.description.contains("غائم") || weather.description.contains("غيوم") -> WeatherVisual.CLOUDY
        else -> WeatherVisual.UNKNOWN
    }
}

@Composable
internal fun WeatherGlassCard(
    weather: WeatherUiState,
    currentTime: String,
    onRequestLocation: () -> Unit,
    onRefreshWeather: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = weatherVisual(weather)
    val hour = currentTime.substringBefore(":").toIntOrNull()
    val isNight = hour != null && (hour >= 19 || hour < 6)
    val transition = rememberInfiniteTransition(label = "weather-motion")
    val drift by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_500, easing = LinearEasing), RepeatMode.Reverse),
        label = "cloud-drift"
    )
    val precipitation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_250, easing = LinearEasing)),
        label = "precipitation"
    )
    val fogDrift by transition.animateFloat(
        initialValue = -0.08f,
        targetValue = 0.08f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "fog-drift"
    )

    val tint = when (visual) {
        WeatherVisual.SUNNY -> Color(0xFFB77B25)
        WeatherVisual.CLOUDY -> Color(0xFF6D91B2)
        WeatherVisual.OVERCAST -> Color(0xFF53677D)
        WeatherVisual.RAINY, WeatherVisual.STORMY -> Color(0xFF397DA7)
        WeatherVisual.FOGGY -> Color(0xFF9AAAB8)
        WeatherVisual.SNOWY -> Color(0xFF8ECBD9)
        WeatherVisual.UNKNOWN -> Color(0xFF777E9B)
    }
    val shape = RoundedCornerShape(19.dp)
    val nightOverlay = if (isNight) Color(0xD900050D) else Color.Transparent
    val nightTint = if (isNight) tint.copy(alpha = 0.15f) else tint.copy(alpha = 0.32f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(nightTint, nightOverlay.copy(alpha = if (isNight) 0.88f else 0f), Color(0xF0060A12))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            .padding(horizontal = 9.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(62.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                val cloudColor = Color(0xFFE8F3FF).copy(alpha = 0.88f)
                val cloudShade = Color(0xFFB8D0E3).copy(alpha = 0.66f)
                val sunColor = Color(0xFFFFD36B)
                val sunCenter = Offset(w * (0.74f + drift * 0.025f), h * 0.38f)
                val sunRadius = h * 0.19f

                if (visual == WeatherVisual.SUNNY || visual == WeatherVisual.CLOUDY || visual == WeatherVisual.UNKNOWN) {
                    if (isNight) {
                        val moonCenter = Offset(w * (0.73f + drift * 0.018f), h * 0.34f)
                        val moonRadius = h * 0.19f
                        drawCircle(Color(0xFFB9D8F4).copy(alpha = 0.14f), moonRadius * 1.8f, moonCenter)
                        drawCircle(Color(0xFFFFF1C4), moonRadius, moonCenter)
                        drawCircle(Color(0xFF24364E), moonRadius * 0.86f, Offset(moonCenter.x + moonRadius * 0.42f, moonCenter.y - moonRadius * 0.20f))
                        val stars = listOf(
                            Offset(0.13f, 0.20f), Offset(0.28f, 0.34f), Offset(0.43f, 0.13f),
                            Offset(0.57f, 0.27f), Offset(0.88f, 0.17f), Offset(0.91f, 0.54f),
                            Offset(0.20f, 0.60f), Offset(0.67f, 0.12f), Offset(0.38f, 0.66f)
                        )
                        stars.forEachIndexed { index, point ->
                            val radius = if (index % 3 == 0) 1.7.dp.toPx() else 1.0.dp.toPx()
                            val center = Offset(w * point.x, h * point.y)
                            drawCircle(Color(0xFFFFF4D2).copy(alpha = if (index % 2 == 0) 0.95f else 0.62f), radius, center)
                            if (index % 3 == 0) {
                                drawLine(Color(0xFFFFF4D2).copy(alpha = 0.70f), Offset(center.x - radius * 1.8f, center.y), Offset(center.x + radius * 1.8f, center.y), 0.7.dp.toPx())
                                drawLine(Color(0xFFFFF4D2).copy(alpha = 0.70f), Offset(center.x, center.y - radius * 1.8f), Offset(center.x, center.y + radius * 1.8f), 0.7.dp.toPx())
                            }
                        }
                    } else {
                        drawCircle(Color(0xFFFFD36B).copy(alpha = 0.16f), sunRadius * 2.0f, sunCenter)
                        drawCircle(Color(0xFFFFC65C).copy(alpha = 0.20f), sunRadius * 1.55f, sunCenter)
                        for (i in 0 until 12) {
                            val angle = i * (Math.PI / 6.0)
                            val start = Offset(
                                sunCenter.x + kotlin.math.cos(angle).toFloat() * sunRadius * 1.30f,
                                sunCenter.y + kotlin.math.sin(angle).toFloat() * sunRadius * 1.30f
                            )
                            val end = Offset(
                                sunCenter.x + kotlin.math.cos(angle).toFloat() * sunRadius * 1.62f,
                                sunCenter.y + kotlin.math.sin(angle).toFloat() * sunRadius * 1.62f
                            )
                            drawLine(sunColor.copy(alpha = if (visual == WeatherVisual.CLOUDY) 0.45f else 0.82f), start, end, strokeWidth = 1.4.dp.toPx())
                        }
                        drawCircle(Brush.linearGradient(listOf(Color(0xFFFFF5C4), Color(0xFFFFB938)), sunCenter, Offset(sunCenter.x + sunRadius, sunCenter.y + sunRadius)), sunRadius, sunCenter)
                        drawCircle(Color.White.copy(alpha = 0.44f), sunRadius * 0.48f, Offset(sunCenter.x - sunRadius * 0.25f, sunCenter.y - sunRadius * 0.28f))
                    }
                }

                fun cloud(cx: Float, cy: Float, scale: Float, alpha: Float) {
                    val cloudWidth = w * 0.34f * scale
                    val cloudHeight = h * 0.34f * scale
                    val left = cx - cloudWidth * 0.5f
                    val top = cy - cloudHeight * 0.38f
                    val lowerShade = if (isNight) Color(0xFF34465C) else Color(0xFF7D93A8)
                    val upperLight = if (isNight) Color(0xFFB8C9DC) else Color(0xFFF7FBFF)
                    val shadowTop = Offset(left + cloudWidth * 0.04f, top + cloudHeight * 0.44f)
                    drawOval(
                        brush = Brush.verticalGradient(listOf(lowerShade.copy(alpha = alpha * 0.94f), Color(0xFF1E2A39).copy(alpha = alpha * 0.82f))),
                        topLeft = Offset(left, top + cloudHeight * 0.38f),
                        size = Size(cloudWidth, cloudHeight * 0.70f)
                    )
                    drawCircle(Color(0xFF172434).copy(alpha = alpha * 0.18f), cloudHeight * 0.43f, Offset(left + cloudWidth * 0.30f, top + cloudHeight * 0.52f))
                    drawCircle(
                        brush = Brush.linearGradient(listOf(upperLight.copy(alpha = alpha), cloudShade.copy(alpha = alpha * 0.98f)), shadowTop, Offset(shadowTop.x + cloudWidth * 0.5f, shadowTop.y + cloudHeight)),
                        radius = cloudHeight * 0.48f,
                        center = Offset(left + cloudWidth * 0.27f, top + cloudHeight * 0.43f)
                    )
                    drawCircle(
                        brush = Brush.linearGradient(listOf(Color.White.copy(alpha = alpha), cloudShade.copy(alpha = alpha)), Offset(left + cloudWidth * 0.42f, top), Offset(left + cloudWidth * 0.65f, top + cloudHeight)),
                        radius = cloudHeight * 0.67f,
                        center = Offset(left + cloudWidth * 0.52f, top + cloudHeight * 0.22f)
                    )
                    drawCircle(
                        brush = Brush.linearGradient(listOf(upperLight.copy(alpha = alpha), lowerShade.copy(alpha = alpha * 0.92f)), Offset(left + cloudWidth * 0.72f, top), Offset(left + cloudWidth * 0.82f, top + cloudHeight)),
                        radius = cloudHeight * 0.44f,
                        center = Offset(left + cloudWidth * 0.78f, top + cloudHeight * 0.43f)
                    )
                    drawOval(
                        brush = Brush.verticalGradient(listOf(upperLight.copy(alpha = alpha), cloudShade.copy(alpha = alpha * 0.96f))),
                        topLeft = Offset(left + cloudWidth * 0.10f, top + cloudHeight * 0.43f),
                        size = Size(cloudWidth * 0.80f, cloudHeight * 0.54f)
                    )
                }

                when (visual) {
                    WeatherVisual.CLOUDY -> {
                        // Partly cloudy: keep the sun/moon visible behind layered clouds.
                        cloud(w * (0.48f + drift * 0.04f), h * 0.55f, 1.25f, 0.94f)
                        cloud(w * 0.73f, h * 0.31f, 0.62f, 0.56f)
                    }
                    WeatherVisual.OVERCAST -> {
                        // Fully overcast: no sun or moon; use a dense, shaded cloud layer.
                        cloud(w * (0.43f + drift * 0.025f), h * 0.48f, 1.42f, if (isNight) 0.78f else 0.96f)
                        cloud(w * 0.73f, h * 0.31f, 0.90f, if (isNight) 0.58f else 0.72f)
                    }
                    WeatherVisual.RAINY, WeatherVisual.STORMY -> {
                        cloud(w * (0.48f + drift * 0.035f), h * 0.43f, 1.28f, 0.96f)
                        for (i in 0 until 9) {
                            val x = w * (0.20f + i * 0.075f)
                            val y = h * (0.68f + ((i * 0.19f + precipitation) % 1f) * 0.20f)
                            drawLine(Color(0xFF8DE4FF).copy(alpha = 0.90f), Offset(x, y), Offset(x - 3.dp.toPx(), y + 6.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                        }
                        if (visual == WeatherVisual.STORMY) {
                            val bolt = Path().apply {
                                moveTo(w * 0.64f, h * 0.58f)
                                lineTo(w * 0.57f, h * 0.76f)
                                lineTo(w * 0.64f, h * 0.75f)
                                lineTo(w * 0.60f, h * 0.91f)
                                lineTo(w * 0.72f, h * 0.69f)
                                lineTo(w * 0.65f, h * 0.70f)
                                close()
                            }
                            drawPath(bolt, Color(0xFFFFD86E))
                        }
                    }
                    WeatherVisual.FOGGY -> {
                        cloud(w * 0.49f, h * 0.32f, 1.05f, 0.58f)
                        for (i in 0..3) {
                            val y = h * (0.58f + i * 0.105f)
                            val offset = fogDrift * w + (i % 2) * w * 0.06f
                            drawLine(Color(0xFFDCE7EF).copy(alpha = 0.74f - i * 0.09f), Offset(w * 0.14f + offset, y), Offset(w * 0.86f + offset, y), strokeWidth = (2.5f - i * 0.3f).dp.toPx())
                        }
                    }
                    WeatherVisual.SNOWY -> {
                        cloud(w * 0.49f, h * 0.42f, 1.18f, 0.94f)
                        for (i in 0 until 9) {
                            val x = w * (0.20f + i * 0.075f)
                            val y = h * (0.68f + ((i * 0.17f + precipitation) % 1f) * 0.20f)
                            drawCircle(Color.White.copy(alpha = 0.95f), 1.7.dp.toPx(), Offset(x, y))
                        }
                    }
                    WeatherVisual.SUNNY -> Unit
                    WeatherVisual.UNKNOWN -> cloud(w * 0.45f, h * 0.55f, 0.85f, 0.54f)
                }
            }
        }

        Text(
            text = weather.temperatureC?.let { "$it°C" } ?: if (weather.isLoading) "…" else "—°C",
            color = Color.White,
            fontSize = 23.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = when {
                weather.isLoading -> "جارٍ تحديث الطقس"
                weather.needsPermission -> "الموقع مطلوب لعرض الطقس"
                else -> weather.description
            },
            color = Color.White.copy(alpha = 0.93f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            text = weather.city,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (weather.needsPermission || (!weather.isLoading && weather.temperatureC == null)) {
            Spacer(Modifier.height(3.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(onClick = if (weather.needsPermission) onRequestLocation else onRefreshWeather)
                    .focusable()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (weather.needsPermission) Icons.Rounded.LocationOn else Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = if (weather.needsPermission) "تفعيل الموقع" else "إعادة المحاولة",
                    color = Color.White,
                    fontSize = 9.sp
                )
            }
        }
    }
}
