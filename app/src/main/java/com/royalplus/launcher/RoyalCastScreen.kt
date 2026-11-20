package com.royalplus.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.media.projection.MediaProjectionManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.webrtc.SurfaceViewRenderer

private val CastPanel = Color(0xFF111722)
private val CastPanelBorder = Color(0x55D6AD5B)
private val CastGold = Color(0xFFFFD36A)
private val CastPearl = Color(0xFFF2F0E8)
private val CastMuted = Color(0xFFB6B8BF)

@Composable
internal fun RoyalCastScreen(language: String) {
    val adaptiveProfile = LocalRoyalAdaptiveProfile.current
    if (adaptiveProfile.isPhone) {
        RoyalCastPhoneScreen(language)
    } else {
        RoyalCastTelevisionScreen(language)
    }
}

@Composable
private fun RoyalCastPhoneScreen(language: String) {
    val context = LocalContext.current
    var selectedTv by remember { mutableStateOf<RoyalCastEndpoint?>(null) }
    var scannerMessage by remember { mutableStateOf<String?>(null) }
    val projectionManager = remember(context) {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val endpoint = selectedTv
        val permissionData = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && permissionData != null && endpoint != null) {
            val serviceIntent = Intent(context, RoyalCastSenderService::class.java).apply {
                action = RoyalCastSenderService.ACTION_START
                putExtra(RoyalCastSenderService.EXTRA_HOST, endpoint.host)
                putExtra(RoyalCastSenderService.EXTRA_PORT, endpoint.port)
                putExtra(RoyalCastSenderService.EXTRA_TOKEN, endpoint.token)
                putExtra(RoyalCastSenderService.EXTRA_PIN, endpoint.pin)
                putExtra(RoyalCastSenderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RoyalCastSenderService.EXTRA_PROJECTION_DATA, permissionData)
            }
            runCatching {
                ContextCompat.startForegroundService(context, serviceIntent)
                RoyalCastSenderStatus.update("تم منح الإذن. يجري الاتصال بالتلفاز...")
            }.onFailure {
                RoyalCastSenderStatus.update(it.message ?: "تعذر تشغيل خدمة البث.")
            }
        } else {
            RoyalCastSenderStatus.update("تم إلغاء إذن مشاركة الشاشة. لم يبدأ البث.")
        }
    }

    val scannerLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            scannerMessage = "لم يتم مسح رمز التلفاز."
        } else {
            val endpoint = RoyalCastPairing.parsePayload(raw)
            if (endpoint == null) {
                selectedTv = null
                scannerMessage = "هذا الرمز غير صالح أو ليس رمز ROYAL CAST."
            } else {
                selectedTv = endpoint
                scannerMessage = null
                RoyalCastSenderStatus.update("تم العثور على التلفاز. راجع رمز التأكيد ثم ابدأ البث.")
            }
        }
    }

    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CastHeader(
            title = when (language) { "en" -> "ROYAL CAST"; "tr" -> "ROYAL CAST"; else -> "بث شاشة الهاتف" },
            subtitle = when (language) {
                "en" -> "Mirror your phone screen to your ROYAL+ TV"
                "tr" -> "Telefon ekranınızı ROYAL+ televizyonunuza yansıtın"
                else -> "اعرض شاشة هاتفك مباشرة على تلفاز ROYAL+"
            },
            icon = Icons.Rounded.PhoneAndroid
        )

        CastInfoCard {
            Text(
                when (language) {
                    "en" -> "Connect both devices to the same Wi-Fi network. On the TV, open the Phone Cast section and keep its pairing QR code visible."
                    "tr" -> "İki cihazı da aynı Wi-Fi ağına bağlayın. TV'de Telefon Yansıtma bölümünü açıp QR kodunu ekranda tutun."
                    else -> "صل الهاتف والتلفاز بشبكة Wi-Fi نفسها. افتح قسم بث شاشة الهاتف على التلفاز واترك رمز الاقتران ظاهراً."
                },
                color = CastPearl, fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center
            )
        }

        Button(
            onClick = {
                scannerMessage = null
                val options = ScanOptions().apply {
                    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    setPrompt(when (language) { "en" -> "Scan the ROYAL+ TV QR code"; "tr" -> "ROYAL+ TV QR kodunu tarayın"; else -> "وجّه الكاميرا نحو رمز التلفاز" })
                    setBeepEnabled(false)
                    setOrientationLocked(false)
                }
                scannerLauncher.launch(options)
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CastGold, contentColor = Color(0xFF17130B)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Rounded.QrCodeScanner, contentDescription = null)
            Spacer(Modifier.width(9.dp))
            Text(
                when (language) { "en" -> "Scan TV pairing code"; "tr" -> "TV kodunu tara"; else -> "مسح رمز اقتران التلفاز" },
                fontWeight = FontWeight.Bold
            )
        }

        if (scannerMessage != null) {
            Text(scannerMessage.orEmpty(), color = Color(0xFFFFB6A8), fontSize = 13.sp, textAlign = TextAlign.Center)
        }

        selectedTv?.let { tv ->
            CastInfoCard {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF65D6A6), modifier = Modifier.size(28.dp))
                Text(
                    when (language) { "en" -> "TV found"; "tr" -> "TV bulundu"; else -> "تم العثور على التلفاز" },
                    color = CastGold, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Text("${tv.host} : ${tv.port}", color = CastMuted, fontSize = 12.sp)
                Text(
                    when (language) { "en" -> "Pairing PIN"; "tr" -> "Eşleştirme PIN'i"; else -> "رمز التأكيد الظاهر على التلفاز" },
                    color = CastPearl, fontSize = 13.sp
                )
                Text(tv.pin.chunked(3).joinToString(" "), color = CastGold, fontSize = 27.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Button(
                    onClick = {
                        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CastGold, contentColor = Color(0xFF17130B)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Rounded.ScreenShare, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(when (language) { "en" -> "Start screen sharing"; "tr" -> "Ekran paylaşımını başlat"; else -> "بدء مشاركة الشاشة" }, fontWeight = FontWeight.Bold)
                }
            }
        }

        CastInfoCard {
            Text(
                when (language) { "en" -> "Broadcast status"; "tr" -> "Yayın durumu"; else -> "حالة البث" },
                color = CastGold, fontSize = 14.sp, fontWeight = FontWeight.Bold
            )
            Text(RoyalCastSenderStatus.message, color = CastPearl, fontSize = 13.sp, textAlign = TextAlign.Center)
            OutlinedButton(
                onClick = {
                    context.stopService(Intent(context, RoyalCastSenderService::class.java))
                    RoyalCastSenderStatus.update("تم طلب إيقاف البث")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.StopCircle, contentDescription = null, tint = Color(0xFFFF9F91))
                Spacer(Modifier.width(8.dp))
                Text(when (language) { "en" -> "Stop sharing"; "tr" -> "Paylaşımı durdur"; else -> "إيقاف البث" }, color = CastPearl)
            }
        }

        Text(
            when (language) {
                "en" -> "Screen capture starts only after Android displays its consent dialog. Video is sent directly over your local network."
                "tr" -> "Ekran yakalama yalnızca Android izin penceresini onayladıktan sonra başlar. Video yerel ağınız üzerinden doğrudan gönderilir."
                else -> "لا يبدأ التقاط الشاشة إلا بعد موافقتك في نافذة Android. ينتقل الفيديو مباشرة عبر شبكتك المحلية."
            },
            color = CastMuted, fontSize = 11.sp, lineHeight = 17.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoyalCastTelevisionScreen(language: String) {
    val context = LocalContext.current
    val controller = remember(context) { RoyalCastReceiverController(context.applicationContext) }
    val receiverState by controller.state.collectAsState()
    var renderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    DisposableEffect(controller) {
        controller.start()
        onDispose {
            controller.detachRenderer(renderer)
            runCatching { renderer?.release() }
            renderer = null
            controller.stop()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CastHeader(
            title = "ROYAL CAST",
            subtitle = when (language) {
                "en" -> "Receive a phone screen on this TV"
                "tr" -> "Telefon ekranını bu TV'de alın"
                else -> "استقبال شاشة الهاتف على هذا التلفاز"
            },
            icon = Icons.Rounded.Cast
        )

        if (receiverState.phase == RoyalCastReceiverPhase.CONNECTED) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.dp, CastPanelBorder, RoundedCornerShape(18.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        SurfaceViewRenderer(viewContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            init(RoyalCastRtc.eglContext(), null)
                            setEnableHardwareScaler(true)
                            setMirror(false)
                            renderer = this
                            controller.attachRenderer(this)
                        }
                    },
                    update = { view ->
                        if (renderer !== view) renderer = view
                        controller.attachRenderer(view)
                    }
                )
                Text(
                    receiverState.message,
                    color = CastPearl.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp)
                        .background(Color(0x99000000), RoundedCornerShape(9.dp)).padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        } else {
            CastInfoCard(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    when (language) { "en" -> "Ready to receive"; "tr" -> "Bağlantı bekleniyor"; else -> "التلفاز جاهز للاستقبال" },
                    color = CastGold, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    receiverState.message,
                    color = CastPearl, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center
                )
                val payload = receiverState.pairingPayload
                val qrBitmap = remember(payload) { payload?.let(::createRoyalCastQr) }
                if (qrBitmap != null && receiverState.phase == RoyalCastReceiverPhase.WAITING) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "ROYAL CAST pairing QR code",
                        modifier = Modifier.size(220.dp).clip(RoundedCornerShape(14.dp))
                    )
                    Text(
                        when (language) { "en" -> "PAIRING PIN"; "tr" -> "EŞLEŞTİRME PIN'İ"; else -> "رمز التأكيد" },
                        color = CastMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp
                    )
                    Text(receiverState.pin.orEmpty().chunked(3).joinToString(" "), color = CastGold, fontSize = 29.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                    Text(
                        "${receiverState.host}  •  ${receiverState.port}",
                        color = CastMuted, fontSize = 11.sp
                    )
                } else if (receiverState.phase == RoyalCastReceiverPhase.STARTING) {
                    Text("ROYAL+", color = CastGold, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CastStatusPill(receiverState.message, receiverState.phase == RoyalCastReceiverPhase.CONNECTED, Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    controller.stop()
                    controller.start()
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(when (language) { "en" -> "Restart"; "tr" -> "Yenile"; else -> "تجديد الرمز" }, color = CastPearl)
            }
        }
        Text(
            when (language) {
                "en" -> "Keep your phone and TV on the same Wi-Fi network. The TV waits for one paired phone at a time."
                "tr" -> "Telefon ve TV aynı Wi-Fi ağında olmalıdır. TV aynı anda bir eşleştirilmiş telefonu bekler."
                else -> "يجب أن يكون الهاتف والتلفاز على شبكة Wi-Fi نفسها. يستقبل التلفاز هاتفاً واحداً في كل مرة."
            },
            color = CastMuted, fontSize = 11.sp, lineHeight = 17.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CastHeader(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(CastPanel).border(1.dp, CastPanelBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0x332D2311)).border(1.dp, CastPanelBorder, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = CastGold, modifier = Modifier.size(29.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = CastGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = CastMuted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun CastInfoCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(CastPanel).border(1.dp, CastPanelBorder, RoundedCornerShape(18.dp))
            .padding(17.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(11.dp),
        content = content
    )
}

@Composable
private fun CastStatusPill(message: String, connected: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (connected) Color(0x2229A879) else CastPanel)
            .border(1.dp, if (connected) Color(0x884CCB9C) else CastPanelBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            if (connected) Icons.Rounded.CheckCircle else Icons.Rounded.Cast,
            contentDescription = null,
            tint = if (connected) Color(0xFF65D6A6) else CastGold,
            modifier = Modifier.size(18.dp)
        )
        Text(message, color = CastPearl, fontSize = 11.sp, maxLines = 2)
    }
}

private fun createRoyalCastQr(payload: String): Bitmap? = runCatching {
    val hints = hashMapOf<EncodeHintType, Any>(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 480, 480, hints)
    val pixels = IntArray(matrix.width * matrix.height) { index ->
        val x = index % matrix.width
        val y = index / matrix.width
        if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE
    }
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
    }
}.getOrNull()
