package com.royalplus.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.sinjartv.rstv.AdultContentPolicy
import com.sinjartv.rstv.LocalLibraryStore
import com.sinjartv.rstv.M3uPlaylistParser
import com.sinjartv.rstv.RemotePlaylistDownloader
import com.sinjartv.rstv.RemotePlaylistMerger
import com.sinjartv.rstv.RemotePlaylistPackage
import com.sinjartv.rstv.RemotePlaylistSources
import com.sinjartv.rstv.RemoteSourceSnapshot
import com.sinjartv.rstv.RSTVChannel
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class SinjarSection { LIVE, SOURCES, FAVORITES }

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
internal fun SinjarTvScreen(language: String, onFullscreenChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val player = remember(context) { ExoPlayer.Builder(context).build() }
    val favoriteWriteMutex = remember { Mutex() }
    var section by remember { mutableStateOf(SinjarSection.LIVE) }
    var channels by remember { mutableStateOf<List<RSTVChannel>>(emptyList()) }
    var favoriteUrls by remember { mutableStateOf<Set<String>>(emptySet()) }
    var savedSourceCount by remember { mutableStateOf(0) }
    var status by remember { mutableStateOf("جارٍ استعادة مكتبة Sinjar TV...") }
    var busyPackageId by remember { mutableStateOf<String?>(null) }
    var selectedChannel by remember { mutableStateOf<RSTVChannel?>(null) }
    var isFullscreen by remember { mutableStateOf(false) }
    var videoHeight by remember { mutableStateOf(0) }

    val sourceEntries = remember {
        val douboCountries = RemotePlaylistSources.douboCountryPackages()
        val countryCodes = douboCountries.mapNotNull { source ->
            source.id.removePrefix("doubo-country-").uppercase()
                .takeIf { it.matches(Regex("[A-Z]{2}")) }
        }.distinct()
        RemotePlaylistSources.playlists +
            RemotePlaylistSources.countryPackages(countryCodes) +
            douboCountries
    }
    val packages = remember(sourceEntries) { RemotePlaylistSources.displayPackages(sourceEntries) }

    suspend fun reloadLibrary() {
        val saved = withContext(Dispatchers.IO) { LocalLibraryStore.loadRemoteSources(context) }
        channels = withContext(Dispatchers.Default) {
            RemotePlaylistMerger.merge(saved).filterNot(AdultContentPolicy::isAdult)
        }
        savedSourceCount = saved.size
        favoriteUrls = withContext(Dispatchers.IO) { LocalLibraryStore.favoriteStreams(context) }
        status = "تم تحميل ${channels.size} قناة من ${saved.size} مصدر محفوظ"
    }

    LaunchedEffect(Unit) {
        runCatching { reloadLibrary() }
            .onFailure { status = "تعذر استعادة المكتبة: ${it.message ?: "خطأ غير معروف"}" }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                status = when (playbackState) {
                    Player.STATE_BUFFERING -> "جارٍ تحميل البث..."
                    Player.STATE_READY -> "البث يعمل الآن"
                    Player.STATE_ENDED -> "انتهى البث"
                    else -> status
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                status = "تعذّر تشغيل البث: ${error.message ?: error.errorCodeName}"
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoHeight = videoSize.height
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(isFullscreen) { onFullscreenChange(isFullscreen) }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    fun playChannel(channel: RSTVChannel) {
        if (AdultContentPolicy.isAdult(channel)) {
            status = "هذه القناة مصنّفة كمحتوى مقيّد ولا تظهر ضمن البث العام"
            return
        }
        selectedChannel = channel
        videoHeight = 0
        status = "جارٍ تشغيل ${channel.name}..."
        val httpFactory = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(channel.httpHeaders)
        val mediaSource = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
            .createMediaSource(MediaItem.fromUri(channel.streamUrl))
        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
    }

    fun toggleFavorite(channel: RSTVChannel) {
        scope.launch {
            favoriteWriteMutex.withLock {
                val isNowFavorite = withContext(Dispatchers.IO) {
                    LocalLibraryStore.toggleFavorite(context, channel.streamUrl)
                }
                favoriteUrls = if (isNowFavorite) favoriteUrls + channel.streamUrl
                    else favoriteUrls - channel.streamUrl
            }
        }
    }

    fun downloadPackage(playlistPackage: RemotePlaylistPackage) {
        if (busyPackageId != null) return
        busyPackageId = playlistPackage.id
        status = "جارٍ تحديث ${playlistPackage.name}..."
        scope.launch {
            var downloaded = 0
            var importedChannels = 0
            val failures = mutableListOf<String>()
            playlistPackage.sources.distinctBy { it.playlistUrl }.forEach { source ->
                runCatching {
                    val content = withContext(Dispatchers.IO) { RemotePlaylistDownloader.download(source) }
                    val parsed = withContext(Dispatchers.Default) { M3uPlaylistParser.parse(content) }
                    require(parsed.isValidPlaylist) { "القائمة التي أعادها المصدر غير صالحة" }
                    require(parsed.channels.isNotEmpty()) { "لم يتم العثور على قنوات HTTPS صالحة" }
                    val snapshot = RemoteSourceSnapshot(
                        id = source.id,
                        name = source.name,
                        provider = source.provider,
                        attribution = source.attribution,
                        content = content
                    )
                    withContext(Dispatchers.IO) { LocalLibraryStore.saveRemoteSource(context, snapshot) }
                    parsed.channels.size
                }.onSuccess { count ->
                    downloaded++
                    importedChannels += count
                }.onFailure { error ->
                    failures += "${source.provider}: ${error.message ?: "فشل التنزيل"}"
                }
            }
            runCatching { reloadLibrary() }
                .onFailure { error -> status = "تم تنزيل بعض المصادر، لكن تعذّر تحديث المكتبة: ${error.message}" }
            if (downloaded > 0) {
                status = "تم تحديث ${downloaded} من ${playlistPackage.sources.size} مصادر؛ ${importedChannels} قناة قبل إزالة التكرار"
                if (failures.isNotEmpty()) status += " • تعذّر: ${failures.joinToString("؛ ")}"
            } else {
                status = "لم ينجح تنزيل هذه الباقة: ${failures.joinToString("؛ ")}"
            }
            busyPackageId = null
        }
    }


    val visibleChannels = when (section) {
        SinjarSection.LIVE -> channels
        SinjarSection.FAVORITES -> channels.filter { it.streamUrl in favoriteUrls }
        SinjarSection.SOURCES -> emptyList()
    }

    val gold = Color(0xFFD7B45A)
    val panel = Color(0xFF151923)
    val muted = Color(0xFFA7AAB3)
    val arabic = language != "en" && language != "tr"
    val liveLabel = if (arabic) "البث المباشر" else if (language == "tr") "Canlı yayın" else "Live TV"
    val sourcesLabel = if (arabic) "المصادر" else if (language == "tr") "Kaynaklar" else "Sources"
    val favoritesLabel = if (arabic) "المفضلة" else if (language == "tr") "Favoriler" else "Favorites"

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Color(0xFF090C13))
            .then(if (isFullscreen) Modifier else Modifier.padding(14.dp)),
        verticalArrangement = Arrangement.spacedBy(if (isFullscreen) 0.dp else 8.dp)
    ) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = rememberRoyalBitmapPainter(R.drawable.sinjar_show_logo, maxEdgePx = 512, maxPixels = 262_144L),
                    contentDescription = if (arabic) "شعار سنجار" else "Sinjar TV logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.width(186.dp).height(58.dp)
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        scope.launch {
                            runCatching { reloadLibrary() }
                                .onFailure { status = "تعذّر تحديث المكتبة" }
                        }
                    }
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = gold)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SinjarTabButton(liveLabel, section == SinjarSection.LIVE, gold) { section = SinjarSection.LIVE }
                SinjarTabButton(sourcesLabel, section == SinjarSection.SOURCES, gold) { section = SinjarSection.SOURCES }
                SinjarTabButton(favoritesLabel, section == SinjarSection.FAVORITES, gold) { section = SinjarSection.FAVORITES }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 15.dp))
                .background(panel)
        ) {
            if (section == SinjarSection.SOURCES) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        if (arabic) "مكتبة المصادر • محفوظ: $savedSourceCount" else "$sourcesLabel • Saved: $savedSourceCount",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(packages, key = { it.id }) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B202B)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(item.description, color = muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        item.sources.map { it.provider }.distinct().joinToString(" + "),
                                        color = gold, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    item.sources.mapNotNull { it.attribution }.distinct().forEach { attribution ->
                                        Text(attribution, color = muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(
                                        onClick = { downloadPackage(item) },
                                        enabled = busyPackageId == null,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = gold,
                                            contentColor = Color(0xFF111318)
                                        ),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            if (busyPackageId == item.id) "جارٍ التحديث..." else if (arabic) "تنزيل / تحديث" else "Download / Update",
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                SinjarPlayerPane(
                    modifier = Modifier.fillMaxSize(),
                    player = player,
                    selectedChannel = selectedChannel,
                    visibleChannels = visibleChannels,
                    favoriteUrls = favoriteUrls,
                    videoHeight = videoHeight,
                    status = status,
                    arabic = arabic,
                    gold = gold,
                    muted = muted,
                    favoritesLabel = favoritesLabel,
                    isFullscreen = isFullscreen,
                    onPlayChannel = ::playChannel,
                    onToggleFavorite = ::toggleFavorite,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onStopPlayback = {
                        player.stop()
                        player.clearMediaItems()
                        selectedChannel = null
                        videoHeight = 0
                    }
                )
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun SinjarPlayerPane(
    modifier: Modifier,
    player: ExoPlayer,
    selectedChannel: RSTVChannel?,
    visibleChannels: List<RSTVChannel>,
    favoriteUrls: Set<String>,
    videoHeight: Int,
    status: String,
    arabic: Boolean,
    gold: Color,
    muted: Color,
    favoritesLabel: String,
    isFullscreen: Boolean,
    onPlayChannel: (RSTVChannel) -> Unit,
    onToggleFavorite: (RSTVChannel) -> Unit,
    onToggleFullscreen: () -> Unit,
    onStopPlayback: () -> Unit
) {
    var channelMenuVisible by remember { mutableStateOf(false) }
    var favoritePromptChannel by remember { mutableStateOf<RSTVChannel?>(null) }
    var highlightedIndex by remember { mutableStateOf(0) }
    var infoChannel by remember { mutableStateOf<RSTVChannel?>(null) }
    var lastConfirmedUrl by remember { mutableStateOf<String?>(null) }
    var lastConfirmedAt by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsTimerKey by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val channelListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(infoChannel?.streamUrl) {
        if (infoChannel != null) {
            delay(3600)
            infoChannel = null
        }
    }
    LaunchedEffect(controlsTimerKey, channelMenuVisible, favoritePromptChannel) {
        controlsVisible = true
        delay(5000)
        if (!channelMenuVisible && favoritePromptChannel == null) controlsVisible = false
    }
    LaunchedEffect(visibleChannels.size) {
        highlightedIndex = visibleChannels.indexOfFirst { it.streamUrl == selectedChannel?.streamUrl }
            .let { if (it >= 0) it else 0 }
    }
    LaunchedEffect(highlightedIndex, channelMenuVisible, visibleChannels.size) {
        if (channelMenuVisible && visibleChannels.isNotEmpty()) {
            channelListState.animateScrollToItem(highlightedIndex.coerceIn(0, visibleChannels.lastIndex))
        }
    }

    val qualityLevel = when {
        videoHeight >= 2160 -> 4
        videoHeight >= 1080 -> 3
        videoHeight >= 720 -> 2
        videoHeight > 0 -> 1
        else -> 0
    }
    val qualityLabel = when (qualityLevel) {
        4 -> "4K"
        3 -> "FHD"
        2 -> "HD"
        1 -> "SD"
        else -> if (arabic) "جارٍ فحص الجودة" else "Checking quality"
    }
    val qualityColor = when (qualityLevel) {
        4 -> Color(0xFFD7B45A)
        3 -> Color(0xFF37C879)
        2 -> Color(0xFF42A5F5)
        1 -> Color(0xFFE34B4B)
        else -> muted
    }

    fun handleOk() {
        val now = SystemClock.uptimeMillis()
        val promptChannel = favoritePromptChannel
        if (promptChannel != null) {
            onToggleFavorite(promptChannel)
            favoritePromptChannel = null
            infoChannel = null
            return
        }

        if (channelMenuVisible) {
            val channel = visibleChannels.getOrNull(highlightedIndex) ?: return
            val isSecondOkForSameChannel =
                lastConfirmedUrl == channel.streamUrl && now - lastConfirmedAt in 0..650
            if (isSecondOkForSameChannel) {
                favoritePromptChannel = channel
                channelMenuVisible = false
            } else {
                onPlayChannel(channel)
                infoChannel = channel
                channelMenuVisible = false
                lastConfirmedUrl = channel.streamUrl
                lastConfirmedAt = now
            }
            return
        }

        val current = selectedChannel
        val isDoubleOkOnCurrentChannel =
            current != null && lastConfirmedUrl == current.streamUrl &&
                now - lastConfirmedAt in 0..650
        if (isDoubleOkOnCurrentChannel) {
            favoritePromptChannel = current
            lastConfirmedUrl = null
        } else {
            channelMenuVisible = visibleChannels.isNotEmpty()
            highlightedIndex = visibleChannels.indexOfFirst { it.streamUrl == current?.streamUrl }
                .let { if (it >= 0) it else 0 }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(if (isFullscreen) 0.dp else 12.dp))
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val key = event.key
                if (event.type == KeyEventType.KeyDown) {
                    controlsTimerKey++
                    when (key) {
                        androidx.compose.ui.input.key.Key.DirectionUp -> {
                            if (channelMenuVisible && visibleChannels.isNotEmpty()) {
                                highlightedIndex = (highlightedIndex - 1).coerceAtLeast(0)
                            }
                            true
                        }
                        androidx.compose.ui.input.key.Key.DirectionDown -> {
                            if (channelMenuVisible && visibleChannels.isNotEmpty()) {
                                highlightedIndex = (highlightedIndex + 1).coerceAtMost(visibleChannels.lastIndex)
                            }
                            true
                        }
                        androidx.compose.ui.input.key.Key.DirectionCenter,
                        androidx.compose.ui.input.key.Key.Enter -> {
                            handleOk()
                            true
                        }
                        androidx.compose.ui.input.key.Key.Back,
                        androidx.compose.ui.input.key.Key.Escape -> {
                            when {
                                favoritePromptChannel != null -> favoritePromptChannel = null
                                channelMenuVisible -> channelMenuVisible = false
                                isFullscreen -> onToggleFullscreen()
                            }
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    this.player = player
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.keepScreenOn = true
            }
        )

        Image(
            painter = rememberRoyalBitmapPainter(R.drawable.sinjar_show_logo, maxEdgePx = 512, maxPixels = 262_144L),
            contentDescription = if (arabic) "شعار سنجار" else "Sinjar TV",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.TopEnd)
                .padding(12.dp)
                .width(72.dp)
                .height(30.dp)
                .background(Color.Black.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
        )

        if (selectedChannel == null) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .background(Color(0xA6121822), RoundedCornerShape(14.dp))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Rounded.Tv, contentDescription = null, tint = gold, modifier = Modifier.size(42.dp))
                Text(
                    if (arabic) "اضغط OK لعرض قائمة القنوات" else "Press OK to open channels",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Transparent controls are layered over the video; they never reserve a top strip.
        if (controlsVisible) {
            IconButton(
                onClick = onToggleFullscreen,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(42.dp)
            ) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            status,
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart)
                .padding(10.dp)
                .background(Color(0x8810151D), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )

        if (channelMenuVisible) {
            Column(
                modifier = Modifier.align(Alignment.CenterStart)
                    .fillMaxHeight(0.88f)
                    .width(if (isFullscreen) 290.dp else 250.dp)
                    .padding(start = 10.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.82f), RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xD90A8884))
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                Text(
                    if (arabic) "قائمة القنوات" else "CHANNELS",
                    color = gold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)
                )
                androidx.compose.foundation.lazy.LazyColumn(
                    state = channelListState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(visibleChannels.size) { index ->
                        val channel = visibleChannels[index]
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(
                                        if (highlightedIndex == index) Color(0x6653D7CE)
                                        else Color.Transparent,
                                        RoundedCornerShape(5.dp)
                                    )
                                    .clickable {
                                        highlightedIndex = index
                                        onPlayChannel(channel)
                                        infoChannel = channel
                                        channelMenuVisible = false
                                        lastConfirmedUrl = channel.streamUrl
                                        lastConfirmedAt = SystemClock.uptimeMillis()
                                    }
                                    .padding(horizontal = 7.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    (index + 1).toString().padStart(2, '0'),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(25.dp)
                                )
                                Text(
                                    channel.name,
                                    color = gold,
                                    fontSize = 11.sp,
                                    fontWeight = if (highlightedIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Box(
                                Modifier.fillMaxWidth().height(1.dp)
                                    .background(Color.White.copy(alpha = 0.72f))
                            )
                        }
                    }
                }
                Text(
                    if (arabic) "↑ ↓ للتنقل  •  OK للاختيار" else "↑ ↓ Navigate  •  OK Select",
                    color = Color.White,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        }

        val info = infoChannel
        if (info != null && favoritePromptChannel == null) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .widthIn(min = 250.dp, max = 390.dp)
                    .border(1.dp, Color(0xFF46CFC5), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xE8172029))
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    countryFlag(info.country),
                    fontSize = 30.sp
                )
                Text(
                    info.name,
                    color = gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    info.country?.takeIf { it.isNotBlank() } ?: if (arabic) "الدولة غير محددة" else "Country not specified",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 11.sp
                )
                Text(
                    "$qualityLabel  •  ${if (arabic) "جودة البث" else "Stream quality"}",
                    color = qualityColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(5.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(qualityLevel / 4f)
                            .fillMaxHeight()
                            .background(qualityColor, RoundedCornerShape(5.dp))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("SD", color = Color(0xFFE34B4B), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("HD", color = Color(0xFF42A5F5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("FHD", color = Color(0xFF37C879), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("4K", color = Color(0xFFD7B45A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        val favoriteTarget = favoritePromptChannel
        if (favoriteTarget != null) {
            Column(
                modifier = Modifier.align(Alignment.Center)
                    .border(1.dp, gold, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xEF111923))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = gold, modifier = Modifier.size(30.dp))
                Text(
                    if (favoriteTarget.streamUrl in favoriteUrls) {
                        if (arabic) "إزالة من المفضلة؟" else "Remove from favourites?"
                    } else {
                        if (arabic) "إضافة إلى المفضلة؟" else "Add to favourites?"
                    },
                    color = gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(favoriteTarget.name, color = Color.White, fontSize = 11.sp, maxLines = 1)
                Text(
                    if (arabic) "اضغط OK للتأكيد أو رجوع للإلغاء" else "Press OK to confirm or Back to cancel",
                    color = muted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun countryFlag(country: String?): String {
    val value = country?.trim().orEmpty()
    if (value.length == 2 && value.all { it.isLetter() }) {
        return value.uppercase().map { char ->
            String(Character.toChars(0x1F1E6 + (char.code - 'A'.code)))
        }.joinToString("")
    }
    return when (value.lowercase()) {
        "iraq", "العراق" -> "🇮🇶"
        "syria", "سوريا" -> "🇸🇾"
        "turkey", "türkiye", "تركيا" -> "🇹🇷"
        "saudi arabia", "السعودية" -> "🇸🇦"
        "united arab emirates", "uae", "الإمارات" -> "🇦🇪"
        "lebanon", "لبنان" -> "🇱🇧"
        "jordan", "الأردن" -> "🇯🇴"
        "egypt", "مصر" -> "🇪🇬"
        "kuwait", "الكويت" -> "🇰🇼"
        "qatar", "قطر" -> "🇶🇦"
        "united kingdom", "uk", "المملكة المتحدة" -> "🇬🇧"
        "united states", "usa", "us", "الولايات المتحدة" -> "🇺🇸"
        else -> "🌐"
    }
}


@Composable
private fun SinjarTabButton(label: String, selected: Boolean, gold: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(36.dp).border(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) gold else Color.White.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp)
        ),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) gold else Color.LightGray)
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 10.sp)
    }
}
