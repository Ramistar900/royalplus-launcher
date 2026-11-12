package com.royal.adaptive

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

enum class RoyalDeviceClass { PHONE, TABLET, LARGE_DISPLAY, TELEVISION }
enum class RoyalWindowWidthClass { COMPACT, MEDIUM, EXPANDED }
enum class RoyalWindowHeightClass { COMPACT, MEDIUM, EXPANDED }
enum class RoyalInputMode { TOUCH, REMOTE, HYBRID }

data class RoyalAdaptiveProfile(
    val deviceClass: RoyalDeviceClass,
    val widthClass: RoyalWindowWidthClass,
    val heightClass: RoyalWindowHeightClass,
    val inputMode: RoyalInputMode,
    val windowWidthDp: Dp,
    val windowHeightDp: Dp,
    val density: Float,
    val densityDpi: Int,
    val fontScale: Float,
    val reportedResolution: String,
    val resolutionClass: String,
    val isLandscape: Boolean,
    val pageHorizontalPadding: Dp,
    val pageVerticalPadding: Dp,
    val itemSpacing: Dp,
    val sectionSpacing: Dp,
    val smallIconSize: Dp,
    val standardIconSize: Dp,
    val largeIconSize: Dp,
    val buttonHeight: Dp,
    val minimumFocusTarget: Dp,
    val gridMinCellWidth: Dp,
    val contentMaxWidth: Dp,
    val appTileHeight: Dp
) {
    val isPhone: Boolean get() = deviceClass == RoyalDeviceClass.PHONE
    val isTablet: Boolean get() =
        deviceClass == RoyalDeviceClass.TABLET ||
            deviceClass == RoyalDeviceClass.LARGE_DISPLAY
    val isTelevision: Boolean get() = deviceClass == RoyalDeviceClass.TELEVISION
    // A tablet running in a narrow multi-window area must still use a compact layout.
    val prefersWideLayout: Boolean get() =
        isTelevision || widthClass != RoyalWindowWidthClass.COMPACT
    val prefersRailNavigation: Boolean get() =
        isTelevision || widthClass == RoyalWindowWidthClass.EXPANDED
    val prefersRemoteFocus: Boolean get() =
        inputMode == RoyalInputMode.REMOTE || inputMode == RoyalInputMode.HYBRID
    val prefersTouchControls: Boolean get() =
        inputMode == RoyalInputMode.TOUCH || inputMode == RoyalInputMode.HYBRID

    fun columnsFor(
        width: Dp = windowWidthDp,
        minCellWidth: Dp = gridMinCellWidth,
        minColumns: Int = 1,
        maxColumns: Int = 8,
        horizontalPadding: Dp = 0.dp
    ): Int = calculateGridColumns(
        widthDp = width.value,
        horizontalPaddingDp = horizontalPadding.value,
        minCellWidthDp = minCellWidth.value,
        minColumns = minColumns,
        maxColumns = maxColumns
    )

    fun customerProductColumns(width: Dp = windowWidthDp): Int {
        if (isTelevision) {
            return columnsFor(width, 240.dp, minColumns = 2, maxColumns = 6, horizontalPadding = 8.dp)
        }
        return when {
            width < 480.dp -> 2
            width < 840.dp -> 3
            width < 1200.dp -> 4
            width < 1600.dp -> 5
            else -> 6
        }
    }

    fun launcherAppColumns(width: Dp = windowWidthDp): Int {
        val compactPhone = isPhone && width < 480.dp
        return columnsFor(
            width,
            minCellWidth = when {
                compactPhone -> 160.dp
                isTelevision -> 140.dp
                else -> 170.dp
            },
            minColumns = if (compactPhone || !isTelevision) 2 else 3,
            maxColumns = if (isTelevision) 6 else 5,
            horizontalPadding = 8.dp
        )
    }

    fun iconSize(base: Dp): Dp {
        val scale = when {
            isTelevision -> 1.38f
            widthClass == RoyalWindowWidthClass.EXPANDED -> 1.20f
            widthClass == RoyalWindowWidthClass.MEDIUM -> 1.10f
            else -> 1f
        }
        return (base.value * scale).dp
    }

    fun controlHeight(base: Dp = buttonHeight): Dp =
        if (isTelevision) maxOf(base, 60.dp) else maxOf(base, 48.dp)

    fun boundedContentWidth(requested: Dp = contentMaxWidth): Dp =
        minOf(requested, contentMaxWidth)

    companion object {
        val Default = RoyalAdaptiveProfile(
            deviceClass = RoyalDeviceClass.PHONE,
            widthClass = RoyalWindowWidthClass.COMPACT,
            heightClass = RoyalWindowHeightClass.EXPANDED,
            inputMode = RoyalInputMode.TOUCH,
            windowWidthDp = 360.dp,
            windowHeightDp = 640.dp,
            density = 1f,
            densityDpi = 160,
            fontScale = 1f,
            reportedResolution = "Unknown",
            resolutionClass = "Unknown",
            isLandscape = false,
            pageHorizontalPadding = 16.dp,
            pageVerticalPadding = 12.dp,
            itemSpacing = 12.dp,
            sectionSpacing = 16.dp,
            smallIconSize = 20.dp,
            standardIconSize = 24.dp,
            largeIconSize = 32.dp,
            buttonHeight = 48.dp,
            minimumFocusTarget = 48.dp,
            gridMinCellWidth = 160.dp,
            contentMaxWidth = 680.dp,
            appTileHeight = 82.dp
        )
    }
}

