package com.royalplus.launcher

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private data class RoyalFileEntry(
    val id: String,
    val name: String,
    val uri: Uri,
    val mimeType: String,
    val directory: Boolean,
    val size: Long,
    val modified: Long,
    val file: File? = null,
    val document: DocumentFile? = null
)

private data class RoyalStorageStats(val total: Long, val used: Long, val available: Long) {
    val percent: Int get() = if (total <= 0L) 0 else ((used.toDouble() / total) * 100.0).toInt().coerceIn(0, 100)
}

private val royalFileCategories = listOf(
    "all", "downloads", "images", "videos", "audio", "documents", "apk", "other"
)

@Composable
internal fun RoyalFileManagerScreen(language: String) {
    val context = LocalContext.current
    val root = remember { Environment.getExternalStorageDirectory() }
    var currentDirectory by remember { mutableStateOf(root) }
    var documentStack by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var category by remember { mutableStateOf("all") }
    var entries by remember { mutableStateOf<List<RoyalFileEntry>>(emptyList()) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var viewer by remember { mutableStateOf<RoyalFileEntry?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var directAccess by remember { mutableStateOf(hasRoyalFileManagerAccess(context)) }
    var storage by remember { mutableStateOf(readRoyalStorageStats(root)) }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        directAccess = hasRoyalFileManagerAccess(context)
        refreshKey++
    }
    val allFilesSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        directAccess = hasRoyalFileManagerAccess(context)
        refreshKey++
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val selectedFolder = DocumentFile.fromTreeUri(context, uri)
            if (selectedFolder != null) {
                documentStack = listOf(selectedFolder)
                category = "all"
                selectedIds = emptySet()
                refreshKey++
            }
        }
    }

    val currentDocumentDirectory = documentStack.lastOrNull()
    val browsingAllowed = directAccess || currentDocumentDirectory != null

    LaunchedEffect(currentDirectory, currentDocumentDirectory?.uri, category, refreshKey, browsingAllowed) {
        if (!browsingAllowed) {
            entries = emptyList()
            storage = readRoyalStorageStats(root)
            return@LaunchedEffect
        }
        loading = true
        entries = withContext(Dispatchers.IO) {
            if (currentDocumentDirectory != null) {
                listRoyalDocumentEntries(currentDocumentDirectory, category)
            } else {
                val target = if (category == "downloads") {
                    File(root, "Download").takeIf { it.exists() } ?: File(root, "Downloads")
                } else currentDirectory
                listRoyalFileEntries(target, category)
            }
        }
        storage = withContext(Dispatchers.IO) { readRoyalStorageStats(root) }
        loading = false
    }

    val canGoUp = viewer != null || selectedIds.isNotEmpty() ||
        documentStack.size > 1 || (documentStack.isEmpty() && currentDirectory != root)

    BackHandler(enabled = canGoUp) {
        when {
            viewer != null -> viewer = null
            selectedIds.isNotEmpty() -> selectedIds = emptySet()
            documentStack.size > 1 -> documentStack = documentStack.dropLast(1)
            documentStack.isEmpty() && currentDirectory != root ->
                currentDirectory = currentDirectory.parentFile ?: root
        }
    }

    Column(
        Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (viewer != null) {
            RoyalFilePreview(
                entry = viewer!!,
                language = language,
                onBack = { viewer = null }
            )
        } else {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Rounded.FolderOpen, null, tint = RoyalColors.Blue, modifier = Modifier.size(30.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (language) { "en" -> "ROYAL FILES"; "tr" -> "ROYAL DOSYALAR"; else -> "ملفات ROYAL+" },
                        color = RoyalColors.Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        currentDocumentDirectory?.name ?: if (currentDirectory == root) when (language) { "en" -> "Internal storage"; "tr" -> "Dahili depolama"; else -> "التخزين الداخلي" } else currentDirectory.name,
                        color = RoyalColors.Pearl, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (selectedIds.isNotEmpty()) {
                    IconButton(onClick = { deleteDialog = true }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "حذف المحدد", tint = Color(0xFFFF7777))
                    }
                }
                IconButton(onClick = { refreshKey++; selectedIds = emptySet() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "تحديث", tint = RoyalColors.Gold)
                }
                if (canGoUp) {
                    IconButton(onClick = {
                        when {
                            selectedIds.isNotEmpty() -> selectedIds = emptySet()
                            documentStack.size > 1 -> documentStack = documentStack.dropLast(1)
                            documentStack.isEmpty() && currentDirectory != root ->
                                currentDirectory = currentDirectory.parentFile ?: root
                        }
                    }) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع", tint = RoyalColors.Pearl)
                    }
                }
            }

            RoyalStorageCard(storage, language)

            if (!browsingAllowed) {
                RoyalFileAccessPanel(
                    language = language,
                    onGrantAllFiles = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val appSettings = Intent(
                                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                Uri.parse("package:" + context.packageName)
                            )
                            val generalSettings = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            try {
                                allFilesSettingsLauncher.launch(appSettings)
                            } catch (_: Exception) {
                                try { allFilesSettingsLauncher.launch(generalSettings) } catch (_: Exception) { }
                            }
                        } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                            val permissions = mutableListOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                                permissions += Manifest.permission.WRITE_EXTERNAL_STORAGE
                            }
                            legacyPermissionLauncher.launch(permissions.toTypedArray())
                        } else {
                            folderPicker.launch(null)
                        }
                    },
                    onChooseFolder = { folderPicker.launch(null) }
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    royalFileCategories.forEach { key ->
                        val selected = category == key
                        Surface(
                            onClick = {
                                category = key
                                selectedIds = emptySet()
                                if (key == "downloads" && documentStack.isEmpty()) {
                                    currentDirectory = root
                                }
                            },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selected) RoyalColors.Gold.copy(alpha = 0.20f) else RoyalColors.Panel,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) RoyalColors.Gold else RoyalColors.Muted.copy(alpha = 0.22f)
                            )
                        ) {
                            Text(
                                royalFileCategoryLabel(key, language),
                                color = if (selected) RoyalColors.Gold else RoyalColors.Pearl,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                if (selectedIds.isNotEmpty()) {
                    Text(
                        if (language == "en") selectedIds.size.toString() + " selected — tap files to add or remove"
                        else if (language == "tr") selectedIds.size.toString() + " seçildi"
                        else "تم تحديد " + selectedIds.size + " ملف — اضغط لإضافة أو إزالة ملفات",
                        color = RoyalColors.Turquoise, fontSize = 11.sp
                    )
                }

                when {
                    loading -> Text(
                        if (language == "en") "Reading files…" else if (language == "tr") "Dosyalar okunuyor…" else "جارٍ قراءة الملفات…",
                        color = RoyalColors.Muted, modifier = Modifier.padding(12.dp)
                    )
                    entries.isEmpty() -> Text(
                        if (language == "en") "No files in this location" else if (language == "tr") "Bu konumda dosya yok" else "لا توجد ملفات في هذا الموقع",
                        color = RoyalColors.Muted, modifier = Modifier.padding(12.dp)
                    )
                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(entries, key = { it.id }) { entry ->
                            RoyalFileRow(
                                entry = entry,
                                selected = entry.id in selectedIds,
                                selectionMode = selectedIds.isNotEmpty(),
                                language = language,
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        selectedIds = if (entry.id in selectedIds) selectedIds - entry.id else selectedIds + entry.id
                                    } else if (entry.directory) {
                                        if (entry.document != null) documentStack = documentStack + entry.document
                                        else entry.file?.let { currentDirectory = it }
                                        category = "all"
                                    } else {
                                        viewer = entry
                                    }
                                },
                                onLongClick = {
                                    selectedIds = selectedIds + entry.id
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            containerColor = RoyalColors.Panel,
            title = {
                Text(
                    if (language == "en") "Delete selected items?" else if (language == "tr") "Seçilen öğeler silinsin mi?" else "حذف العناصر المحددة؟",
                    color = RoyalColors.Pearl, fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    if (language == "en") "This permanently removes " + selectedIds.size + " item(s) from the selected storage location."
                    else if (language == "tr") selectedIds.size.toString() + " öğe kalıcı olarak silinecek."
                    else "سيتم حذف " + selectedIds.size + " عنصر نهائياً من موقع التخزين المحدد.",
                    color = RoyalColors.Muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = entries.filter { it.id in selectedIds }
                    chosen.forEach { deleteRoyalFileEntry(it) }
                    selectedIds = emptySet()
                    deleteDialog = false
                    refreshKey++
                }) {
                    Text(if (language == "en") "Delete" else if (language == "tr") "Sil" else "حذف", color = Color(0xFFFF7777))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) {
                    Text(if (language == "en") "Cancel" else if (language == "tr") "İptal" else "إلغاء", color = RoyalColors.Gold)
                }
            }
        )
    }
}

