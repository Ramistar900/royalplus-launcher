package com.royalplus.launcher

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun QuranReaderScreen(
    language: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("royalplus_muslim_preferences", android.content.Context.MODE_PRIVATE)
    }
    var surahs by remember { mutableStateOf<List<QuranSurah>>(emptyList()) }
    var loadError by remember { mutableStateOf(false) }
    var selectedSurah by remember { mutableStateOf<QuranSurah?>(null) }
    var query by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var selectedReader by remember { mutableStateOf(preferences.getString("quran_reader_key", "afasy") ?: "afasy") }
    var selectedWallpaper by remember { mutableStateOf(preferences.getString("quran_wallpaper_key", "color") ?: "color") }
    var audioLoading by remember { mutableStateOf(false) }
    var audioReady by remember { mutableStateOf(false) }
    var audioPlaying by remember { mutableStateOf(false) }
    var audioMessage by remember { mutableStateOf("") }
    var currentAudioVerse by remember { mutableStateOf(0) }
    var audioMuted by remember { mutableStateOf(false) }
    var audioDownloadInProgress by remember { mutableStateOf(false) }
    var downloadAllInProgress by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf("") }
    var audioLibraryOpen by remember { mutableStateOf(false) }
    var mossadSelectionsOpen by remember { mutableStateOf(false) }
    var downloadedAudioSurahs by remember(selectedReader) {
        mutableStateOf(QuranAudioRepository.downloadedSurahNumbers(context.applicationContext, selectedReader))
    }
    val screenScope = androidx.compose.runtime.rememberCoroutineScope()
    var activeAudioSurah by remember { mutableStateOf(0) }
    var activeTimings by remember { mutableStateOf<List<QuranVerseTiming>>(emptyList()) }
    val audioPlayer = remember { MediaPlayer() }
    val lastReadNumber = preferences.getInt("quran_last_surah", 1)
    val lastReadVerse = preferences.getInt("quran_last_verse", 1)
    var bookmarkedSurah by remember { mutableStateOf(preferences.getInt("quran_bookmark_surah", 0)) }
    var bookmarkedVerse by remember { mutableStateOf(preferences.getInt("quran_bookmark_verse", 0)) }
    val isArabic = language == "ar"
    val isTurkish = language == "tr"

    DisposableEffect(audioPlayer) {
        onDispose {
            runCatching { if (audioPlayer.isPlaying) audioPlayer.stop() }
            runCatching { audioPlayer.reset() }
            runCatching { audioPlayer.release() }
        }
    }

    LaunchedEffect(selectedSurah?.number, selectedReader) {
        runCatching { if (audioPlayer.isPlaying) audioPlayer.stop() }
        runCatching { audioPlayer.reset() }
        audioLoading = false
        audioReady = false
        audioPlaying = false
        audioMessage = ""
        currentAudioVerse = 0
        activeAudioSurah = 0
        activeTimings = emptyList()
    }

    LaunchedEffect(Unit) {
        runCatching {
            withContext(Dispatchers.IO) { QuranTextRepository.load(context.applicationContext) }
        }.onSuccess {
            surahs = it
        }.onFailure {
            loadError = true
        }
    }

    BackHandler {
        if (settingsOpen) {
            settingsOpen = false
            return@BackHandler
        }
        if (selectedSurah != null) {
            selectedSurah = null
        } else {
            onClose()
        }
    }

    val cardBackground = Color(0xFF121C28)
    val quranFont = remember { FontFamily(Font(R.font.amiri_regular)) }

    val quranWallpaperRes = when (selectedWallpaper) {
        "nature" -> R.drawable.royal_nature_wallpaper
        "forest" -> R.drawable.royal_wallpaper_forest_04
        "green" -> R.drawable.royal_wallpaper_grreen_07
        "lake" -> R.drawable.royal_wallpaper_lake_03
        "mountains" -> R.drawable.royal_wallpaper_mountains_02
        "sunset" -> R.drawable.royal_wallpaper_sunset_05
        "waterfall" -> R.drawable.royal_wallpaper_waterfall_06
        else -> R.drawable.royal_color_wallpaper
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = rememberRoyalBitmapPainter(quranWallpaperRes, maxEdgePx = 2048),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Column(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Color(0xFF1C2937))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.6f), CircleShape)
                    .clickable {
                        if (selectedSurah != null) selectedSurah = null else onClose()
                    }.focusable(),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = RoyalColors.Gold)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when { isArabic -> "القرآن الكريم"; isTurkish -> "Kur'an-ı Kerim"; else -> "The Holy Quran" },
                    color = RoyalColors.Gold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when { isArabic -> "النص العثماني • يعمل دون إنترنت"; isTurkish -> "Osmanlı yazımı • çevrimdışı"; else -> "Uthmani text • works offline" },
                    color = RoyalColors.Muted,
                    fontSize = 10.sp
                )
            }
            Text(
                "114",
                color = RoyalColors.Pearl,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Color(0xFF1C2937).copy(alpha = 0.82f))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.6f), CircleShape)
                    .clickable { settingsOpen = true }.focusable(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = if (isArabic) "إعدادات القرآن" else "Quran settings",
                    tint = RoyalColors.Gold
                )
            }
        }

        if (surahs.isEmpty() && !loadError) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    when { isArabic -> "جارٍ تحميل المصحف…"; isTurkish -> "Kur'an yükleniyor…"; else -> "Loading Quran…" },
                    color = RoyalColors.Gold,
                    fontSize = 16.sp
                )
            }
        } else if (loadError) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        isArabic -> "تعذر فتح نص القرآن. أعد بناء التطبيق مع تحميل ملف النص."
                        isTurkish -> "Kur'an metni açılamadı. Metin dosyasını içeren derlemeyi yeniden yükleyin."
                        else -> "Quran text could not be opened. Rebuild with the text asset included."
                    },
                    color = Color(0xFFFFC5B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (selectedSurah == null) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0x9A16A6A2))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .clickable { audioLibraryOpen = true }
                    .focusable()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("مكتبتي الصوتية", color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("السور المنزّلة للقارئ المحدد فقط", color = RoyalColors.Pearl, fontSize = 10.sp)
                }
                Text(
                    "${downloadedAudioSurahs.size} / 114",
                    color = RoyalColors.Pearl,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF183B3C))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .clickable { mossadSelectionsOpen = true }
                    .focusable()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("مختارات عبد الرحمن مسعد", color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("17 تلاوة مختارة • تنزيل للاستماع دون إنترنت", color = RoyalColors.Pearl, fontSize = 10.sp)
                }
                Text("استعراض  ›", color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = RoyalColors.Gold) },
                placeholder = {
                    Text(
                        when { isArabic -> "ابحث باسم السورة أو بآية"; isTurkish -> "Sure veya ayet ara"; else -> "Search surah or verse" },
                        color = RoyalColors.Muted,
                        fontSize = 12.sp
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = RoyalColors.Pearl,
                    unfocusedTextColor = RoyalColors.Pearl,
                    focusedBorderColor = RoyalColors.Gold,
                    unfocusedBorderColor = RoyalColors.Gold.copy(alpha = 0.35f),
                    cursorColor = RoyalColors.Gold
                )
            )

            val normalizedQuery = remember(query) { QuranTextRepository.normalizeForSearch(query) }
            val filteredSurahs = remember(surahs, normalizedQuery) {
                if (normalizedQuery.isBlank()) surahs else surahs.filter { surah ->
                    QuranTextRepository.normalizeForSearch(surah.name).contains(normalizedQuery) ||
                        surah.verses.any { verse ->
                            QuranTextRepository.normalizeForSearch(verse.text).contains(normalizedQuery)
                        }
                }
            }

            if (bookmarkedSurah in 1..114 && query.isBlank()) {
                val bookmarkedChapter = surahs.firstOrNull { it.number == bookmarkedSurah }
                if (bookmarkedChapter != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF182438))
                            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .clickable {
                                selectedSurah = bookmarkedChapter
                                preferences.edit().putInt("quran_last_surah", bookmarkedChapter.number)
                                    .putInt("quran_last_verse", bookmarkedVerse.coerceAtLeast(1)).apply()
                            }
                            .focusable()
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when { isArabic -> "العلامة المرجعية"; isTurkish -> "Yer imi"; else -> "Bookmark" },
                                color = RoyalColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${bookmarkedChapter.name} • ${if (isArabic) "الآية" else if (isTurkish) "Ayet" else "Verse"} $bookmarkedVerse",
                                color = RoyalColors.Pearl, fontSize = 14.sp
                            )
                        }
                        Text("★", color = RoyalColors.Gold, fontSize = 20.sp)
                    }
                }
            }

            if (lastReadNumber in 1..114 && query.isBlank()) {
                val lastRead = surahs.firstOrNull { it.number == lastReadNumber }
                if (lastRead != null) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(cardBackground)
                            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .clickable { selectedSurah = lastRead }
                            .focusable()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when { isArabic -> "متابعة آخر قراءة"; isTurkish -> "Son okumaya devam et"; else -> "Continue reading" },
                                color = RoyalColors.Gold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(lastRead.name, color = RoyalColors.Pearl, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("›", color = RoyalColors.Gold, fontSize = 24.sp)
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredSurahs, key = { it.number }) { surah ->
                    val isLastRead = surah.number == lastReadNumber
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(cardBackground)
                            .border(
                                1.dp,
                                if (isLastRead) RoyalColors.Gold.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                selectedSurah = surah
                                val editor = preferences.edit().putInt("quran_last_surah", surah.number)
                                if (surah.number != lastReadNumber) editor.putInt("quran_last_verse", 1)
                                editor.apply()
                            }
                            .focusable()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape)
                                .background(Color(0xFF253345)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(surah.number.toString(), color = RoyalColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(surah.name, color = RoyalColors.Pearl, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${surah.numberedVerseCount} • " + when {
                                    surah.revelationType.contains("مكية") -> if (isArabic) "مكية" else if (isTurkish) "Mekki" else "Meccan"
                                    surah.revelationType.contains("مدنية") -> if (isArabic) "مدنية" else if (isTurkish) "Medeni" else "Medinan"
                                    else -> surah.revelationType
                                },
                                color = RoyalColors.Muted,
                                fontSize = 10.sp
                            )
                        }
                        Text("›", color = RoyalColors.Gold, fontSize = 24.sp)
                    }
                }
                if (filteredSurahs.isEmpty()) {
                    item {
                        Text(
                            when { isArabic -> "لا توجد نتائج"; isTurkish -> "Sonuç bulunamadı"; else -> "No results" },
                            color = RoyalColors.Muted,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            val surah = selectedSurah!!
            val verseListState = rememberLazyListState()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var savedBookmarkForSurah by remember(surah.number) {
                mutableStateOf(
                    preferences.getInt("quran_bookmark_verse", 0)
                        .takeIf { preferences.getInt("quran_bookmark_surah", 0) == surah.number } ?: 0
                )
            }
            val currentVisibleVerse = remember(surah.number, verseListState.firstVisibleItemIndex) {
                surah.verses.getOrNull(verseListState.firstVisibleItemIndex)
                    ?.takeIf { it.number > 0 }?.number
                    ?: surah.verses.drop(verseListState.firstVisibleItemIndex).firstOrNull { it.number > 0 }?.number
                    ?: 1
            }
            val currentPage = remember(surah.number, currentVisibleVerse) {
                QuranTextRepository.pageNumber(context.applicationContext, surah.number, currentVisibleVerse)
            }
            LaunchedEffect(surah.number) {
                val initialIndex = if (lastReadNumber == surah.number) {
                    surah.verses.indexOfFirst { it.number == lastReadVerse }.coerceAtLeast(0)
                } else 0
                verseListState.scrollToItem(initialIndex)
            }
            LaunchedEffect(surah.number, verseListState) {
                snapshotFlow { verseListState.firstVisibleItemIndex }
                    .distinctUntilChanged()
                    .collect { index ->
                        val verse = surah.verses.getOrNull(index)
                            ?.takeIf { it.number > 0 }
                            ?: surah.verses.drop(index).firstOrNull { it.number > 0 }
                        if (verse != null) {
                            preferences.edit()
                                .putInt("quran_last_surah", surah.number)
                                .putInt("quran_last_verse", verse.number)
                                .apply()
                        }
                    }
            }
            Text(
                surah.name,
                modifier = Modifier.fillMaxWidth(),
                color = RoyalColors.Gold,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = quranFont,
                textAlign = TextAlign.Center
            )
            Text(
                when { isArabic -> "${surah.numberedVerseCount} آية • النص العثماني"; isTurkish -> "${surah.numberedVerseCount} ayet • Osmanlı yazımı"; else -> "${surah.numberedVerseCount} verses • Uthmani script" },
                modifier = Modifier.fillMaxWidth(),
                color = RoyalColors.Muted,
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuranReaderAction(
                    label = when { isArabic -> "حفظ موضع القراءة"; isTurkish -> "Okuma yerini kaydet"; else -> "Bookmark position" },
                    modifier = Modifier.weight(1f),
                    selected = savedBookmarkForSurah == currentVisibleVerse
                ) {
                    bookmarkedSurah = surah.number
                    bookmarkedVerse = currentVisibleVerse
                    savedBookmarkForSurah = currentVisibleVerse
                    preferences.edit()
                        .putInt("quran_bookmark_surah", surah.number)
                        .putInt("quran_bookmark_verse", currentVisibleVerse)
                        .apply()
                }
                QuranReaderAction(
                    label = when { isArabic -> "اذهب للعلامة"; isTurkish -> "Yer imine git"; else -> "Go to bookmark" },
                    modifier = Modifier.weight(1f),
                    selected = false,
                    enabled = savedBookmarkForSurah > 0
                ) {
                    val target = surah.verses.indexOfFirst { it.number == savedBookmarkForSurah }
                    if (target >= 0) scope.launch { verseListState.animateScrollToItem(target) }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFF121C28).copy(alpha = 0.94f))
                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.55f), RoundedCornerShape(13.dp))
                    .padding(9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (audioPlaying) Color(0xFF7A2424) else Color(0xFF253345))
                        .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), CircleShape)
                        .clickable(enabled = !audioLoading && !audioDownloadInProgress && downloadedAudioSurahs.contains(surah.number)) {
                            if (audioReady) {
                                if (audioPlaying) {
                                    runCatching { audioPlayer.pause() }
                                    audioPlaying = false
                                    currentAudioVerse = 0
                                } else {
                                    runCatching { audioPlayer.start() }.onSuccess { audioPlaying = true }
                                }
                            } else {
                                audioLoading = true
                                audioMessage = "جارٍ فتح التلاوة المحمّلة…"
                                scope.launch {
                                    val track = withContext(Dispatchers.IO) {
                                        QuranAudioRepository.loadDownloadedTrack(
                                            context.applicationContext,
                                            selectedReader,
                                            surah.number
                                        )
                                    }
                                    if (track?.localFile == null) {
                                        audioLoading = false
                                        downloadedAudioSurahs = withContext(Dispatchers.IO) {
                                            QuranAudioRepository.downloadedSurahNumbers(context.applicationContext, selectedReader)
                                        }
                                        audioMessage = "نزّل هذه السورة أولاً لتشغيلها دون إنترنت"
                                    } else {
                                        activeAudioSurah = surah.number
                                        activeTimings = track.timings
                                        audioPlayer.reset()
                                        audioPlayer.setAudioAttributes(
                                            AudioAttributes.Builder()
                                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                                .build()
                                        )
                                        audioPlayer.setVolume(if (audioMuted) 0f else 1f, if (audioMuted) 0f else 1f)
                                        audioPlayer.setOnPreparedListener { prepared ->
                                            audioLoading = false
                                            audioReady = true
                                            audioPlaying = true
                                            prepared.start()
                                        }
                                        audioPlayer.setOnCompletionListener {
                                            audioPlaying = false
                                            audioReady = false
                                            currentAudioVerse = 0
                                        }
                                        audioPlayer.setOnErrorListener { _, _, _ ->
                                            audioLoading = false
                                            audioReady = false
                                            audioPlaying = false
                                            currentAudioVerse = 0
                                            audioMessage = "تعذر تشغيل ملف التلاوة المحمّل"
                                            true
                                        }
                                        audioMessage = if (track.timingAvailable)
                                            "تعمل دون إنترنت • تلوين الآيات متاح"
                                        else "تعمل دون إنترنت • لا تتوفر توقيتات مطابقة للتلوين"
                                        runCatching {
                                            audioPlayer.setDataSource(track.localFile.absolutePath)
                                            audioPlayer.prepareAsync()
                                        }.onFailure {
                                            audioLoading = false
                                            audioReady = false
                                            audioPlaying = false
                                            audioMessage = "تعذر فتح ملف التلاوة المحمّل"
                                        }
                                    }
                                }
                            }
                        }.focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when { audioLoading -> "…"; audioPlaying -> "Ⅱ"; audioReady -> "▶"; else -> "▶" },
                        color = RoyalColors.Gold, fontSize = 19.sp, fontWeight = FontWeight.Bold
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text("مشغل التلاوة", color = RoyalColors.Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        audioMessage.ifBlank {
                            if (surah.number in downloadedAudioSurahs) {
                                "القارئ: " + when (selectedReader) {
                                    "maher" -> "ماهر المعيقلي"
                                    "sudais" -> "عبد الرحمن السديس"
                                    "mossad" -> "عبد الرحمن مسعد • تلاوات مختارة"
                                    else -> "مشاري العفاسي"
                                }
                            } else "هذه السورة غير محمّلة لهذا القارئ"
                        },
                        color = RoyalColors.Pearl, fontSize = 10.sp, maxLines = 2
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Color(0xFF183E43))
                        .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), CircleShape)
                        .clickable(enabled = !audioDownloadInProgress && surah.number !in downloadedAudioSurahs) {
                            audioDownloadInProgress = true
                            audioMessage = "جارٍ تنزيل السورة وحفظها على الجهاز…"
                            val downloadReader = selectedReader
                            scope.launch {
                                val track = QuranAudioRepository.downloadTrack(
                                    context.applicationContext,
                                    downloadReader,
                                    surah.number
                                )
                                if (selectedReader == downloadReader && track?.localFile != null) {
                                    downloadedAudioSurahs = downloadedAudioSurahs + surah.number
                                    activeTimings = track.timings
                                    audioMessage = "اكتمل التنزيل • أصبحت السورة متاحة دون إنترنت"
                                } else if (selectedReader == downloadReader) {
                                    audioMessage = if (downloadReader == "mossad")
                                        "تلاوات عبد الرحمن مسعد المختارة لم تُربط بمصادر تنزيل موثوقة بعد"
                                    else "تعذر تنزيل هذه السورة من مصدر القارئ"
                                }
                                audioDownloadInProgress = false
                            }
                        }.focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        when {
                            audioDownloadInProgress -> "…"
                            surah.number in downloadedAudioSurahs -> "✓"
                            else -> "↓"
                        },
                        color = RoyalColors.Gold,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    Modifier.size(38.dp).clip(CircleShape)
                        .background(Color(0xFF183E43))
                        .border(1.dp, RoyalColors.Gold.copy(alpha = 0.65f), CircleShape)
                        .clickable {
                            val shouldMute = !audioMuted
                            audioMuted = shouldMute
                            runCatching {
                                val volume = if (shouldMute) 0f else 1f
                                audioPlayer.setVolume(volume, volume)
                            }
                        }.focusable(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (audioMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = if (audioMuted) "إلغاء كتم الصوت" else "كتم الصوت",
                        tint = RoyalColors.Gold,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    "■", color = RoyalColors.Gold, fontSize = 17.sp,
                    modifier = Modifier.clip(CircleShape).clickable {
                        runCatching { if (audioPlayer.isPlaying) audioPlayer.stop() }
                        runCatching { audioPlayer.reset() }
                        audioReady = false
                        audioPlaying = false
                        audioLoading = false
                        currentAudioVerse = 0
                        audioMessage = "تم إيقاف التلاوة"
                    }.padding(8.dp).focusable()
                )
            }

            LaunchedEffect(audioPlaying, activeTimings, surah.number) {
                while (audioPlaying) {
                    val position = runCatching { audioPlayer.currentPosition }.getOrDefault(-1)
                    val timedVerse = if (activeAudioSurah == surah.number && position >= 0) {
                        activeTimings.firstOrNull { position >= it.startMs && position < it.endMs }?.ayah ?: 0
                    } else 0
                    if (timedVerse != currentAudioVerse) {
                        currentAudioVerse = timedVerse
                        if (timedVerse > 0) {
                            val targetIndex = surah.verses.indexOfFirst { it.number == timedVerse }
                            if (targetIndex >= 0) verseListState.animateScrollToItem(targetIndex)
                        }
                    }
                    delay(150L)
                }
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xB31FA9A3))
                    .border(1.2.dp, RoyalColors.Gold.copy(alpha = 0.88f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        state = verseListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(surah.verses, key = { "${surah.number}-${it.number}" }) { verse ->
                            if (verse.number == 0) {
                                Text(
                                    verse.text,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).focusable(),
                                    color = RoyalColors.Gold,
                                    fontSize = 25.sp,
                                    lineHeight = 43.sp,
                                    fontFamily = quranFont,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (verse.number == savedBookmarkForSurah) Color(0x332A2518)
                                            else Color.Transparent
                                        )
                                        .border(
                                            if (verse.number == savedBookmarkForSurah) 1.dp else 0.dp,
                                            if (verse.number == savedBookmarkForSurah) RoyalColors.Gold.copy(alpha = 0.8f) else Color.Transparent,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 10.dp)
                                        .focusable(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        verse.text,
                                        modifier = Modifier.weight(1f),
                                        color = if (verse.number == currentAudioVerse) Color(0xFFFF3B30) else RoyalColors.Pearl,
                                        fontSize = 24.sp,
                                        lineHeight = 43.sp,
                                        fontFamily = quranFont,
                                        textAlign = TextAlign.Right
                                    )
                                    QuranAyahFlowerNumber(verse.number)
                                }
                            }
                        }
                    }
                    Box(
                        Modifier.align(Alignment.CenterHorizontally)
                            .padding(top = 5.dp, bottom = 1.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color(0x6639C7BD))
                            .border(1.dp, RoyalColors.Gold.copy(alpha = 0.8f), RoundedCornerShape(50))
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "الصفحة $currentPage",
                            color = RoyalColors.Gold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    "❧",
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 3.dp, top = 0.dp),
                    color = RoyalColors.Gold.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
                Text(
                    "❧",
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 0.dp),
                    color = RoyalColors.Gold.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
                Text(
                    "❧",
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 3.dp, bottom = 0.dp),
                    color = RoyalColors.Gold.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
                Text(
                    "❧",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 3.dp, bottom = 0.dp),
                    color = RoyalColors.Gold.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
            }
        }

        Spacer(Modifier.height(2.dp))
        Text(
            "نص القرآن: مشروع تنزيل Tanzil • CC BY 3.0",
            modifier = Modifier.fillMaxWidth(),
            color = RoyalColors.Muted,
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
        }
    }

    if (settingsOpen) {
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            containerColor = Color(0xF0141D29),
            title = {
                Text("إعدادات القرآن الكريم", color = RoyalColors.Gold, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(
                    modifier = Modifier.height(420.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item { Text("اختيار القارئ", color = RoyalColors.Gold, fontWeight = FontWeight.Bold) }
                    items(listOf(
                        "afasy" to "مشاري العفاسي",
                        "maher" to "ماهر المعيقلي",
                        "sudais" to "عبد الرحمن السديس",
                        "mossad" to "عبد الرحمن مسعد • تلاوات مختارة"
                    ), key = { it.first }) { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                .background(if (selectedReader == key) RoyalColors.Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    selectedReader = key
                                    downloadAllInProgress = false
                                    preferences.edit().putString("quran_reader_key", key).apply()
                                    screenScope.launch {
                                        downloadedAudioSurahs = withContext(Dispatchers.IO) {
                                            QuranAudioRepository.downloadedSurahNumbers(context.applicationContext, key)
                                        }
                                    }
                                }.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = RoyalColors.Pearl, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            if (selectedReader == key) Text("✓", color = RoyalColors.Gold)
                        }
                    }
                    item {
                        Spacer(Modifier.height(5.dp))
                        QuranReaderAction(
                            label = if (downloadAllInProgress) "إيقاف تنزيل السور" else "تنزيل جميع سور هذا القارئ",
                            modifier = Modifier.fillMaxWidth(),
                            selected = downloadAllInProgress,
                            enabled = selectedReader != "mossad" || downloadAllInProgress
                        ) {
                            if (downloadAllInProgress) {
                                downloadAllInProgress = false
                                downloadProgress = "تم إيقاف التنزيل بناءً على طلبك"
                            } else {
                                val batchReader = selectedReader
                                downloadAllInProgress = true
                                downloadProgress = "يبدأ تنزيل السور المتاحة لهذا القارئ…"
                                screenScope.launch {
                                    var completed = downloadedAudioSurahs.size
                                    for (chapter in surahs) {
                                        if (!downloadAllInProgress || selectedReader != batchReader) break
                                        if (chapter.number in downloadedAudioSurahs) continue
                                        val result = QuranAudioRepository.downloadTrack(
                                            context.applicationContext,
                                            batchReader,
                                            chapter.number
                                        )
                                        if (selectedReader == batchReader && result?.localFile != null) {
                                            downloadedAudioSurahs = downloadedAudioSurahs + chapter.number
                                            completed += 1
                                        }
                                        downloadProgress = "تم تنزيل $completed من 114 سورة"
                                    }
                                    downloadAllInProgress = false
                                    if (selectedReader == batchReader && downloadProgress != "تم إيقاف التنزيل بناءً على طلبك") {
                                        downloadProgress = "انتهت محاولة التنزيل • المتاح الآن $completed من 114"
                                    }
                                }
                            }
                        }
                    }
                    if (downloadProgress.isNotBlank()) {
                        item {
                            Text(
                                downloadProgress,
                                color = RoyalColors.Pearl,
                                fontSize = 10.sp
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(5.dp))
                        Text("خلفية المصحف", color = RoyalColors.Gold, fontWeight = FontWeight.Bold)
                    }
                    items(listOf(
                        "color" to "ملونة", "nature" to "طبيعة", "forest" to "غابة",
                        "green" to "خضراء", "lake" to "بحيرة", "mountains" to "جبال",
                        "sunset" to "غروب", "waterfall" to "شلال"
                    ), key = { it.first }) { (key, label) ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                .background(if (selectedWallpaper == key) RoyalColors.Gold.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
                                .clickable {
                                    selectedWallpaper = key
                                    preferences.edit().putString("quran_wallpaper_key", key).apply()
                                }.padding(9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = RoyalColors.Pearl, modifier = Modifier.weight(1f), fontSize = 12.sp)
                            if (selectedWallpaper == key) Text("✓", color = RoyalColors.Gold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsOpen = false }) {
                    Text("تم", color = RoyalColors.Gold)
                }
            }
        )
    }

    if (audioLibraryOpen) {
        val readerLabel = when (selectedReader) {
            "maher" -> "ماهر المعيقلي"
            "sudais" -> "عبد الرحمن السديس"
            "mossad" -> "عبد الرحمن مسعد • تلاوات مختارة"
            else -> "مشاري العفاسي"
        }
        AlertDialog(
            onDismissRequest = { audioLibraryOpen = false },
            containerColor = Color(0xF0141D29),
            title = {
                Text("الصوتيات المحمّلة • $readerLabel", color = RoyalColors.Gold, fontWeight = FontWeight.Bold)
            },
            text = {
                val librarySurahs = surahs.filter { it.number in downloadedAudioSurahs }.sortedBy { it.number }
                if (librarySurahs.isEmpty()) {
                    Text(
                        "لا توجد سور محمّلة لهذا القارئ بعد. افتح سورة ثم اضغط زر التنزيل، أو استخدم تنزيل جميع السور من الإعدادات.",
                        color = RoyalColors.Pearl,
                        fontSize = 13.sp
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(380.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        items(librarySurahs, key = { it.number }) { chapter ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                    .background(Color(0x6630AFA8))
                                    .border(1.dp, RoyalColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(9.dp))
                                    .clickable {
                                        selectedSurah = chapter
                                        audioLibraryOpen = false
                                    }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(chapter.name, color = RoyalColors.Pearl, modifier = Modifier.weight(1f))
                                Text("✓", color = RoyalColors.Gold, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { audioLibraryOpen = false }) {
                    Text("إغلاق", color = RoyalColors.Gold)
                }
            }
        )
    }

    if (mossadSelectionsOpen) {
        QuranMossadSelectionsScreen(onClose = { mossadSelectionsOpen = false })
    }
}

@Composable
private fun QuranAyahFlowerNumber(number: Int) {
    Box(
        Modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            val gold = RoyalColors.Gold
            val orbit = size.minDimension * 0.27f
            val petalRadius = size.minDimension * 0.12f
            for (petal in 0 until 8) {
                val angle = petal * (PI / 4.0)
                drawCircle(
                    color = gold.copy(alpha = 0.88f),
                    radius = petalRadius,
                    center = Offset(
                        centerX + cos(angle).toFloat() * orbit,
                        centerY + sin(angle).toFloat() * orbit
                    )
                )
            }
            drawCircle(
                color = gold,
                radius = size.minDimension * 0.34f,
                style = Stroke(width = 1.2.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF174D4B),
                radius = size.minDimension * 0.19f
            )
        }
        Text(
            number.toString(),
            color = RoyalColors.Gold,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun QuranReaderAction(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier.clip(RoundedCornerShape(10.dp))
            .background(if (selected) RoyalColors.Gold.copy(alpha = 0.2f) else Color(0xFF1B2735))
            .border(
                1.dp,
                if (selected) RoyalColors.Gold else RoyalColors.Gold.copy(alpha = if (enabled) 0.4f else 0.15f),
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .focusable(enabled = enabled)
            .padding(horizontal = 9.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) RoyalColors.Pearl else RoyalColors.Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}