val LocalRoyalAdaptiveProfile = staticCompositionLocalOf {
    RoyalAdaptiveProfile.Default
}

/** Wrap the Compose root once; sizing follows the real app window, not panel inches. */
@Composable
fun RoyalAdaptiveHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        val density = LocalDensity.current
        val profile = remember(
            context,
            configuration.uiMode,
            configuration.smallestScreenWidthDp,
            maxWidth,
            maxHeight,
            density.density,
            density.fontScale
        ) {
            createRoyalAdaptiveProfile(
                context = context,
                configuration = configuration,
                widthDp = maxWidth,
                heightDp = maxHeight,
                density = density.density,
                fontScale = density.fontScale
            )
        }
        CompositionLocalProvider(LocalRoyalAdaptiveProfile provides profile) {
            content()
        }
    }
}

@Composable
fun rememberRoyalAdaptiveProfile(): RoyalAdaptiveProfile =
    LocalRoyalAdaptiveProfile.current

internal fun resolveRoyalDeviceClass(
    uiModeType: Int,
    hasLeanback: Boolean,
    hasTelevision: Boolean,
    hasTouchscreen: Boolean,
    smallestScreenWidthDp: Int,
    windowWidthDp: Float
): RoyalDeviceClass {
    val tvSignal =
        uiModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            hasLeanback ||
            hasTelevision ||
            (!hasTouchscreen && windowWidthDp >= 720f)
    return when {
        tvSignal -> RoyalDeviceClass.TELEVISION
        smallestScreenWidthDp >= 840 -> RoyalDeviceClass.LARGE_DISPLAY
        smallestScreenWidthDp >= 600 -> RoyalDeviceClass.TABLET
        else -> RoyalDeviceClass.PHONE
    }
}

internal fun resolveRoyalWidthClass(widthDp: Float): RoyalWindowWidthClass =
    when {
        widthDp < 600f -> RoyalWindowWidthClass.COMPACT
        widthDp < 840f -> RoyalWindowWidthClass.MEDIUM
        else -> RoyalWindowWidthClass.EXPANDED
    }

internal fun calculateGridColumns(
    widthDp: Float,
    horizontalPaddingDp: Float,
    minCellWidthDp: Float,
    minColumns: Int,
    maxColumns: Int
): Int {
    val safeCell = minCellWidthDp.coerceAtLeast(1f)
    val safeMin = minColumns.coerceAtLeast(1)
    val safeMax = maxColumns.coerceAtLeast(safeMin)
    val available = (widthDp - 2f * horizontalPaddingDp).coerceAtLeast(safeCell)
    return floor(available / safeCell).toInt().coerceIn(safeMin, safeMax)
}

