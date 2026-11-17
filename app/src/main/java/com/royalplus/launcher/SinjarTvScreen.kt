package com.royalplus.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tv
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private enum class SinjarSection { LIVE, SOURCES, FAVORITES }

@OptIn(UnstableApi::class)
@Composable
internal fun SinjarTvScreen(language: String) {
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
        onDispose { player.release() }
    }

    fun playChannel(channel: RSTVChannel) {
        if (AdultContentPolicy.isAdult(channel)) {
            status = "هذه القناة مصنّفة كمحتوى مقيّد ولا تظهر ضمن البث العام"
            return
        }
        selectedChannel = channel
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
        modifier = Modifier.fillMaxSize().background(Color(0xFF090C13)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = gold, modifier = Modifier.size(31.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("SINJAR TV", color = gold, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                Text(status, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { scope.launch { runCatching { reloadLibrary() }.onFailure { status = "تعذّر تحديث المكتبة" } } }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = gold)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SinjarTabButton(liveLabel, section == SinjarSection.LIVE, gold) { section = SinjarSection.LIVE }
            SinjarTabButton(sourcesLabel, section == SinjarSection.SOURCES, gold) { section = SinjarSection.SOURCES }
            SinjarTabButton(favoritesLabel, section == SinjarSection.FAVORITES, gold) { section = SinjarSection.FAVORITES }
        }

        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                modifier = Modifier.weight(1.05f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (section == SinjarSection.SOURCES) {
                    Text(
                        if (arabic) "مكتبة المصادر • محفوظ: $savedSourceCount" else "$sourcesLabel • Saved: $savedSourceCount",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        items(packages, key = { it.id }) { item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = panel),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(item.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(item.description, color = muted, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        item.sources.map { it.provider }.distinct().joinToString(" + "),
                                        color = gold, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    item.sources.mapNotNull { it.attribution }.distinct().forEach { attribution ->
                                        Text(attribution, color = muted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    Button(
                                        onClick = { downloadPackage(item) },
                                        enabled = busyPackageId == null,
                                        colors = ButtonDefaults.buttonColors(containerColor = gold, contentColor = Color(0xFF111318)),
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(5.dp))
                                        Text(if (busyPackageId == item.id) "جارٍ التحديث..." else if (arabic) "تنزيل / تحديث" else "Download / Update", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        if (section == SinjarSection.FAVORITES) favoritesLabel else liveLabel,
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                    if (visibleChannels.isEmpty()) {
                        Text(
                            if (section == SinjarSection.FAVORITES) {
                                if (arabic) "لم تضف قنوات إلى المفضلة بعد." else "No favourite channels yet."
                            } else {
                                if (arabic) "لا توجد قنوات بعد. افتح «المصادر» لتنزيل باقة قنوات." else "No channels yet. Open Sources to download a channel package."
                            },
                            color = muted, fontSize = 12.sp
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(visibleChannels, key = { it.streamUrl }) { channel ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selectedChannel?.streamUrl == channel.streamUrl) Color(0xFF29251B) else panel
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().clickable { playChannel(channel) }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(channel.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                listOfNotNull(channel.country, channel.group).joinToString(" • ").ifBlank { "IPTV" },
                                                color = muted, fontSize = 9.sp, maxLines = 1
                                            )
                                        }
                                        IconButton(onClick = { toggleFavorite(channel) }, modifier = Modifier.size(34.dp)) {
                                            Icon(
                                                if (channel.streamUrl in favoriteUrls) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                contentDescription = favoritesLabel,
                                                tint = if (channel.streamUrl in favoriteUrls) gold else muted
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1.15f).fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    selectedChannel?.name ?: if (arabic) "مشغل البث" else "Live player",
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(245.dp)
                ) {
                    if (selectedChannel == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Rounded.Tv, contentDescription = null, tint = gold, modifier = Modifier.size(44.dp))
                                Text(
                                    if (arabic) "اختر قناة لبدء المشاهدة" else "Select a channel to start watching",
                                    color = muted, fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    this.player = player
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                                    keepScreenOn = true
                                }
                            },
                            update = { view ->
                                view.player = player
                                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                view.keepScreenOn = true
                            }
                        )
                    }
                }
                Text(
                    if (arabic) "تُحفظ المصادر والمفضلة محلياً على جهاز التلفاز. لا يعني وجود رابط في فهرس عام أن بثه مرخّص أو متاح دائماً." else "Sources and favourites are stored locally on this TV. A public playlist entry does not guarantee broadcast rights or availability.",
                    color = muted, fontSize = 9.sp
                )
                if (selectedChannel != null) {
                    TextButton(onClick = {
                        player.stop()
                        player.clearMediaItems()
                        selectedChannel = null
                    }) {
                        Text(if (arabic) "إيقاف المشاهدة" else "Stop playback", color = gold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SinjarTabButton(label: String, selected: Boolean, gold: Color, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.border(
            width = if (selected) 1.dp else 0.5.dp,
            color = if (selected) gold else Color.White.copy(alpha = 0.12f),
            shape = RoundedCornerShape(10.dp)
        ),
        colors = ButtonDefaults.textButtonColors(contentColor = if (selected) gold else Color.LightGray)
    ) {
        Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, fontSize = 11.sp)
    }
}