@Composable
private fun RoyalFileAccessPanel(
    language: String,
    onGrantAllFiles: () -> Unit,
    onChooseFolder: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RoyalColors.Panel),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Storage, null, tint = RoyalColors.Blue, modifier = Modifier.size(32.dp))
            Text(
                when (language) { "en" -> "Allow access to device files"; "tr" -> "Cihaz dosyalarına erişime izin ver"; else -> "اسمح بالوصول إلى ملفات الجهاز" },
                color = RoyalColors.Pearl, fontSize = 16.sp, fontWeight = FontWeight.Bold
            )
            Text(
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                        if (language == "en") "For full shared-storage browsing, Android requires the special “All files access” permission. Android still protects certain private app folders."
                        else if (language == "tr") "Paylaşılan depolamayı tam olarak taramak için Android özel “Tüm dosyalara erişim” izni ister. Bazı uygulama özel klasörleri yine korunur."
                        else "لتصفح مساحة التخزين المشتركة، يتطلب أندرويد إذن «الوصول إلى جميع الملفات». تبقى بعض مجلدات التطبيقات الخاصة محمية من النظام."
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->
                        if (language == "en") "Android 10 uses the system folder picker. Select a folder to browse it here."
                        else if (language == "tr") "Android 10 sistem klasör seçicisini kullanır. Buradan göz atmak için bir klasör seçin."
                        else "يستخدم Android 10 منتقي المجلدات الخاص بالنظام. اختر مجلداً ليظهر محتواه هنا."
                    else ->
                        if (language == "en") "ROYAL+ needs storage permission to list, preview and delete shared files."
                        else if (language == "tr") "ROYAL+ dosyaları listelemek, önizlemek ve silmek için depolama izni ister."
                        else "يحتاج ROYAL+ إلى إذن التخزين لعرض الملفات ومعاينتها وحذفها."
                },
                color = RoyalColors.Muted, fontSize = 12.sp
            )
            Button(onClick = onGrantAllFiles) {
                Text(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        if (language == "en") "Open storage access settings" else if (language == "tr") "Depolama erişim ayarları" else "فتح إعدادات إذن التخزين"
                    } else if (language == "en") "Grant storage permission" else if (language == "tr") "Depolama izni ver" else "منح إذن التخزين"
                )
            }
            TextButton(onClick = onChooseFolder) {
                Text(if (language == "en") "Choose a folder instead" else if (language == "tr") "Bunun yerine klasör seç" else "اختيار مجلد بدلاً من ذلك", color = RoyalColors.Blue)
            }
        }
    }
}

