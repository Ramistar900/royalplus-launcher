package com.royalplus.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.royal.adaptive.LocalRoyalAdaptiveProfile

internal enum class RoyalSportsGlyph(val label: String) {
    FOOTBALL("كرة القدم"),
    TODAY_MATCHES("مباريات اليوم"),
    FIXTURES("جدول المباريات"),
    LIVE("النتائج المباشرة"),
    RESULTS("نتائج المباريات"),
    TEAMS("الفرق"),
    GOAL("المرمى"),
    REFRESH("تحديث")
}

private val SportsNavy = Color(0xFF102334)
private val SportsTurquoise = Color(0xFF45D4DF)
private val SportsGold = Color(0xFFFFD36A)
private val SportsPearl = Color(0xFFF4F1E7)
private val SportsLive = Color(0xFFFF716B)

@Composable
internal fun royalSportsIconSize(base: Dp, max: Dp = 96.dp): Dp {
    val profile = LocalRoyalAdaptiveProfile.current
    val widthBoost = ((profile.windowWidthDp.value - 1280f) / 2560f)
        .coerceIn(0f, 0.15f)
    val scaled = profile.iconSize(base).value * (1f + widthBoost)
    return scaled.coerceIn(base.value, max.value).dp
}

@Composable
internal fun RoyalSportsIcon(
    icon: RoyalSportsGlyph,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    rotationDegrees: Float = 0f
) {
    Box(modifier.semantics {
        this.contentDescription = contentDescription ?: icon.label
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val edge = size.minDimension
            val scale = edge / 64f
            val offsetX = (size.width - edge) / 2f
            val offsetY = (size.height - edge) / 2f
            withTransform({
                translate(offsetX, offsetY)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawRoundRect(
                    color = SportsNavy,
                    topLeft = Offset(3f, 3f),
                    size = Size(58f, 58f),
                    cornerRadius = CornerRadius(17f, 17f)
                )
                drawCircle(
                    color = SportsTurquoise.copy(alpha = 0.075f),
                    radius = 24f,
                    center = Offset(32f, 32f)
                )
                drawRoundRect(
                    color = SportsTurquoise.copy(alpha = 0.36f),
                    topLeft = Offset(3.5f, 3.5f),
                    size = Size(57f, 57f),
                    cornerRadius = CornerRadius(16.5f, 16.5f),
                    style = Stroke(1.2f)
                )
                when (icon) {
                    RoyalSportsGlyph.FOOTBALL -> rotate(degrees = rotationDegrees, pivot = Offset(32f, 32f)) { drawSoccerBall() }
                    RoyalSportsGlyph.TODAY_MATCHES -> drawCalendar()
                    RoyalSportsGlyph.FIXTURES -> drawFixtureList()
                    RoyalSportsGlyph.LIVE -> drawLiveBoard()
                    RoyalSportsGlyph.RESULTS -> drawTrophy()
                    RoyalSportsGlyph.TEAMS -> drawTeams()
                    RoyalSportsGlyph.GOAL -> drawGoal()
                    RoyalSportsGlyph.REFRESH -> drawRefresh()
                }
            }
        }
    }
}

@Composable
internal fun RoyalRotatingFootballIcon(
    modifier: Modifier = Modifier,
    contentDescription: String = "الرياضة"
) {
    val transition = rememberInfiniteTransition(label = "royal_sports_ball_rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing)
        ),
        label = "royal_sports_ball_angle"
    )
    RoyalSportsIcon(
        icon = RoyalSportsGlyph.FOOTBALL,
        modifier = modifier,
        contentDescription = contentDescription,
        rotationDegrees = rotation
    )
}

