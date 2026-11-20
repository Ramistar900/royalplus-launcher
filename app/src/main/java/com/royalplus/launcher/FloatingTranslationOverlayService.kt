package com.royalplus.launcher

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Explicitly started local-caption service. Android 10+ captures eligible app playback;
 * Android 8/9 use the device microphone. Speech recognition runs on-device and audio is never uploaded.
 */
class FloatingTranslationOverlayService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    // Four-window FIFO bounds memory while retaining the newest live audio.
    private val audioSegments = ArrayBlockingQueue<FloatArray>(4)
    private val audioGapDetected = AtomicBoolean(false)

    @Volatile private var recognizer: OfflineRecognizer? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var projectionResultCode: Int = Activity.RESULT_CANCELED
    private var projectionResultData: Intent? = null
    @Volatile private var phoneCompanionMode = false
    @Volatile private var captionSender: RoyalCaptionSender? = null
    private var phoneTargetHost: String? = null
    private var phonePairingPin: String? = null
    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var consecutiveSilentSegments = 0
    private var consecutiveUnrecognizedSegments = 0
    private var lastRecognizedTail: List<String> = emptyList()
    private var lastRecognizedLanguage: String? = null
    private var consecutiveSlowRecognitionSegments = 0
    private var lastQueueOverflowNoticeElapsedRealtime = 0L
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
    private data class PendingCaption(
        val text: String,
        val language: String,
        val sourceLanguage: String,
        val attempt: Int = 0
    )

    // One global FIFO preserves spoken order, including when the source language changes.
    private val pendingTranslations = ArrayDeque<PendingCaption>()
    private val translationExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "RoyalSmall100Inference").apply { isDaemon = true }
    }
    private val translationModelSetup = AtomicBoolean(false)
    private val translationCapabilityFailed = AtomicBoolean(false)
    @Volatile private var small100Translator: Small100Translator? = null
    @Volatile private var translationModelInstalled = false
    private var translationModelThread: Thread? = null
    private var translationInFlight = false
    private var lastUnsupportedLanguageNotice: String? = null
    @Volatile private var translationDownloadNoticeShown = false
    private val captionPhraseBuffer = CaptionPhraseBuffer(maxCharacters = MAX_CAPTION_BUFFER_CHARACTERS)
    private val captionPhraseFlushRunnable = Runnable { flushPendingCaptionPhrase() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_SHOW && running.get()) {
            revealControl()
            updateNotification("الترجمة جاهزة؛ شغّل صوتاً لبدء الترجمة")
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) START_NOT_STICKY else START_STICKY
        }
        if (intent?.action == ACTION_DOWNLOAD_TRANSLATION_MODEL && running.get()) {
            startTranslationModelDownload()
            return START_NOT_STICKY
        }

        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        phoneCompanionMode = intent?.action == ACTION_START_PHONE_CAPTURE ||
            (intent == null && preferences.getBoolean(KEY_PHONE_CAPTURE_ENABLED, false))
        if (phoneCompanionMode) {
            if (TranslationDeviceRoleDetector.detect(this) != TranslationDeviceRole.PHONE_CAPTURE) {
                stopSelf()
                return START_NOT_STICKY
            }
            phoneTargetHost = intent?.getStringExtra(EXTRA_TV_HOST)
                ?: preferences.getString(KEY_PHONE_TV_HOST, null)
            phonePairingPin = intent?.getStringExtra(EXTRA_TV_PIN)
                ?: preferences.getString(KEY_PHONE_TV_PIN, null)
            val host = phoneTargetHost
            val pin = phonePairingPin
            if (host.isNullOrBlank() || pin.isNullOrBlank() ||
                !RoyalCaptionTransport.isPrivateIpv4Address(host) ||
                !pin.matches(Regex("\\d{8}"))
            ) {
                preferences.edit().putBoolean(KEY_PHONE_CAPTURE_ENABLED, false).apply()
                stopSelf()
                return START_NOT_STICKY
            }
            preferences.edit()
                .putBoolean(KEY_PHONE_CAPTURE_ENABLED, true)
                .putString(KEY_PHONE_TV_HOST, host)
                .putString(KEY_PHONE_TV_PIN, pin)
                .apply()
            captionSender = runCatching {
                RoyalCaptionSender(host, pin) { connected, message ->
                    if (running.get()) updateNotification(message)
                }
            }.getOrElse {
                updateNotification(it.localizedMessage ?: "تعذر تجهيز اتصال التلفاز")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val hardwareAssessment = TranslationDeviceCapability.checkHardware(applicationContext)
        if (!hardwareAssessment.supported) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, false).apply()
            sendBroadcast(
                Intent(ACTION_DEVICE_CAPABILITY_FAILED)
                    .setPackage(packageName)
                    .putExtra(EXTRA_CAPABILITY_MESSAGE, hardwareAssessment.message)
                    .putExtra(EXTRA_CAPABILITY_TITLE, hardwareAssessment.title)
            )
            android.widget.Toast.makeText(
                applicationContext,
                hardwareAssessment.message,
                android.widget.Toast.LENGTH_LONG
            ).show()
            stopSelf()
            return START_NOT_STICKY
        }

        if (!phoneCompanionMode && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return START_NOT_STICKY
        }

        val usePlaybackCapture = !phoneCompanionMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        if (usePlaybackCapture) {
            projectionResultCode = intent?.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, Activity.RESULT_CANCELED)
                ?: Activity.RESULT_CANCELED
            projectionResultData = intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
            if (projectionResultCode != Activity.RESULT_OK || projectionResultData == null) {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        createNotificationChannel()
        startTranslationForeground(usePlaybackCapture)
        if (phoneCompanionMode) {
            acquirePhoneBackgroundLocks()
        } else if (captionView == null) {
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
        return if (usePlaybackCapture) START_NOT_STICKY else START_STICKY
    }

    /**
     * Keeps the phone CPU and Wi-Fi radio awake during an explicitly started
     * microphone foreground session. The screen itself is never kept on.
     */
    @Suppress("DEPRECATION")
    private fun acquirePhoneBackgroundLocks() {
        runCatching {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            cpuWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ROYALPlus:PhoneCaptionCapture"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        runCatching {
            val manager = getSystemService(WIFI_SERVICE) as? WifiManager ?: return@runCatching
            wifiLock = manager.createWifiLock(
                WifiManager.WIFI_MODE_FULL,
                "ROYALPlus:PhoneCaptionWiFi"
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun prepareRecognizer() {
        try {
            // Start playback capture immediately: Android's MediaProjection grant must be
            // consumed before any model download or other potentially slow setup.
            if (!startAudioCapture()) return

            val engine = WhisperModelRepository.createRecognizer(applicationContext) { message ->
                updateStatus(message)
            }
            if (!running.get()) {
                engine.release()
                return
            }

            recognizer = engine
            updateStatus("اكتمل تجهيز التعرّف على الكلام؛ يجري التحقق من نموذج الترجمة المحلي…")
            prepareInstalledTranslationEngine()
            decodeThread = Thread({ decodeAudioSegments() }, "RoyalWhisperDecode").apply { start() }
        } catch (error: OutOfMemoryError) {
            System.gc()
            failTranslationForCapability(
                TranslationDeviceCapability.insufficientMemory(
                    "ذاكرة الجهاز لا تكفي لتجهيز محرك التعرّف على الكلام والترجمة معاً."
                )
            )
        } catch (error: Exception) {
            if (running.get()) {
                val detail = error.localizedMessage ?: "تحقق من الإنترنت والمساحة"
                updateStatus(
                    if (error is InterruptedException) "تم إيقاف تجهيز الترجمة"
                    else "تعذر تجهيز محرك التعرّف على الكلام: $detail"
                )
                mainHandler.postDelayed({ stopSelf() }, 4_000L)
            }
        }
    }

    private fun startAudioCapture(): Boolean {
        val recorder = try {
            if (phoneCompanionMode) createMicrophoneRecord()
            else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) createPlaybackCaptureRecord()
            else createMicrophoneRecord()
        } catch (error: Exception) {
            updateStatus(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "تعذر التقاط صوت التطبيق: ${error.localizedMessage ?: "لم يمنح النظام إذن مشاركة الصوت"}"
                } else {
                    "تعذر تشغيل ميكروفون التلفاز: ${error.localizedMessage ?: "تحقق من وجود ميكروفون ومنح الإذن"}"
                }
            )
            mainHandler.postDelayed({ stopSelf() }, 8_000L)
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            updateStatus("لم يتمكن الجهاز من تهيئة مصدر الصوت")
            mainHandler.postDelayed({ stopSelf() }, 5_000L)
            return false
        }
        audioRecord = recorder
        updateStatus(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "تم ربط التقاط صوت التطبيقات. جارٍ تجهيز محرك الترجمة…"
            } else {
                "Android 8/9: الترجمة تستمع عبر ميكروفون التلفاز؛ ارفع صوت الفيديو وتأكد من وجود ميكروفون فعلي."
            }
        )
        captureThread = Thread({ captureAudio(recorder) }, "RoyalAudioCapture").apply { start() }
        return true
    }

    @Suppress("NewApi")
    @android.annotation.SuppressLint("MissingPermission")
    private fun createPlaybackCaptureRecord(): AudioRecord {
        // Re-check at the exact privileged API boundary; service startup checks are not
        // sufficient for lint or for permission changes while the service is alive.
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("يلزم إذن الميكروفون لالتقاط صوت التطبيقات")
        }
        val data = projectionResultData ?: throw IllegalStateException("يلزم موافقة النظام على التقاط الصوت")
        val manager = getSystemService(MediaProjectionManager::class.java)
            ?: throw IllegalStateException("خدمة مشاركة الشاشة والصوت غير متاحة")
        val projection = manager.getMediaProjection(projectionResultCode, data)
            ?: throw IllegalStateException("رفض النظام إذن التقاط الصوت")
        mediaProjection = projection
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                updateStatus("أوقف النظام مشاركة صوت التطبيقات. أعد تشغيل الترجمة لمنح الإذن مجدداً.")
                stopSelf()
            }
        }
        mediaProjectionCallback = callback
        projection.registerCallback(callback, mainHandler)

        val captureConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IllegalStateException("إعداد صوت النظام غير مدعوم على هذا الجهاز")
        return AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(maxOf(minBuffer * 3, SAMPLE_RATE * 4))
            .setAudioPlaybackCaptureConfig(captureConfig)
            .build()
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun createMicrophoneRecord(): AudioRecord {
        // Runtime permission can be revoked after service startup; check again immediately
        // before either microphone-backed AudioRecord constructor.
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("يلزم منح إذن الميكروفون لتشغيل الترجمة")
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IllegalStateException("الميكروفون لا يدعم معدل الصوت المطلوب")
        val bufferSize = maxOf(minBuffer * 3, SAMPLE_RATE * 4)
        val voiceRecorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull()
        if (voiceRecorder?.state == AudioRecord.STATE_INITIALIZED) return voiceRecorder
        runCatching { voiceRecorder?.release() }
        // Some Android TV firmware exposes only the generic microphone source.
        return AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    /**
     * Use fixed two-second windows with overlap. This is the capture path that
     * previously worked on the phone and does not depend on a second speech detector.
     */
    private fun captureAudio(recorder: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val input = ShortArray(CAPTURE_READ_SAMPLES)
        val segment = FloatArray(SEGMENT_SAMPLES)
        var filled = 0
        try {
            recorder.startRecording()
            updateNotification(
                if (phoneCompanionMode) "الهاتف يلتقط صوت التلفاز ويرسل الترجمة حتى عند إطفاء الشاشة"
                else "يلتقط ROYAL+ صوت الفيديو الآن؛ يجري تحليل المقاطع محلياً"
            )
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(input, 0, input.size)
                if (count > 0) {
                    for (index in 0 until count) {
                        segment[filled++] = input[index] / 32768.0f
                        if (filled == SEGMENT_SAMPLES) {
                            val completed = segment.copyOf()
                            var accepted = audioSegments.offer(completed)
                            if (!accepted) {
                                // Bound memory while keeping the newest audio when the
                                // recognizer is slower than live playback.
                                audioSegments.poll()
                                audioGapDetected.set(true)
                                accepted = audioSegments.offer(completed)
                                val now = SystemClock.elapsedRealtime()
                                if (
                                    accepted &&
                                    now - lastQueueOverflowNoticeElapsedRealtime >= BACKLOG_NOTICE_INTERVAL_MILLIS
                                ) {
                                    lastQueueOverflowNoticeElapsedRealtime = now
                                    updateNotification("سرعة التعرّف أقل من سرعة الفيديو؛ تتم متابعة أحدث مقاطع الصوت.")
                                }
                            }

                            // Preserve the last 350 ms so words crossing a window boundary are not clipped.
                            System.arraycopy(
                                segment,
                                SEGMENT_SAMPLES - SEGMENT_OVERLAP_SAMPLES,
                                segment,
                                0,
                                SEGMENT_OVERLAP_SAMPLES
                            )
                            filled = SEGMENT_OVERLAP_SAMPLES
                        }
                    }
                } else if (count < 0) {
                    updateStatus("انقطع اتصال الصوت؛ أعد تشغيل الترجمة")
                    mainHandler.postDelayed({ stopSelf() }, 3_000L)
                    break
                }
            }
        } catch (error: Exception) {
            if (running.get()) {
                val detail = error.localizedMessage ?: "خطأ في التقاط الصوت"
                updateStatus("توقف التقاط الصوت: $detail")
                mainHandler.postDelayed({ stopSelf() }, 3_000L)
            }
        } finally {
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: Exception) {
                // The platform may already have stopped recording during service shutdown.
            }
        }
    }

    private fun hasMeaningfulAudio(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return false
        var squaredEnergy = 0.0
        var peak = 0f
        for (sample in samples) {
            val magnitude = kotlin.math.abs(sample)
            if (magnitude > peak) peak = magnitude
            squaredEnergy += sample * sample
        }
        val rms = kotlin.math.sqrt(squaredEnergy / samples.size).toFloat()
        return peak >= 0.0035f && rms >= 0.00075f
    }

    /** Remove repeated leading words introduced by the 350 ms overlapping audio windows. */
    private fun removeOverlappingWords(text: String): String {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return ""
        fun normalized(word: String): String =
            word.lowercase(Locale.ROOT).trim { !it.isLetterOrDigit() }

        val previous = lastRecognizedTail
        var overlap = 0
        val maximum = minOf(MAX_OVERLAP_WORDS, previous.size, words.size)
        for (candidate in maximum downTo 1) {
            if (
                previous.takeLast(candidate).map(::normalized) ==
                words.take(candidate).map(::normalized)
            ) {
                overlap = candidate
                break
            }
        }
        val freshWords = words.drop(overlap)
        if (freshWords.isNotEmpty()) {
            lastRecognizedTail = (previous + freshWords).takeLast(MAX_OVERLAP_WORDS)
        }
        return freshWords.joinToString(" ")
    }

    private fun decodeAudioSegments() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                val samples = audioSegments.take()
                if (audioGapDetected.getAndSet(false)) {
                    lastRecognizedTail = emptyList()
                    lastRecognizedLanguage = null
                }

                if (!hasMeaningfulAudio(samples)) {
                    if (consecutiveSilentSegments == 0) {
                        mainHandler.post { flushPendingCaptionPhrase() }
                    }
                    consecutiveSilentSegments++
                    if (consecutiveSilentSegments == 2) {
                        lastRecognizedTail = emptyList()
                        lastRecognizedLanguage = null
                        updateStatus(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                "لا يصل صوت واضح من التطبيق إلى ROYAL+. تحقق من إذن مشاركة صوت الشاشة."
                            } else {
                                "لم يلتقط الميكروفون صوتاً واضحاً؛ قرّب الهاتف أو التلفاز من مصدر الصوت."
                            }
                        )
                    }
                    continue
                }
                consecutiveSilentSegments = 0

                val engine = recognizer ?: continue
                val stream = engine.createStream()
                var speechRecognitionTooSlow = false
                val result = try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    val audioDurationMillis = samples.size.toLong() * 1_000L / SAMPLE_RATE
                    val decodeStartedAt = SystemClock.elapsedRealtime()
                    engine.decode(stream)
                    val decodeElapsedMillis = SystemClock.elapsedRealtime() - decodeStartedAt
                    if (audioDurationMillis >= 1_500L) {
                        val slowDecodeLimit = maxOf(5_000L, audioDurationMillis * 2L)
                        if (decodeElapsedMillis > slowDecodeLimit) {
                            consecutiveSlowRecognitionSegments++
                        } else {
                            consecutiveSlowRecognitionSegments = 0
                        }
                        speechRecognitionTooSlow = consecutiveSlowRecognitionSegments >= 3
                    } else {
                        consecutiveSlowRecognitionSegments = 0
                    }
                    engine.getResult(stream)
                } finally {
                    stream.release()
                }

                if (speechRecognitionTooSlow) {
                    failTranslationForCapability(TranslationDeviceCapability.slowSpeechRecognition())
                    break
                }

                val recognized = result.text.replace(Regex("\\s+"), " ").trim()
                when {
                    recognized.isBlank() -> {
                        consecutiveUnrecognizedSegments++
                        if (consecutiveUnrecognizedSegments == 3) {
                            updateStatus("وصل صوت إلى محرك التعرّف، لكنه لم يتعرف على كلام واضح. تأكد من وضوح الحوار.")
                        }
                    }
                    SpeechCaptionFilter.isNonSpeechLabel(recognized) -> {
                        lastRecognizedTail = emptyList()
                        lastRecognizedLanguage = null
                        mainHandler.post { flushPendingCaptionPhrase() }
                        consecutiveUnrecognizedSegments++
                    }
                    else -> {
                        val language = normalizeDetectedLanguage(result.lang)
                        if (language.isBlank()) {
                            updateStatus("وصل كلام، لكن تعذّر تحديد لغته.")
                        } else {
                            if (lastRecognizedLanguage != null && lastRecognizedLanguage != language) {
                                lastRecognizedTail = emptyList()
                            }
                            lastRecognizedLanguage = language
                            val freshText = removeOverlappingWords(recognized)
                            if (freshText.isNotBlank()) {
                                consecutiveUnrecognizedSegments = 0
                                queueCaptionPhrase(freshText, language)
                            }
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: OutOfMemoryError) {
                System.gc()
                failTranslationForCapability(
                    TranslationDeviceCapability.insufficientMemory(
                        "نفدت ذاكرة الجهاز أثناء التعرّف على الكلام؛ أوقفت الترجمة لحماية استقرار ROYAL+."
                    )
                )
                break
            } catch (error: Exception) {
                val detail = error.localizedMessage ?: "خطأ في محرك التعرّف"
                updateStatus("تعذّر تحليل الصوت: $detail")
            }
        }
    }

    private fun queueCaptionPhrase(text: String, language: String) {
        mainHandler.post {
            if (!running.get()) return@post

            captionPhraseBuffer.append(text, language).forEach { phrase ->
                translateCaption(phrase.text, phrase.language)
            }

            mainHandler.removeCallbacks(captionPhraseFlushRunnable)
            if (!captionPhraseBuffer.isEmpty()) {
                mainHandler.postDelayed(captionPhraseFlushRunnable, CAPTION_PHRASE_IDLE_MILLIS)
            }
        }
    }

    private fun flushPendingCaptionPhrase() {
        val phrase = captionPhraseBuffer.flush() ?: return
        translateCaption(phrase.text, phrase.language)
    }

    /**
     * Whisper detects the source language automatically. SMaLL-100 checks its own
     * 100-language token map after its local model has been installed.
     */
    private fun normalizeDetectedLanguage(rawLanguage: String): String {
        val detected = rawLanguage.trim()
            .lowercase(Locale.ROOT)
            .replace('_', '-')
            .substringBefore('-')
        // Whisper and ISO language registries use a few different legacy aliases.
        return when (detected) {
            "jw" -> "jv"
            "yue" -> "zh"
            "fil" -> "tl"
            "nb", "nn" -> "no"
            else -> detected
        }
    }

    private fun translateCaption(text: String, detectedLanguage: String) {
        mainHandler.post {
            if (!running.get()) return@post
            val normalizedLanguage = normalizeDetectedLanguage(detectedLanguage)
            if (normalizedLanguage.isBlank()) return@post

            pendingTranslations.addLast(
                PendingCaption(
                    text = text,
                    language = normalizedLanguage,
                    sourceLanguage = normalizedLanguage
                )
            )
            processNextPendingTranslation()
        }
    }

    /**
     * Local SMaLL-100 translation runs on one worker so sentence order is preserved
     * without blocking audio capture, the overlay, or the Android main thread.
     */
    private fun processNextPendingTranslation() {
        if (!running.get() || translationInFlight) return
        val pending = pendingTranslations.peekFirst() ?: return

        if (pending.language == "ar") {
            pendingTranslations.removeFirst()
            showCaption(pending.text, pending.language)
            processNextPendingTranslation()
            return
        }

        val engine = small100Translator
        if (engine == null) {
            if (translationModelInstalled) {
                prepareInstalledTranslationEngine()
            } else {
                if (!translationDownloadNoticeShown) {
                    translationDownloadNoticeShown = true
                    updateStatus("اكتمل نموذج التعرّف الصوتي. بقي نموذج الترجمة العربي المحلي (~621MB): افتح إشعار ROYAL+ واضغط «تنزيل الترجمة». الحزمتان منفصلتان.")
                } else {
                    updateNotification("نموذج الترجمة المحلي (~621MB) غير مثبت بعد؛ اضغط «تنزيل الترجمة» في إشعار ROYAL+.")
                }
            }
            return
        }

        if (!engine.supportsSourceLanguage(pending.sourceLanguage)) {
            pendingTranslations.removeFirst()
            if (lastUnsupportedLanguageNotice != pending.sourceLanguage) {
                lastUnsupportedLanguageNotice = pending.sourceLanguage
                updateStatus(
                    "لغة المصدر ${pending.sourceLanguage} غير مدعومة في النموذج المحلي؛ لم يُرسل النص إلى أي خدمة خارجية."
                )
            }
            processNextPendingTranslation()
            return
        }

        translationInFlight = true
        translationExecutor.execute {
            val translated = runCatching { engine.translate(pending.text, "ar") }
            mainHandler.post {
                if (!running.get()) return@post
                if (pendingTranslations.peekFirst() === pending) {
                    pendingTranslations.removeFirst()
                }
                translationInFlight = false
                translated.onSuccess { caption ->
                    lastUnsupportedLanguageNotice = null
                    if (caption.isNotBlank()) {
                        showCaption(caption, pending.language)
                    } else {
                        updateStatus("وصلت العبارة إلى محرك الترجمة، لكنه لم يُنتج نصاً عربياً لهذه الجملة.")
                    }
                    processNextPendingTranslation()
                }.onFailure { error ->
                    pendingTranslations.addFirst(pending.copy(attempt = pending.attempt + 1))
                    if (error is OutOfMemoryError) {
                        System.gc()
                        failTranslationForCapability(
                            TranslationDeviceCapability.insufficientMemory(
                                "نفدت ذاكرة الجهاز أثناء ترجمة الكلام. تم إيقاف الترجمة لحماية استقرار التطبيق."
                            )
                        )
                    } else {
                        val detail = error.localizedMessage ?: "خطأ محلي في محرك ONNX"
                        updateStatus("تعذرت الترجمة المحلية؛ حُفظت الجملة لإعادة المحاولة: $detail")
                        mainHandler.postDelayed(
                            { processNextPendingTranslation() },
                            TRANSLATION_RETRY_DELAY_MILLIS
                        )
                    }
                }
            }
        }
    }

    private fun prepareInstalledTranslationEngine() {
        if (!running.get() || small100Translator != null || translationCapabilityFailed.get()) return
        if (!translationModelSetup.compareAndSet(false, true)) return
        translationModelThread = Thread({
            try {
                val files = Small100ModelRepository.installedFiles(applicationContext)
                if (files == null) {
                    translationModelInstalled = false
                    translationModelSetup.set(false)
                    translationDownloadNoticeShown = true
                    updateStatus("اكتمل نموذج التعرّف الصوتي. لتنزيل نموذج الترجمة العربي المحلي (~621MB)، افتح إشعار ROYAL+ واضغط «تنزيل الترجمة» عبر Wi-Fi.")
                    return@Thread
                }
                val engine = createVerifiedTranslationEngine(files) ?: return@Thread
                if (!running.get()) {
                    engine.close()
                    translationModelSetup.set(false)
                    return@Thread
                }
                mainHandler.post {
                    if (!running.get()) {
                        engine.close()
                        translationModelSetup.set(false)
                        return@post
                    }
                    small100Translator = engine
                    translationModelInstalled = true
                    translationModelSetup.set(false)
                    translationDownloadNoticeShown = false
                    updateStatus("محرك الترجمة المحلية جاهز. شغّل الفيديو لعرض الترجمة العربية.")
                    processNextPendingTranslation()
                }
            } catch (error: OutOfMemoryError) {
                System.gc()
                failTranslationForCapability(
                    TranslationDeviceCapability.insufficientMemory(
                        "لم تتوفر ذاكرة كافية لتهيئة نموذج الترجمة."
                    )
                )
            } catch (error: Exception) {
                translationModelSetup.set(false)
                val detail = error.localizedMessage ?: "تعذر التحقق من ملفات نموذج الترجمة"
                updateStatus("تعذر تجهيز نموذج الترجمة المحلي: $detail")
            }
        }, "RoyalSmall100Setup").apply { isDaemon = true; start() }
    }

    private fun startTranslationModelDownload() {
        if (!running.get() || small100Translator != null || translationCapabilityFailed.get()) return
        if (!translationModelSetup.compareAndSet(false, true)) {
            updateNotification("تنزيل أو تجهيز نموذج الترجمة جارٍ بالفعل.")
            return
        }
        translationModelThread = Thread({
            try {
                val capacity = TranslationDeviceCapability.checkBeforeModelLoad(applicationContext)
                if (!capacity.supported) {
                    failTranslationForCapability(capacity)
                    return@Thread
                }
                val files = Small100ModelRepository.ensureDownloaded(applicationContext) { progress ->
                    updateNotification("تنزيل نموذج الترجمة المحلي — $progress")
                }
                val engine = createVerifiedTranslationEngine(files) ?: return@Thread
                if (!running.get()) {
                    engine.close()
                    translationModelSetup.set(false)
                    return@Thread
                }
                mainHandler.post {
                    if (!running.get()) {
                        engine.close()
                        translationModelSetup.set(false)
                        return@post
                    }
                    small100Translator = engine
                    translationModelInstalled = true
                    translationModelSetup.set(false)
                    translationDownloadNoticeShown = false
                    updateStatus("اكتمل تنزيل نموذج الترجمة واجتاز اختبار السرعة. الترجمة العربية جاهزة.")
                    processNextPendingTranslation()
                }
            } catch (error: OutOfMemoryError) {
                System.gc()
                failTranslationForCapability(
                    TranslationDeviceCapability.insufficientMemory(
                        "نفدت الذاكرة أثناء تنزيل نموذج الترجمة أو تهيئته."
                    )
                )
            } catch (error: Exception) {
                translationModelSetup.set(false)
                val detail = error.localizedMessage ?: "تحقق من الاتصال والمساحة الحرة"
                updateStatus("تعذر تنزيل نموذج الترجمة: $detail")
            }
        }, "RoyalSmall100Download").apply { isDaemon = true; start() }
    }

    /**
     * Avoids wasting a 621 MB download on devices that cannot load the model,
     * then verifies real local inference before declaring translation ready.
     */
    private fun createVerifiedTranslationEngine(
        files: Small100ModelRepository.ModelFiles
    ): Small100Translator? {
        val capacity = TranslationDeviceCapability.checkBeforeModelLoad(applicationContext)
        if (!capacity.supported) {
            failTranslationForCapability(capacity)
            return null
        }

        val engine = try {
            Small100Translator.create(files)
        } catch (error: OutOfMemoryError) {
            System.gc()
            failTranslationForCapability(
                TranslationDeviceCapability.insufficientMemory(
                    "لم تكفِ ذاكرة الجهاز لتحميل نموذج الترجمة."
                )
            )
            return null
        } catch (error: Exception) {
            failTranslationForCapability(
                TranslationDeviceCapability.engineUnavailable(
                    error.localizedMessage ?: "تعذر تهيئة محرك ONNX على هذا الجهاز."
                )
            )
            return null
        }

        updateNotification("اكتمل تنزيل النموذج؛ يجري الآن اختبار سرعة الترجمة المحلية وقدرة الجهاز.")
        val assessment = TranslationDeviceCapability.benchmark(applicationContext, engine)
        if (!assessment.supported) {
            runCatching { engine.close() }
            failTranslationForCapability(assessment)
            return null
        }
        return engine
    }

    private fun failTranslationForCapability(
        assessment: TranslationDeviceCapability.Assessment
    ) {
        if (!translationCapabilityFailed.compareAndSet(false, true)) return
        translationModelSetup.set(false)
        running.set(false)
        mainHandler.post {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, false).apply()
            updateStatus(assessment.message)
            sendBroadcast(
                Intent(ACTION_DEVICE_CAPABILITY_FAILED)
                    .setPackage(packageName)
                    .putExtra(EXTRA_CAPABILITY_MESSAGE, assessment.message)
                    .putExtra(EXTRA_CAPABILITY_TITLE, assessment.title)
            )
            // Keep the on-screen explanation visible before releasing the overlay and audio resources.
            mainHandler.postDelayed({ stopSelf() }, STATUS_DISPLAY_MILLIS + 500L)
        }
    }

    private fun showCaption(text: String, language: String) {
        mainHandler.post {
            val safeArabicCaption = ArabicCaptionFormatter.format(text)
            if (phoneCompanionMode) {
                if (safeArabicCaption.isBlank()) {
                    updateNotification("تم التعرف على الكلام، لكن الترجمة العربية غير صالحة للإرسال.")
                    return@post
                }
                captionSender?.sendCaption(safeArabicCaption, language)
                return@post
            }
            val caption = captionView ?: return@post
            if (safeArabicCaption.isBlank()) {
                updateStatus("تم التعرف على الكلام، لكن محرك الترجمة لم يُرجع نصاً عربياً صالحاً.")
                return@post
            }
            caption.text = safeArabicCaption
            caption.visibility = View.VISIBLE
            mainHandler.removeCallbacks(captionHideRunnable)
            mainHandler.postDelayed(captionHideRunnable, CAPTION_DISPLAY_MILLIS)
            updateNotification("تمت الترجمة إلى العربية")
        }
    }

    private fun updateStatus(message: String) {
        updateNotification(message)
        mainHandler.post {
            val caption = captionView ?: return@post
            caption.text = message
            caption.visibility = View.VISIBLE
            mainHandler.removeCallbacks(captionHideRunnable)
            mainHandler.postDelayed(captionHideRunnable, STATUS_DISPLAY_MILLIS)
        }
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
            textSize = 22f
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            breakStrategy = android.graphics.text.LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
            hyphenationFrequency = android.text.Layout.HYPHENATION_FREQUENCY_NONE
            includeFontPadding = false
            // Two-line film-style subtitles with balanced, symmetric screen margins.
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            visibility = View.GONE
            setShadowLayer(dp(1).toFloat(), 0f, dp(1).toFloat(), 0x99000000.toInt())
            contentDescription = "الترجمة العربية"
        }
        val captionParams = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.90f).toInt().coerceAtLeast(dp(240)),
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
            try {
                manager.addView(control, params)
                controlView = control
                controlWindowParams = params
            } catch (_: Exception) {
                // If the system rejects the control window, restore the edge handle.
                showEdgeReveal()
                return
            }
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
            text = "CC"
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            isClickable = true
            contentDescription = "المس لإظهار أدوات الترجمة"
            background = solidRoundedBackground(0xCC1A1F28.toInt(), dp(10).toFloat())
            setOnClickListener { revealControl() }
        }
        val params = WindowManager.LayoutParams(
            dp(34),
            dp(88),
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
        mainHandler.removeCallbacks(controlHideRunnable)
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

    private fun startTranslationForeground(usePlaybackCapture: Boolean) {
        val message = when {
            phoneCompanionMode -> "تهيئة ميكروفون الهاتف والاتصال بالتلفاز"
            usePlaybackCapture -> "تهيئة التقاط صوت التطبيقات"
            else -> "تهيئة ميكروفون الجهاز"
        }
        val notification = buildNotification(message)
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && usePlaybackCapture -> {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && phoneCompanionMode -> {
                // The microphone foreground-service type was added in Android 11 (API 30).
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            }
            else -> {
                // Android 7–10 use the compatible two-argument foreground-service API.
                startForeground(NOTIFICATION_ID, notification)
            }
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
            .apply {
                if (!translationModelInstalled && !translationCapabilityFailed.get()) {
                    val downloadIntent = Intent(this@FloatingTranslationOverlayService, FloatingTranslationOverlayService::class.java)
                        .setAction(ACTION_DOWNLOAD_TRANSLATION_MODEL)
                    val downloadPendingIntent = PendingIntent.getService(
                        this@FloatingTranslationOverlayService,
                        4104,
                        downloadIntent,
                        pendingFlags
                    )
                    addAction(Notification.Action.Builder(
                        android.R.drawable.stat_sys_download,
                        "تنزيل الترجمة (~621MB)",
                        downloadPendingIntent
                    ).build())
                }
            }
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
        captureThread?.interrupt()
        decodeThread?.interrupt()
        modelThread?.interrupt()
        audioSegments.clear()
        audioGapDetected.set(false)

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
            }
        } catch (_: Exception) {
            // The capture device may already have been released by the system.
        }
        audioRecord = null
        captionSender?.close()
        captionSender = null
        runCatching { if (cpuWakeLock?.isHeld == true) cpuWakeLock?.release() }
        cpuWakeLock = null
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wifiLock = null
        val projection = mediaProjection
        val projectionCallback = mediaProjectionCallback
        mediaProjection = null
        mediaProjectionCallback = null
        if (projection != null) {
            if (projectionCallback != null) runCatching { projection.unregisterCallback(projectionCallback) }
            runCatching { projection.stop() }
        }
        projectionResultData = null

        val engineToRelease = recognizer
        recognizer = null
        if (engineToRelease != null) {
            val decoder = decodeThread
            if (decoder == null || !decoder.isAlive) {
                try {
                    engineToRelease.release()
                } catch (_: Exception) {
                    // Native recognizer cleanup must not prevent the service from stopping.
                }
            } else {
                // sherpa-onnx owns native resources; release only after the decode call exits.
                Thread({
                    var interrupted = false
                    while (decoder.isAlive) {
                        try {
                            decoder.join()
                        } catch (_: InterruptedException) {
                            interrupted = true
                        }
                    }
                    try {
                        engineToRelease.release()
                    } catch (_: Exception) {
                        // Native recognizer cleanup must not prevent the service from stopping.
                    } finally {
                        if (interrupted) Thread.currentThread().interrupt()
                    }
                }, "RoyalWhisperRelease").start()
            }
        }


        mainHandler.removeCallbacks(captionPhraseFlushRunnable)
        captionPhraseBuffer.clear()
        pendingTranslations.clear()
        translationInFlight = false
        translationModelThread?.interrupt()
        translationModelThread = null
        val translationEngineToRelease = small100Translator
        small100Translator = null
        translationExecutor.shutdownNow()
        if (translationEngineToRelease != null) {
            Thread({
                var interrupted = false
                while (!translationExecutor.isTerminated) {
                    try {
                        translationExecutor.awaitTermination(1, TimeUnit.SECONDS)
                    } catch (_: InterruptedException) {
                        interrupted = true
                    }
                }
                runCatching { translationEngineToRelease.close() }
                if (interrupted) Thread.currentThread().interrupt()
            }, "RoyalSmall100Release").apply { isDaemon = true; start() }
        }

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
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .putBoolean(KEY_PHONE_CAPTURE_ENABLED, false)
            .apply()

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
        const val ACTION_START_PHONE_CAPTURE = "com.royalplus.launcher.action.START_PHONE_CAPTION_CAPTURE"
        const val EXTRA_TV_HOST = "royal_caption_tv_host"
        const val EXTRA_TV_PIN = "royal_caption_tv_pin"
        const val KEY_PHONE_CAPTURE_ENABLED = "royal_phone_caption_capture_enabled"
        const val KEY_PHONE_TV_HOST = "royal_phone_caption_tv_host"
        const val KEY_PHONE_TV_PIN = "royal_phone_caption_tv_pin"
        const val ACTION_STOP = "com.royalplus.launcher.action.STOP_TRANSLATION_OVERLAY"
        const val ACTION_DOWNLOAD_TRANSLATION_MODEL = "com.royalplus.launcher.action.DOWNLOAD_TRANSLATION_MODEL"
        const val ACTION_DEVICE_CAPABILITY_FAILED = "com.royalplus.launcher.action.TRANSLATION_DEVICE_CAPABILITY_FAILED"
        const val EXTRA_CAPABILITY_MESSAGE = "translation_capability_message"
        const val EXTRA_CAPABILITY_TITLE = "translation_capability_title"
        const val PREFERENCES = "royalplus_launcher_preferences"
        const val KEY_ENABLED = "floating_translation_overlay_enabled"
        const val EXTRA_PROJECTION_RESULT_CODE = "royal_projection_result_code"
        const val EXTRA_PROJECTION_DATA = "royal_projection_result_data"
        private const val CHANNEL_ID = "royal_translation_overlay"
        private const val NOTIFICATION_ID = 4101
        private const val CAPTION_DISPLAY_MILLIS = 4_500L
        private const val CAPTION_PHRASE_IDLE_MILLIS = 250L
        private const val MAX_CAPTION_BUFFER_CHARACTERS = 160
        private const val STATUS_DISPLAY_MILLIS = 7_000L
        private const val CONTROL_IDLE_HIDE_MILLIS = 10_000L
        private const val SAMPLE_RATE = 16_000
        private const val CAPTURE_READ_SAMPLES = 1_600
        private const val SEGMENT_SECONDS = 2
        private const val SEGMENT_OVERLAP_MILLIS = 350
        private const val SEGMENT_OVERLAP_SAMPLES = SAMPLE_RATE * SEGMENT_OVERLAP_MILLIS / 1_000
        private const val SEGMENT_SAMPLES = SAMPLE_RATE * SEGMENT_SECONDS
        private const val MAX_OVERLAP_WORDS = 4
        private const val BACKLOG_NOTICE_INTERVAL_MILLIS = 10_000L
        private const val TRANSLATION_RETRY_DELAY_MILLIS = 2_500L
    }
}