private fun createRoyalAdaptiveProfile(
    context: Context,
    configuration: Configuration,
    widthDp: Dp,
    heightDp: Dp,
    density: Float,
    fontScale: Float
): RoyalAdaptiveProfile {
    val safeWidth = widthDp.value.takeIf { it.isFinite() && it > 0f }
        ?: configuration.screenWidthDp.toFloat().coerceAtLeast(1f)
    val safeHeight = heightDp.value.takeIf { it.isFinite() && it > 0f }
        ?: configuration.screenHeightDp.toFloat().coerceAtLeast(1f)

    val packageManager = context.packageManager
    val hasLeanback = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    val hasTelevision = packageManager.hasSystemFeature("android.hardware.type.television")
    val hasTouchscreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    val uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    val deviceClass = resolveRoyalDeviceClass(
        uiModeType,
        hasLeanback,
        hasTelevision,
        hasTouchscreen,
        configuration.smallestScreenWidthDp,
        safeWidth
    )
    val widthClass = resolveRoyalWidthClass(safeWidth)
    val heightClass = when {
        safeHeight < 480f -> RoyalWindowHeightClass.COMPACT
        safeHeight < 800f -> RoyalWindowHeightClass.MEDIUM
        else -> RoyalWindowHeightClass.EXPANDED
    }
    val inputMode = when {
        deviceClass == RoyalDeviceClass.TELEVISION && hasTouchscreen -> RoyalInputMode.HYBRID
        deviceClass == RoyalDeviceClass.TELEVISION || !hasTouchscreen -> RoyalInputMode.REMOTE
        else -> RoyalInputMode.TOUCH
    }

    val horizontalPadding = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 40.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 32.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 24.dp
        else -> 16.dp
    }
    val verticalPadding = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 24.dp
        heightClass == RoyalWindowHeightClass.COMPACT -> 8.dp
        heightClass == RoyalWindowHeightClass.MEDIUM -> 12.dp
        else -> 16.dp
    }
    val itemSpacing = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 20.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 18.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 16.dp
        else -> 12.dp
    }
    val sectionSpacing = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 28.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 24.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 20.dp
        else -> 16.dp
    }
    val smallIcon = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 28.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 26.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 24.dp
        else -> 20.dp
    }
    val standardIcon = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 40.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 32.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 28.dp
        else -> 24.dp
    }
    val largeIcon = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 56.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 44.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 40.dp
        else -> 32.dp
    }
    val buttonHeight = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 60.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 54.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 52.dp
        else -> 48.dp
    }
    val focusTarget = if (deviceClass == RoyalDeviceClass.TELEVISION) 64.dp else 48.dp
    val minCell = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 240.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 200.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 184.dp
        else -> 160.dp
    }
    val contentMax = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 1440.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 1280.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 1100.dp
        else -> 680.dp
    }
    val appTileHeight = when {
        deviceClass == RoyalDeviceClass.TELEVISION -> 136.dp
        widthClass == RoyalWindowWidthClass.EXPANDED -> 124.dp
        widthClass == RoyalWindowWidthClass.MEDIUM -> 116.dp
        else -> 108.dp
    }

    val metrics = context.resources.displayMetrics
    val widthPx = metrics.widthPixels
    val heightPx = metrics.heightPixels
    val longestEdge = max(widthPx, heightPx)
    val resolutionClass = when {
        longestEdge <= 0 -> "Unknown"
        longestEdge < 1280 -> "SD"
        longestEdge < 1920 -> "HD"
        longestEdge < 2560 -> "Full HD"
        longestEdge < 3840 -> "QHD"
        longestEdge < 7680 -> "4K"
        else -> "8K"
    }
    val resolution = if (widthPx > 0 && heightPx > 0) {
        widthPx.toString() + " × " + heightPx.toString()
    } else {
        "Unknown"
    }

    return RoyalAdaptiveProfile(
        deviceClass = deviceClass,
        widthClass = widthClass,
        heightClass = heightClass,
        inputMode = inputMode,
        windowWidthDp = safeWidth.dp,
        windowHeightDp = safeHeight.dp,
        density = density.coerceAtLeast(0.1f),
        densityDpi = metrics.densityDpi,
        fontScale = fontScale.coerceAtLeast(0.1f),
        reportedResolution = resolution,
        resolutionClass = resolutionClass,
        isLandscape = safeWidth >= safeHeight,
        pageHorizontalPadding = horizontalPadding,
        pageVerticalPadding = verticalPadding,
        itemSpacing = itemSpacing,
        sectionSpacing = sectionSpacing,
        smallIconSize = smallIcon,
        standardIconSize = standardIcon,
        largeIconSize = largeIcon,
        buttonHeight = buttonHeight,
        minimumFocusTarget = focusTarget,
        gridMinCellWidth = minCell,
        contentMaxWidth = contentMax,
        appTileHeight = appTileHeight
    )
}
