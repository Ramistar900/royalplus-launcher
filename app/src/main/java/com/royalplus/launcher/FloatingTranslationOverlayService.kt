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
import android.view.View
import android.view.WindowManager
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
    private var captionView: TextView? = null
    private var controlView: TextView? = null
    private var edgeRevealView: TextView? = null
    private var captionWindowParams: WindowManager.LayoutParams? = null
    private var controlWindowParams: WindowManager.LayoutParams? = null
    private var edgeWindowParams: WindowManager.LayoutParams? = null

    private val captionHideRunnable = Runnable {
        captionView?.visibility = View.GONE
    }
    private val controlHideRunnable = Runnable {
        hideControlAndShowEdge()
    }

    private var activeTranslator: Translator? = null
    private var activeSourceLanguage: String? = null
    private var translationModelReady = false
    private var translationModelPending = false
    private var latestPendingCaption: Pair<String, String>? = null
    private var translationRequest = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SHOW && running.get()) {
            revealControl()
            updateNotification("الترجمة العائمة تعمل")
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
        if (captionView == null) {
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
            val caption = captionView ?: return@post
            caption.text = text
            caption.visibility = View.VISIBLE
            mainHandler.removeCallbacks(captionHideRunnable)
            mainHandler.postDelayed(captionHideRunnable, CAPTION_DISPLAY_MILLIS)
            updateNotification("الترجمة العائمة تعمل")
        }
    }

    private fun updateStatus(message: String) {
        updateNotification(message)
    }

    /**
     * The subtitles and the controls use separate windows so hiding the controls
     * never interrupts audio capture or hides a current caption.
     */
    private fun showOverlay() {
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager
        val overlayType = overlayWindowType()

        val caption = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 21f
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            maxLines = 3
            includeFontPadding = true
            setPadding(dp(18), dp(9), dp(18), dp(9))
            background = solidRoundedBackground(0xB3000000.toInt(), dp(10).toFloat())
            visibility = View.GONE
            setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
            contentDescription = "الترجمة العربية"
        }
        val captionParams = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92f).toInt().coerceAtLeast(dp(240)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(62)
        }

        try {
            manager.addView(caption, captionParams)
            captionView = caption
            captionWindowParams = captionParams
            revealControl()
        } catch (error: Exception) {
            try {
                manager.removeView(caption)
            } catch (_: Exception) {
                // The window may not have been attached.
            }
            throw error
        }
    }

    private fun overlayWindowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun revealControl() {
        removeEdgeReveal()
        val manager = windowManager ?: return
        if (controlView == null) {
            val control = TextView(this).apply {
                text = "CC"
                textSize = 11f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(0xFFFFFFFF.toInt())
                background = solidRoundedBackground(0xE61A1F28.toInt(), dp(24).toFloat())
                contentDescription = "إيقاف الترجمة العائمة"
                setOnClickListener { stopSelf() }
            }
            val params = WindowManager.LayoutParams(
                dp(46),
                dp(46),
                overlayWindowType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                x = dp(12)
            }
            manager.addView(control, params)
            controlView = control
            controlWindowParams = params
        }
        scheduleControlHide()
    }

    private fun scheduleControlHide() {
        mainHandler.removeCallbacks(controlHideRunnable)
        mainHandler.postDelayed(controlHideRunnable, CONTROL_IDLE_HIDE_MILLIS)
    }

    private fun hideControlAndShowEdge() {
        removeControl()
        showEdgeReveal()
    }

    private fun showEdgeReveal() {
        val manager = windowManager ?: return
        if (edgeRevealView != null) return

        val edge = TextView(this).apply {
            isClickable = true
            contentDescription = "المس لإظهار أدوات الترجمة"
            background = solidRoundedBackground(0x55000000, dp(8).toFloat())
            setOnClickListener { revealControl() }
        }
        val params = WindowManager.LayoutParams(
            dp(12),
            dp(92),
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            x = 0
        }
        try {
            manager.addView(edge, params)
            edgeRevealView = edge
            edgeWindowParams = params
        } catch (_: Exception) {
            // The notification's Show action remains available as a fallback.
        }
    }

    private fun removeControl() {
        controlView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // The system may already have detached this view.
            }
        }
        controlView = null
        controlWindowParams = null
    }

    private fun removeEdgeReveal() {
        edgeRevealView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // The system may already have detached this view.
            }
        }
        edgeRevealView = null
        edgeWindowParams = null
    }

    private fun solidRoundedBackground(color: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
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
        val showIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
            action = ACTION_SHOW
        }
        val stopIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val showPendingIntent = PendingIntent.getService(this, 4103, showIntent, pendingFlags)
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
            .setContentIntent(showPendingIntent)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .addAction(Notification.Action.Builder(
                android.R.drawable.ic_menu_view, "إظهار", showPendingIntent
            ).build())
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

        mainHandler.removeCallbacks(captionHideRunnable)
        mainHandler.removeCallbacks(controlHideRunnable)

        listOfNotNull(captionView, controlView, edgeRevealView).forEach { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: Exception) {
                // The system may already have detached this overlay.
            }
        }
        captionView = null
        controlView = null
        edgeRevealView = null
        captionWindowParams = null
        controlWindowParams = null
        edgeWindowParams = null
        windowManager = null

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
        private const val CONTROL_IDLE_HIDE_MILLIS = 10_000L
        private const val CAPTION_DISPLAY_MILLIS = 5_000L
        private const val SAMPLE_RATE = 16_000
        private const val SEGMENT_SECONDS = 8
        private const val SEGMENT_SAMPLES = SAMPLE_RATE * SEGMENT_SECONDS
    }
}