@Composable
private fun RoyalStorageCard(stats: RoyalStorageStats, language: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = RoyalColors.Panel)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(78.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(
                        RoyalColors.Muted.copy(alpha = 0.20f),
                        -90f, 360f, false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawArc(
                        RoyalColors.Blue,
                        -90f, stats.percent * 3.6f, false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stats.percent.toString() + "%", color = RoyalColors.Pearl, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(when (language) { "en" -> "used"; "tr" -> "kullanılan"; else -> "مستخدم" }, color = RoyalColors.Muted, fontSize = 8.sp)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(when (language) { "en" -> "Storage"; "tr" -> "Depolama"; else -> "مساحة التخزين" }, color = RoyalColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text(when (language) { "en" -> "Used"; "tr" -> "Kullanılan"; else -> "المستخدم" }, color = RoyalColors.Muted, fontSize = 9.sp)
                        Text(Formatter.formatFileSize(LocalContext.current, stats.used), color = RoyalColors.Pearl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text(when (language) { "en" -> "Free"; "tr" -> "Boş"; else -> "المتبقي" }, color = RoyalColors.Muted, fontSize = 9.sp)
                        Text(Formatter.formatFileSize(LocalContext.current, stats.available), color = RoyalColors.Blue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column {
                        Text(when (language) { "en" -> "Total"; "tr" -> "Toplam"; else -> "الكلي" }, color = RoyalColors.Muted, fontSize = 9.sp)
                        Text(Formatter.formatFileSize(LocalContext.current, stats.total), color = RoyalColors.Pearl, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RoyalFileRow(
    entry: RoyalFileEntry,
    selected: Boolean,
    selectionMode: Boolean,
    language: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val accent = when {
        entry.directory -> RoyalColors.Gold
        entry.mimeType.startsWith("image/") -> RoyalColors.Turquoise
        entry.mimeType.startsWith("video/") -> RoyalColors.Blue
        entry.mimeType.startsWith("audio/") -> RoyalColors.Gold
        entry.mimeType == "application/pdf" -> Color(0xFFE36F69)
        entry.name.endsWith(".apk", true) -> RoyalColors.Green
        else -> RoyalColors.Pearl
    }
    val icon = when {
        entry.directory -> Icons.Rounded.Folder
        entry.mimeType.startsWith("image/") -> Icons.Rounded.Image
        entry.mimeType.startsWith("video/") -> Icons.Rounded.Movie
        entry.mimeType.startsWith("audio/") -> Icons.Rounded.AudioFile
        entry.mimeType == "application/pdf" -> Icons.Rounded.PictureAsPdf
        entry.name.endsWith(".txt", true) || entry.name.endsWith(".pdf", true) -> Icons.Rounded.Description
        else -> Icons.Rounded.InsertDriveFile
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) RoyalColors.Blue.copy(alpha = 0.16f) else RoyalColors.Panel)
            .border(1.dp, if (selected) RoyalColors.Blue else RoyalColors.Gold.copy(alpha = 0.10f), RoundedCornerShape(13.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .focusable()
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (selectionMode) {
            Checkbox(checked = selected, onCheckedChange = { onClick() })
        } else {
            Icon(icon, null, tint = accent, modifier = Modifier.size(26.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(entry.name, color = RoyalColors.Pearl, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (entry.directory) if (language == "en") "Folder" else if (language == "tr") "Klasör" else "مجلد"
                else Formatter.formatFileSize(LocalContext.current, entry.size) + " • " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(entry.modified)),
                color = RoyalColors.Muted, fontSize = 9.sp, maxLines = 1
            )
        }
        if (entry.directory) {
            Icon(Icons.Rounded.FolderOpen, null, tint = RoyalColors.Gold.copy(alpha = 0.85f), modifier = Modifier.size(18.dp))
        } else if (selected) {
            Icon(Icons.Rounded.CheckCircle, null, tint = RoyalColors.Blue, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RoyalFilePreview(
    entry: RoyalFileEntry,
    language: String,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "رجوع", tint = RoyalColors.Gold)
            }
            Column(Modifier.weight(1f)) {
                Text(entry.name, color = RoyalColors.Pearl, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Formatter.formatFileSize(LocalContext.current, entry.size), color = RoyalColors.Muted, fontSize = 10.sp)
            }
        }
        when {
            entry.mimeType.startsWith("image/") -> RoyalInternalImagePreview(entry.uri)
            entry.mimeType.startsWith("video/") || entry.mimeType.startsWith("audio/") -> RoyalInternalMediaPreview(entry.uri)
            entry.mimeType == "application/pdf" -> RoyalInternalPdfPreview(entry)
            isRoyalTextPreview(entry.name, entry.mimeType) -> RoyalInternalTextPreview(entry)
            else -> Column(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(RoyalColors.Panel).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.InsertDriveFile, null, tint = RoyalColors.Blue, modifier = Modifier.size(58.dp))
                Text(entry.name, color = RoyalColors.Pearl, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                Text(
                    if (language == "en") "This format has no built-in preview. You are still inside ROYAL+."
                    else if (language == "tr") "Bu biçim için yerleşik önizleme yok. ROYAL+ içinde kalıyorsunuz."
                    else "لا تتوفر معاينة داخلية لهذا التنسيق حالياً. ستبقى داخل ROYAL+ دون فتح تطبيق خارجي.",
                    color = RoyalColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun RoyalInternalImagePreview(uri: Uri) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                openRoyalFileStream(context, uri, null)?.use { input ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                    BitmapFactory.decodeStream(input, null, options)
                }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text("تعذر عرض الصورة", color = RoyalColors.Muted)
        }
    }
}

@Composable
private fun RoyalInternalMediaPreview(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(Color.Black),
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                this.player = player
            }
        },
        update = { it.player = player }
    )
}

@Composable
private fun RoyalInternalTextPreview(entry: RoyalFileEntry) {
    val context = LocalContext.current
    val text by produceState(initialValue = "جارٍ تحميل النص…", entry.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                openRoyalFileStream(context, entry.uri, entry.file)?.bufferedReader()?.use { reader ->
                    val buffer = CharArray(8192)
                    val output = StringBuilder()
                    var count = reader.read(buffer)
                    while (count > 0 && output.length < 512_000) {
                        output.append(buffer, 0, count)
                        count = reader.read(buffer)
                    }
                    output.toString()
                } ?: "تعذر قراءة الملف"
            }.getOrElse { "تعذر قراءة محتوى الملف" }
        }
    }
    Text(
        text,
        color = RoyalColors.Pearl,
        fontSize = 12.sp,
        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(RoyalColors.Panel)
            .verticalScroll(rememberScrollState()).padding(16.dp)
    )
}

@Composable
private fun RoyalInternalPdfPreview(entry: RoyalFileEntry) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, entry.uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val descriptor = if (entry.file != null) {
                    ParcelFileDescriptor.open(entry.file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(entry.uri, "r")
                } ?: return@runCatching null
                descriptor.use { fd ->
                    PdfRenderer(fd).use { renderer ->
                        if (renderer.pageCount <= 0) return@use null
                        renderer.openPage(0).use { page ->
                            val scale = (1400f / page.width.coerceAtLeast(1)).coerceAtMost(2f)
                            Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            ).also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                        }
                    }
                }
            }.getOrNull()
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text("تعذر عرض صفحة PDF", color = RoyalColors.Muted)
    }
}

private fun isRoyalTextPreview(name: String, mime: String): Boolean =
    mime.startsWith("text/") || listOf(".txt", ".md", ".csv", ".json", ".xml", ".log").any { name.endsWith(it, true) }

private fun openRoyalFileStream(context: Context, uri: Uri, file: File?) =
    if (file != null) file.inputStream() else context.contentResolver.openInputStream(uri)

private fun hasRoyalFileManagerAccess(context: Context): Boolean = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> Environment.isExternalStorageManager()
    Build.VERSION.SDK_INT <= Build.VERSION_CODES.P ->
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    else -> false
}

private fun readRoyalStorageStats(root: File): RoyalStorageStats = runCatching {
    val stats = StatFs(root.absolutePath)
    val total = stats.totalBytes
    val available = stats.availableBytes
    RoyalStorageStats(total, (total - available).coerceAtLeast(0L), available.coerceAtLeast(0L))
}.getOrElse { RoyalStorageStats(0L, 0L, 0L) }

private fun listRoyalFileEntries(directory: File, category: String): List<RoyalFileEntry> {
    if (category == "all" || category == "downloads") {
        return directory.listFiles().orEmpty()
            .filter { it.canRead() }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenByDescending { it.lastModified() })
            .map { royalFileEntry(it) }
    }
    val result = mutableListOf<RoyalFileEntry>()
    val pending = ArrayDeque<Pair<File, Int>>()
    pending.add(directory to 0)
    while (pending.isNotEmpty() && result.size < 2500) {
        val (folder, depth) = pending.removeLast()
        if (depth > 8) continue
        val children = runCatching { folder.listFiles().orEmpty() }.getOrDefault(emptyArray())
        children.forEach { child ->
            if (child.isDirectory) {
                val restricted = child.name == "Android" || child.name == ".thumbnails" || child.name == ".Trash"
                if (!restricted && depth < 8) pending.add(child to depth + 1)
            } else if (matchesRoyalFileCategory(child.name, category)) {
                result += royalFileEntry(child)
            }
        }
    }
    return result.sortedByDescending { it.modified }
}

private fun listRoyalDocumentEntries(directory: DocumentFile, category: String): List<RoyalFileEntry> =
    runCatching { directory.listFiles().toList() }.getOrDefault(emptyList())
        .filter { it.exists() && (category == "all" || matchesRoyalFileCategory(it.name.orEmpty(), category)) }
        .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenByDescending { it.lastModified() })
        .map { doc ->
            val name = doc.name.orEmpty()
            RoyalFileEntry(
                id = doc.uri.toString(),
                name = name,
                uri = doc.uri,
                mimeType = doc.type ?: royalMimeType(name),
                directory = doc.isDirectory,
                size = doc.length(),
                modified = doc.lastModified(),
                document = doc
            )
        }