private fun DrawScope.drawSoccerBall() {
    val center = Offset(32f, 32f)
    drawCircle(SportsPearl, radius = 22f, center = center)
    drawCircle(SportsGold, radius = 22f, center = center, style = Stroke(1.6f))

    val inner = listOf(
        Offset(32f, 19f),
        Offset(43f, 27f),
        Offset(39f, 40f),
        Offset(25f, 40f),
        Offset(21f, 27f)
    )
    val outer = listOf(
        Offset(32f, 10f),
        Offset(53f, 24f),
        Offset(46f, 51f),
        Offset(18f, 51f),
        Offset(11f, 24f)
    )
    val pentagon = Path().apply {
        moveTo(inner.first().x, inner.first().y)
        inner.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(pentagon, SportsNavy)
    inner.indices.forEach { index ->
        drawLine(
            color = SportsNavy,
            start = inner[index],
            end = outer[index],
            strokeWidth = 2.4f
        )
    }
    drawLine(SportsNavy, Offset(21f, 27f), Offset(13f, 36f), 1.8f)
    drawLine(SportsNavy, Offset(43f, 27f), Offset(51f, 36f), 1.8f)
    drawLine(SportsNavy, Offset(25f, 40f), Offset(22f, 50f), 1.8f)
    drawLine(SportsNavy, Offset(39f, 40f), Offset(42f, 50f), 1.8f)
    drawCircle(SportsTurquoise.copy(alpha = 0.9f), 2.2f, Offset(16f, 15f))
}

private fun DrawScope.drawCalendar() {
    drawRoundRect(
        SportsPearl,
        topLeft = Offset(13f, 15f),
        size = Size(38f, 36f),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(3f)
    )
    drawLine(SportsGold, Offset(13f, 25f), Offset(51f, 25f), 3f)
    drawLine(SportsTurquoise, Offset(23f, 11f), Offset(23f, 20f), 3.4f)
    drawLine(SportsTurquoise, Offset(41f, 11f), Offset(41f, 20f), 3.4f)
    drawCircle(SportsGold, 3.2f, Offset(24f, 35f))
    drawCircle(SportsTurquoise, 3.2f, Offset(39f, 35f))
    drawCircle(SportsPearl, 2f, Offset(24f, 44f))
}

private fun DrawScope.drawFixtureList() {
    drawRoundRect(
        SportsPearl,
        topLeft = Offset(17f, 12f),
        size = Size(30f, 42f),
        cornerRadius = CornerRadius(5f, 5f),
        style = Stroke(2.8f)
    )
    drawRoundRect(
        SportsGold,
        topLeft = Offset(25f, 9f),
        size = Size(14f, 8f),
        cornerRadius = CornerRadius(3f, 3f)
    )
    listOf(24f, 34f, 44f).forEachIndexed { index, y ->
        drawCircle(if (index == 1) SportsTurquoise else SportsGold, 2.3f, Offset(25f, y))
        drawLine(SportsPearl, Offset(31f, y), Offset(41f, y), 2.3f)
    }
}

private fun DrawScope.drawLiveBoard() {
    drawRoundRect(
        SportsPearl,
        topLeft = Offset(10f, 15f),
        size = Size(44f, 31f),
        cornerRadius = CornerRadius(5f, 5f),
        style = Stroke(2.8f)
    )
    drawCircle(SportsLive, 4f, Offset(20f, 25f))
    drawLine(SportsTurquoise, Offset(29f, 25f), Offset(45f, 25f), 3f)
    drawLine(SportsGold, Offset(17f, 35f), Offset(47f, 35f), 2.6f)
    drawLine(SportsPearl, Offset(23f, 51f), Offset(41f, 51f), 2.8f)
    drawLine(SportsPearl, Offset(32f, 46f), Offset(32f, 51f), 2.8f)
}

private fun DrawScope.drawTrophy() {
    val cup = Path().apply {
        moveTo(21f, 14f)
        lineTo(43f, 14f)
        lineTo(40f, 30f)
        quadraticTo(38f, 39f, 32f, 39f)
        quadraticTo(26f, 39f, 24f, 30f)
        close()
    }
    drawPath(cup, SportsGold)
    drawArc(
        SportsTurquoise,
        startAngle = 80f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(12f, 16f),
        size = Size(17f, 18f),
        style = Stroke(3f)
    )
    drawArc(
        SportsTurquoise,
        startAngle = -100f,
        sweepAngle = 200f,
        useCenter = false,
        topLeft = Offset(35f, 16f),
        size = Size(17f, 18f),
        style = Stroke(3f)
    )
    drawLine(SportsPearl, Offset(32f, 39f), Offset(32f, 47f), 3.4f)
    drawRoundRect(
        SportsTurquoise,
        topLeft = Offset(21f, 47f),
        size = Size(22f, 5f),
        cornerRadius = CornerRadius(2f, 2f)
    )
}

private fun DrawScope.drawTeams() {
    drawCircle(SportsGold, 6.5f, Offset(23f, 23f))
    drawCircle(SportsTurquoise, 5.7f, Offset(42f, 25f))
    drawArc(
        SportsGold,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(10f, 29f),
        size = Size(26f, 25f),
        style = Stroke(5.5f)
    )
    drawArc(
        SportsTurquoise,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(31f, 31f),
        size = Size(24f, 23f),
        style = Stroke(5f)
    )
    drawLine(SportsPearl.copy(alpha = 0.8f), Offset(17f, 52f), Offset(47f, 52f), 2f)
}

private fun DrawScope.drawGoal() {
    drawRoundRect(
        SportsPearl,
        topLeft = Offset(10f, 13f),
        size = Size(44f, 34f),
        cornerRadius = CornerRadius(2f, 2f),
        style = Stroke(3f)
    )
    for (x in listOf(21f, 32f, 43f)) {
        drawLine(SportsTurquoise.copy(alpha = 0.85f), Offset(x, 16f), Offset(x, 44f), 1.4f)
    }
    for (y in listOf(24f, 34f)) {
        drawLine(SportsTurquoise.copy(alpha = 0.85f), Offset(13f, y), Offset(51f, y), 1.4f)
    }
    drawCircle(SportsGold, 7f, Offset(32f, 46f))
    drawPath(
        Path().apply {
            moveTo(32f, 40f)
            lineTo(36f, 44f)
            lineTo(34f, 50f)
            lineTo(29f, 50f)
            lineTo(28f, 44f)
            close()
        },
        SportsNavy
    )
}

private fun DrawScope.drawRefresh() {
    drawArc(
        SportsTurquoise,
        startAngle = -42f,
        sweepAngle = 286f,
        useCenter = false,
        topLeft = Offset(13f, 13f),
        size = Size(38f, 38f),
        style = Stroke(width = 4f)
    )
    drawPath(
        Path().apply {
            moveTo(43f, 9f)
            lineTo(53f, 12f)
            lineTo(48f, 21f)
            close()
        },
        SportsGold
    )
    drawPath(
        Path().apply {
            moveTo(19f, 54f)
            lineTo(10f, 49f)
            lineTo(18f, 44f)
            close()
        },
        SportsGold
    )
}


@Composable
internal fun SportsPitchGlassBackground(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val shortEdge = size.minDimension
        val stroke = (shortEdge * 0.0022f).coerceIn(1.1f, 2.6f)
        val line = Color(0xFFB6FFD5).copy(alpha = 0.22f)
        val glow = Color(0xFF77E9A8).copy(alpha = 0.12f)
        val landscape = size.width >= size.height

        if (landscape) {
            val left = size.width * 0.065f
            val right = size.width * 0.935f
            val top = size.height * 0.09f
            val bottom = size.height * 0.91f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val fieldWidth = right - left
            val fieldHeight = bottom - top
            drawRoundRect(
                color = glow,
                topLeft = Offset(left, top),
                size = Size(fieldWidth, fieldHeight),
                cornerRadius = CornerRadius(stroke * 5f, stroke * 5f),
                style = Stroke(stroke * 2f)
            )
            drawLine(line, Offset(centerX, top), Offset(centerX, bottom), stroke)
            drawCircle(line, radius = fieldHeight * 0.17f, center = Offset(centerX, centerY), style = Stroke(stroke))
            drawCircle(line, radius = stroke * 1.8f, center = Offset(centerX, centerY))
            val boxWidth = fieldWidth * 0.17f
            val boxHeight = fieldHeight * 0.46f
            val goalWidth = fieldWidth * 0.055f
            val goalHeight = fieldHeight * 0.22f
            drawRect(line, Offset(left, centerY - boxHeight / 2f), Size(boxWidth, boxHeight), style = Stroke(stroke))
            drawRect(line, Offset(right - boxWidth, centerY - boxHeight / 2f), Size(boxWidth, boxHeight), style = Stroke(stroke))
            drawRect(line, Offset(left, centerY - goalHeight / 2f), Size(goalWidth, goalHeight), style = Stroke(stroke))
            drawRect(line, Offset(right - goalWidth, centerY - goalHeight / 2f), Size(goalWidth, goalHeight), style = Stroke(stroke))
            val corner = fieldHeight * 0.045f
            drawArc(line, 0f, 90f, false, Offset(left - corner, top - corner), Size(corner * 2f, corner * 2f), style = Stroke(stroke))
            drawArc(line, 90f, 90f, false, Offset(right - corner, top - corner), Size(corner * 2f, corner * 2f), style = Stroke(stroke))
            drawArc(line, 180f, 90f, false, Offset(right - corner, bottom - corner), Size(corner * 2f, corner * 2f), style = Stroke(stroke))
            drawArc(line, 270f, 90f, false, Offset(left - corner, bottom - corner), Size(corner * 2f, corner * 2f), style = Stroke(stroke))
        } else {
            val left = size.width * 0.085f
            val right = size.width * 0.915f
            val top = size.height * 0.055f
            val bottom = size.height * 0.945f
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val fieldWidth = right - left
            val fieldHeight = bottom - top
            drawRoundRect(
                color = glow,
                topLeft = Offset(left, top),
                size = Size(fieldWidth, fieldHeight),
                cornerRadius = CornerRadius(stroke * 5f, stroke * 5f),
                style = Stroke(stroke * 2f)
            )
            drawLine(line, Offset(left, centerY), Offset(right, centerY), stroke)
            drawCircle(line, radius = fieldWidth * 0.19f, center = Offset(centerX, centerY), style = Stroke(stroke))
            drawCircle(line, radius = stroke * 1.8f, center = Offset(centerX, centerY))
            val boxHeight = fieldHeight * 0.16f
            val boxWidth = fieldWidth * 0.54f
            val goalHeight = fieldHeight * 0.055f
            val goalWidth = fieldWidth * 0.23f
            drawRect(line, Offset(centerX - boxWidth / 2f, top), Size(boxWidth, boxHeight), style = Stroke(stroke))
            drawRect(line, Offset(centerX - boxWidth / 2f, bottom - boxHeight), Size(boxWidth, boxHeight), style = Stroke(stroke))
            drawRect(line, Offset(centerX - goalWidth / 2f, top), Size(goalWidth, goalHeight), style = Stroke(stroke))
            drawRect(line, Offset(centerX - goalWidth / 2f, bottom - goalHeight), Size(goalWidth, goalHeight), style = Stroke(stroke))
        }
    }
}
