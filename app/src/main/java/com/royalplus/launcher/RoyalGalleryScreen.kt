package com.royalplus.launcher

import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RoyalMediaItem(val id: Long, val uri: Uri, val mime: String, val name: String, val video: Boolean)

@Composable
internal fun RoyalGalleryScreen() {
    val context = LocalContext.current
    var items by remember { mutableStateOf<List<RoyalMediaItem>>(emptyList()) }
    var granted by remember { mutableStateOf(hasRoyalMediaPermission(context)) }
    var loading by remember { mutableStateOf(false) }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasRoyalMediaPermission(context)
    }
    fun requestAccess() {
        val requested = when {
            Build.VERSION.SDK_INT >= 34 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= 33 -> arrayOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VIDEO
            )
            else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permissionRequest.launch(requested)
    }
    LaunchedEffect(granted) {
        if (!granted) { items = emptyList(); return@LaunchedEffect }
        loading = true
        items = withContext(Dispatchers.IO) { queryRoyalMedia(context.contentResolver) }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("المعرض", color = RoyalColors.Gold, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("الصور ومقاطع الفيديو على الجهاز", color = RoyalColors.Pearl.copy(alpha = .75f), fontSize = 12.sp)
        when {
            !granted -> GalleryEmpty("السماح بالوصول إلى الصور والفيديوهات", "يحتاج المعرض إلى إذن قراءة الوسائط", "منح الإذن", Icons.Rounded.Lock, ::requestAccess)
            loading -> GalleryEmpty("جارٍ تحميل الوسائط…", "", null, Icons.Rounded.Collections, null)
            items.isEmpty() -> GalleryEmpty("لا توجد وسائط", "لم يتم العثور على صور أو فيديوهات في الذاكرة المتاحة", null, Icons.Rounded.Collections, null)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(126.dp), modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items, key = { it.id.toString() + it.mime }) { item ->
                    RoyalMediaTile(item) {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(item.uri, item.mime)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "لا يوجد تطبيق مناسب لفتح هذا الملف", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

private fun hasRoyalMediaPermission(context: android.content.Context): Boolean {
    val permissions = when {
        Build.VERSION.SDK_INT >= 34 -> listOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        )
        Build.VERSION.SDK_INT >= 33 -> listOf(
            android.Manifest.permission.READ_MEDIA_IMAGES, android.Manifest.permission.READ_MEDIA_VIDEO
        )
        else -> listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    return permissions.any { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
}

private const val GALLERY_THUMBNAIL_MAX_EDGE = 840
private const val GALLERY_THUMBNAIL_MAX_PIXELS = 1_000_000L

private fun loadRoyalMediaThumbnail(
    context: android.content.Context,
    item: RoyalMediaItem
): Bitmap? {
    return runCatching {
        val bitmap = when {
            Build.VERSION.SDK_INT >= 29 ->
                context.contentResolver.loadThumbnail(item.uri, Size(420, 300), null)
            item.video ->
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    item.id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            else -> decodeLegacyPhotoThumbnail(context, item.uri)
        } ?: return@runCatching null

        val sampleSize = royalBitmapSampleSize(
            width = bitmap.width,
            height = bitmap.height,
            maxEdgePx = GALLERY_THUMBNAIL_MAX_EDGE,
            maxPixels = GALLERY_THUMBNAIL_MAX_PIXELS
        )
        if (sampleSize <= 1) {
            bitmap
        } else {
            val targetWidth = (bitmap.width / sampleSize).coerceAtLeast(1)
            val targetHeight = (bitmap.height / sampleSize).coerceAtLeast(1)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true).also { scaled ->
                if (scaled !== bitmap) bitmap.recycle()
            }
        }
    }.getOrNull()
}

private fun decodeLegacyPhotoThumbnail(
    context: android.content.Context,
    uri: Uri
): Bitmap? {
    val bounds = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
        inScaled = false
    }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    } ?: return null

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = royalBitmapSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxEdgePx = GALLERY_THUMBNAIL_MAX_EDGE,
            maxPixels = GALLERY_THUMBNAIL_MAX_PIXELS
        )
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}

private fun queryRoyalMedia(resolver: android.content.ContentResolver): List<RoyalMediaItem> {
    val collection = MediaStore.Files.getContentUri("external")
    val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DISPLAY_NAME, MediaStore.Files.FileColumns.MEDIA_TYPE)
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val args = arrayOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(), MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())
    val result = mutableListOf<RoyalMediaItem>()
    val cursor: Cursor? = resolver.query(collection, projection, selection, args, "${MediaStore.Files.FileColumns.DATE_ADDED} DESC")
    cursor?.use {
        val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mimeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val typeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        while (it.moveToNext()) {
            val id = it.getLong(idCol)
            val mime = it.getString(mimeCol) ?: continue
            val video = it.getInt(typeCol) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            result += RoyalMediaItem(id, Uri.withAppendedPath(collection, id.toString()), mime, it.getString(nameCol) ?: "", video)
        }
    }
    return result
}

@Composable
private fun RoyalMediaTile(item: RoyalMediaItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(item.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        bitmap = withContext(Dispatchers.IO) {
            loadRoyalMediaThumbnail(context, item)
        }
    }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFF11151D))
        .clickable(onClick = onClick).focusable().padding(6.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.35f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF242A34)), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = item.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Icon(if (item.video) Icons.Rounded.Movie else Icons.Rounded.Image, contentDescription = null, tint = RoyalColors.Gold)
            if (item.video) {
                Icon(Icons.Rounded.Movie, contentDescription = "فيديو", tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp))
            }
        }
        Text(item.name.ifBlank { if (item.video) "فيديو" else "صورة" }, color = RoyalColors.Pearl, fontSize = 11.sp, maxLines = 1,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 7.dp))
    }
}

@Composable
private fun GalleryEmpty(title: String, subtitle: String, action: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, onAction: (() -> Unit)?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = RoyalColors.Gold)
            Text(title, color = RoyalColors.Pearl, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (subtitle.isNotBlank()) Text(subtitle, color = RoyalColors.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            if (action != null && onAction != null) Text(action, color = RoyalColors.Gold, modifier = Modifier.clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF28200F)).clickable { onAction() }.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
}
