package com.royalplus.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicitly started microphone-caption service. Speech recognition is on-device;
 * only translated text is rendered in the overlay. Audio is never uploaded.
 */
class FloatingTranslationOverlayService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val audioSegments = ArrayBlockingQueue<FloatArray>(2)

    @Volatile private var recognizer: OfflineRecognizer? = null
    @Volatile private var audioRecord: AudioRecord? = null
    private var modelThread: Thread? = null
    private var captureThread: Thread? = null
    private var decodeThread: Thread? = null

    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var statusView: TextView? = null
    private var captionView: TextView? = null

    private var activeTranslator: Translator? = null
    private var activeSourceLanguage: String? = null
    private var translationModelReady = false
    private var translationModelPending = false
    private var latestPendingCaption: Pair<String, String>? = null
    private var translationRequest = 0
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialX = 0
    private var initialY = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startTranslationForeground()
        if (overlayView == null) {
            try {
                showOverlay()
            } catch (_: Exception) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (running.compareAndSet(false, true)) {
            updateStatus("يتم تجهيز الترجمة لأول استخدام…")
            modelThread = Thread({ prepareRecognizer() }, "RoyalWhisperSetup").apply { start() }
        }
        return START_NOT_STICKY
    }

    private fun prepareRecognizer() {
        try {
            val engine = WhisperModelRepository.createRecognizer(applicationContext) { message ->
                updateStatus(message)
            }
            if (!running.get()) {
                engine.release()
                return
            }
            recognizer = engine
            startAudioCapture()
        } catch (error: Exception) {
            if (running.get()) {
                updateStatus(
                    if (error is InterruptedException) "تم إيقاف تجهيز الترجمة"
                    else "تعذر تجهيز المحرك: ${error.localizedMessage ?: "تحقق من الإنترنت والمساحة"}"
                )
                mainHandler.postDelayed({ stopSelf() }, 4_000L)
            }
        }
    }

    private fun startAudioCapture() {
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            updateStatus("الميكروفون لا يدعم إعداد الصوت المطلوب على هذا الجهاز")
            mainHandler.postDelayed({ stopSelf() }, 4_000L)
            return
        }

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, SAMPLE_RATE * 2)
            )
        } catch (error: Exception) {
            updateStatus("تعذر فتح الميكروفون: ${error.localizedMessage ?: "تحقق من الإذن"}")
            mainHandler.postDelayed({ stopSelf() }, 4_000L)
            return
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            updateStatus("لم يتمكن الجهاز من تهيئة الميكروفون")
            mainHandler.postDelayed({ stopSelf() }, 4_000L)
            return
        }

        audioRecord = recorder
        updateStatus("الاستماع إلى صوت الفيديو عبر الميكروفون…")
        captureThread = Thread({ captureAudio(recorder) }, "RoyalAudioCapture").apply { start() }
        decodeThread = Thread({ decodeAudioSegments() }, "RoyalWhisperDecode").apply { start() }
    }

    private fun captureAudio(recorder: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val input = ShortArray(1_600)
        val segment = FloatArray(SEGMENT_SAMPLES)
        var filled = 0
        try {
            recorder.startRecording()
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(input, 0, input.size)
                if (count > 0) {
                    for (index in 0 until count) {
                        segment[filled++] = input[index] / 32768.0f
                        if (filled == SEGMENT_SAMPLES) {
                            val completed = segment.copyOf()
                            if (!audioSegments.offer(completed)) {
                                audioSegments.poll()
                                audioSegments.offer(completed)
                            }
                            filled = 0
                        }
                    }
                } else if (count < 0) {
                    updateStatus("انقطع اتصال الميكروفون؛ أعد تشغيل الترجمة")
                    mainHandler.postDelayed({ stopSelf() }, 3_000L)
                    break
                }
            }
        } catch (error: Exception) {
            if (running.get()) {
                updateStatus("توقف التقاط الصوت: ${error.localizedMessage ?: "خطأ في الميكروفون"}")
                mainHandler.postDelayed({ stopSelf() }, 3_000L)
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: Exception) {
                // The platform may have stopped recording during service shutdown.
            }
        }
    }

    private fun decodeAudioSegments() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                val samples = audioSegments.take()
                val engine = recognizer ?: continue
                val stream = engine.createStream()
                val result = try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    engine.decode(stream)
                    engine.getResult(stream)
                } finally {
                    stream.release()
                }

                val recognized = result.text.replace(Regex("\\s+"), " ").trim()
                val language = result.lang.trim().lowercase(Locale.ROOT)
                if (recognized.isNotBlank()) {
                    translateCaption(recognized, language)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: Exception) {
                updateStatus("تعذر تحليل الصوت: ${error.localizedMessage ?: "خطأ في محرك التعرّف"}")
            }
        }
    }

    private fun translateCaption(text: String, detectedLanguage: String) {
        mainHandler.post {
            val sourceLanguage = TranslateLanguage.fromLanguageTag(detectedLanguage)
            if (sourceLanguage.isNullOrBlank()) {
                updateStatus("تعرّف الصوت على اللغة، لكن ML Kit لا يوفّر ترجمتها حالياً")
                return@post
            }
            if (sourceLanguage == TranslateLanguage.ARABIC) {
                showCaption(text, detectedLanguage)
                return@post
            }

            if (activeSourceLanguage != sourceLanguage || activeTranslator == null) {
                activeTranslator?.close()
                activeSourceLanguage = sourceLanguage
                translationModelReady = false
                translationModelPending = false
                latestPendingCaption = null
                activeTranslator = Translation.getClient(
                    TranslatorOptions.Builder()
                        .setSourceLanguage(sourceLanguage)
                        .setTargetLanguage(TranslateLanguage.ARABIC)
                        .build()
                )
            }

            val translator = activeTranslator ?: return@post
            if (!translationModelReady) {
                latestPendingCaption = text to detectedLanguage
                if (translationModelPending) return@post

                translationModelPending = true
                updateStatus("تنزيل نموذج الترجمة ${detectedLanguage.uppercase(Locale.ROOT)} إلى العربية…")
                translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .addOnSuccessListener {
                        if (!running.get() || translator !== activeTranslator) return@addOnSuccessListener
                        translationModelPending = false
                        translationModelReady = true
                        val latest = latestPendingCaption
                        latestPendingCaption = null
                        if (latest != null) translateWithReadyModel(translator, latest.first, latest.second)
                    }
                    .addOnFailureListener {
                        if (running.get() && translator === activeTranslator) {
                            translationModelPending = false
                            updateStatus("تعذر تنزيل نموذج الترجمة لهذه اللغة")
                        }
                    }
                return@post
            }
            translateWithReadyModel(translator, text, detectedLanguage)
        }
    }

    private fun translateWithReadyModel(translator: Translator, text: String, language: String) {
        val requestId = ++translationRequest
        translator.translate(text)
            .addOnSuccessListener { translated ->
                if (running.get() && requestId == translationRequest) {
                    showCaption(translated, language)
                }
            }
            .addOnFailureListener {
                if (running.get() && requestId == translationRequest) {
                    updateStatus("تعذرت ترجمة هذا المقطع؛ ستستمر المحاولة مع المقطع التالي")
                }
            }
    }

    private fun showCaption(text: String, language: String) {
        mainHandler.post {
            captionView?.text = text
            statusView?.text = "ترجمة مباشرة • ${language.uppercase(Locale.ROOT)} ← العربية"
            updateNotification("الترجمة العائمة تعمل")
        }
    }

    private fun updateStatus(message: String) {
        mainHandler.post {
            statusView?.text = message
            updateNotification(message)
        }
    }

    private fun showOverlay() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(10), dp(15), dp(13))
            background = roundedBackground(0xE9161B24.toInt(), 0xFFD6AD62.toInt(), dp(16).toFloat())
            elevation = dp(10).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(3), dp(2), dp(2), dp(7))
        }
        val title = TextView(this).apply {
            text = "ROYAL+  •  الترجمة العربية المباشرة"
            setTextColor(0xFFFFD987.toInt())
            textSize = 14f
            setPadding(dp(4), dp(4), dp(8), dp(4))
        }
        val close = TextView(this).apply {
            text = "×"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = roundedBackground(0xFF343B48.toInt(), 0xFF6E7786.toInt(), dp(9).toFloat())
            contentDescription = "إيقاف الترجمة العائمة"
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(38), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(38)))
        header.setOnTouchListener { _, event -> moveOverlay(event) }

        val status = TextView(this).apply {
            text = "بدء تجهيز الترجمة…"
            setTextColor(0xFFFFD987.toInt())
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(3), dp(8), dp(6))
        }
        val caption = TextView(this).apply {
            text = "ستظهر الترجمة العربية هنا"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
            textDirection = android.view.View.TEXT_DIRECTION_FIRST_STRONG_RTL
            setPadding(dp(14), dp(13), dp(14), dp(13))
            background = roundedBackground(0xCC05070B.toInt(), 0x55FFFFFF, dp(12).toFloat())
        }
        container.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        container.addView(status, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))
        container.addView(caption, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.82f).toInt().coerceAtLeast(dp(280)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(104)
        }
        manager.addView(container, params)
        overlayView = container
        overlayParams = params
        statusView = status
        captionView = caption
        close.setOnClickListener { stopSelf() }
    }

    private fun moveOverlay(event: MotionEvent): Boolean {
        val params = overlayParams ?: return false
        val manager = windowManager ?: return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                initialX = params.x
                initialY = params.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                params.x = initialX + (event.rawX - dragStartX).toInt()
                params.y = initialY - (event.rawY - dragStartY).toInt()
                try {
                    overlayView?.let { manager.updateViewLayout(it, params) }
                } catch (_: Exception) {
                    stopSelf()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ترجمة الفيديو العائمة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعار ظاهر أثناء التقاط صوت الفيديو وترجمته محلياً"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun startTranslationForeground() {
        val notification = buildNotification("يتم تجهيز الترجمة")
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val stopPendingIntent = PendingIntent.getService(this, 4102, stopIntent, pendingFlags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("ROYAL+ — الترجمة العربية")
            .setContentText(message.take(110))
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopPendingIntent
            ).build())
            .build()
    }

    private fun updateNotification(message: String) {
        try {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(message))
        } catch (_: Exception) {
            // Notification permissions may be restricted by the TV firmware.
        }
    }

    private fun roundedBackground(color: Int, strokeColor: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            setStroke(dp(1), strokeColor)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        running.set(false)
        translationRequest++
        captureThread?.interrupt()
        decodeThread?.interrupt()
        modelThread?.interrupt()
        audioSegments.clear()

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
            }
        } catch (_: Exception) {
            // The capture device may already have been released by the system.
        }
        audioRecord = null
        recognizer?.release()
        recognizer = null
        activeTranslator?.close()
        activeTranslator = null
        activeSourceLanguage = null
        translationModelReady = false
        translationModelPending = false
        latestPendingCaption = null

        overlayView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // The system may already have detached the overlay.
            }
        }
        overlayView = null
        overlayParams = null
        statusView = null
        captionView = null
        getSharedPreferences(PREFERENCES, MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, false).apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.royalplus.launcher.action.SHOW_TRANSLATION_OVERLAY"
        const val ACTION_STOP = "com.royalplus.launcher.action.STOP_TRANSLATION_OVERLAY"
        const val PREFERENCES = "royalplus_launcher_preferences"
        const val KEY_ENABLED = "floating_translation_overlay_enabled"
        private const val CHANNEL_ID = "royal_translation_overlay"
        private const val NOTIFICATION_ID = 4101
        private const val SAMPLE_RATE = 16_000
        private const val SEGMENT_SECONDS = 8
        private const val SEGMENT_SAMPLES = SAMPLE_RATE * SEGMENT_SECONDS
    }
}