private fun royalFileEntry(file: File): RoyalFileEntry = RoyalFileEntry(
    id = file.absolutePath,
    name = file.name,
    uri = Uri.fromFile(file),
    mimeType = if (file.isDirectory) "inode/directory" else royalMimeType(file.name),
    directory = file.isDirectory,
    size = file.length(),
    modified = file.lastModified(),
    file = file
)

private fun royalMimeType(name: String): String {
    val extension = name.substringAfterLast('.', "").lowercase()
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when (extension) {
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "txt", "md", "csv", "log" -> "text/plain"
        "json" -> "application/json"
        "xml" -> "text/xml"
        else -> "application/octet-stream"
    }
}

private fun matchesRoyalFileCategory(name: String, category: String): Boolean {
    val mime = royalMimeType(name)
    return when (category) {
        "images" -> mime.startsWith("image/")
        "videos" -> mime.startsWith("video/")
        "audio" -> mime.startsWith("audio/")
        "documents" -> mime == "application/pdf" || mime.startsWith("text/") ||
            listOf(".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".rtf").any { name.endsWith(it, true) }
        "apk" -> name.endsWith(".apk", true)
        "other" -> !mime.startsWith("image/") && !mime.startsWith("video/") &&
            !mime.startsWith("audio/") && mime != "application/pdf" && !mime.startsWith("text/") &&
            !name.endsWith(".apk", true)
        else -> true
    }
}

private fun deleteRoyalFileEntry(entry: RoyalFileEntry) {
    entry.file?.let { file ->
        runCatching { if (file.isDirectory) file.deleteRecursively() else file.delete() }
        return
    }
    entry.document?.let { deleteRoyalDocument(it) }
}

private fun deleteRoyalDocument(document: DocumentFile) {
    if (document.isDirectory) {
        runCatching { document.listFiles().forEach { deleteRoyalDocument(it) } }
    }
    runCatching { document.delete() }
}

private fun royalFileCategoryLabel(key: String, language: String): String = when (key) {
    "all" -> if (language == "en") "All files" else if (language == "tr") "Tüm dosyalar" else "الكل"
    "downloads" -> if (language == "en") "Downloads" else if (language == "tr") "İndirilenler" else "التنزيلات"
    "images" -> if (language == "en") "Images" else if (language == "tr") "Resimler" else "الصور"
    "videos" -> if (language == "en") "Videos" else if (language == "tr") "Videolar" else "الفيديو"
    "audio" -> if (language == "en") "Audio" else if (language == "tr") "Ses" else "الصوتيات"
    "documents" -> if (language == "en") "Documents" else if (language == "tr") "Belgeler" else "المستندات"
    "apk" -> "APK"
    else -> if (language == "en") "Other" else if (language == "tr") "Diğer" else "أخرى"
}
