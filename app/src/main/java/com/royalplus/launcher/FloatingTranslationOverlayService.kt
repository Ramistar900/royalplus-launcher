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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.k2fsa.sherpa.onnx.AudioTagging
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.Vad
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Explicitly started local-caption service. Android 10+ captures eligible app playback;
 * Android 8/9 use the device microphone. Speech recognition runs on-device and audio is never uploaded.
 */
class FloatingTranslationOverlayService : Service() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    // VAD emits whole speech turns; this FIFO never discards recognized audio.
    private val speechSegments = LinkedBlockingQueue<FloatArray>()
    private val queuedSpeechSamples = AtomicLong(0L)

    @Volatile private var recognizer: OfflineRecognizer? = null
    @Volatile private var voiceActivityDetector: Vad? = null
    @Volatile private var audioTagger: AudioTagging? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var projectionResultCode: Int = Activity.RESULT_CANCELED
    private var projectionResultData: Intent? = null
    private var consecutiveUnrecognizedSegments = 0
    private var lastBacklogNoticeElapsedRealtime = 0L
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
    @Volatile private var small100Translator: Small100Translator? = null
    @Volatile private var translationModelInstalled = false
    private var translationModelThread: Thread? = null
    private var translationInFlight = false
    private var lastUnsupportedLanguageNotice: String? = null
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

        val usePlaybackCapture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
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
        return if (usePlaybackCapture) START_NOT_STICKY else START_STICKY
    }

    private fun prepareRecognizer() {
        try {
            // Load the small on-device speech detector; Whisper remains the multilingual ASR.
            val detector = WhisperModelRepository.createVoiceActivityDetector(applicationContext) { message ->
                updateStatus(message)
            }
            if (!running.get()) {
                detector.release()
                return
            }
            voiceActivityDetector = detector

            // Keep the existing MediaProjection startup path, then load Whisper off the UI thread.
            if (!startAudioCapture()) {
                voiceActivityDetector = null
                detector.release()
                return
            }

            val engine = WhisperModelRepository.createRecognizer(applicationContext) { message ->
                updateStatus(message)
            }
            if (!running.get()) {
                engine.release()
                return
            }
            val tagger = try {
                WhisperModelRepository.createAudioTagger(applicationContext) { message ->
                    updateStatus(message)
                }
            } catch (error: Exception) {
                val detail = error.localizedMessage ?: "تعذر تحميل نموذج التصنيف"
                updateNotification("تعذر تجهيز مصنف الموسيقى؛ سيستمر كشف الكلام الأساسي: " + detail)
                null
            }
            if (!running.get()) {
                runCatching { tagger?.release() }
                engine.release()
                return
            }
            recognizer = engine
            audioTagger = tagger
            prepareInstalledTranslationEngine()
            decodeThread = Thread({ decodeSpeechSegments() }, "RoyalWhisperDecode").apply { start() }
        } catch (error: Exception) {
            if (running.get()) {
                val detail = error.localizedMessage ?: "تحقق من الإنترنت والمساحة"
                updateStatus(if (error is InterruptedException) "تم إيقاف تجهيز الترجمة" else "تعذر تجهيز محرك الكلام: " + detail)
                mainHandler.postDelayed({ stopSelf() }, 4_000L)
            }
        }
    }

    private fun startAudioCapture(): Boolean {
        val recorder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) createPlaybackCaptureRecord()
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

    private fun captureAudio(recorder: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        val detector = voiceActivityDetector
        if (detector == null) {
            updateStatus("تعذر بدء كاشف الكلام")
            mainHandler.postDelayed({ stopSelf() }, 2_000L)
            return
        }

        val input = ShortArray(CAPTURE_READ_SAMPLES)
        val frame = FloatArray(VAD_WINDOW_SAMPLES)
        var frameFilled = 0
        try {
            recorder.startRecording()
            updateNotification("الترجمة تستمع باستمرار؛ ستظهر الجمل بعد اكتمال الكلام")
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val count = recorder.read(input, 0, input.size)
                if (count > 0) {
                    for (index in 0 until count) {
                        frame[frameFilled++] = input[index] / 32768.0f
                        if (frameFilled == VAD_WINDOW_SAMPLES) {
                            detector.acceptWaveform(frame)
                            enqueueReadySpeechSegments(detector)
                            frameFilled = 0
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
                val detail = error.localizedMessage ?: "خطأ في الميكروفون"
                updateStatus("توقف التقاط الصوت: " + detail)
                mainHandler.postDelayed({ stopSelf() }, 3_000L)
            }
        } finally {
            try {
                // Preserve the final partial VAD frame and flush the final speech turn.
                if (frameFilled > 0) {
                    while (frameFilled < frame.size) {
                        frame[frameFilled++] = 0f
                    }
                    detector.acceptWaveform(frame)
                }
                detector.flush()
                enqueueReadySpeechSegments(detector)
            } catch (_: Exception) {
                // Shutdown must still release the recorder and detector.
            }
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            } catch (_: Exception) {
                // The platform may have stopped recording during service shutdown.
            }
            if (voiceActivityDetector === detector) {
                voiceActivityDetector = null
            }
            runCatching { detector.release() }
        }
    }

    private fun enqueueReadySpeechSegments(detector: Vad) {
        while (!detector.empty()) {
            val samples = detector.front().samples
            if (samples.size >= MIN_SPEECH_SEGMENT_SAMPLES) {
                speechSegments.offer(samples)
                val queuedMillis = queuedSpeechSamples.addAndGet(samples.size.toLong()) * 1_000L / SAMPLE_RATE
                val now = SystemClock.elapsedRealtime()
                if (
                    queuedMillis >= SPEECH_BACKLOG_WARNING_MILLIS &&
                    now - lastBacklogNoticeElapsedRealtime >= BACKLOG_NOTICE_INTERVAL_MILLIS
                ) {
                    lastBacklogNoticeElapsedRealtime = now
                    updateNotification("المعالجة الصوتية متأخرة؛ تم الاحتفاظ بالجمل في قائمة الانتظار ولن تُحذف")
                }
            }
            detector.pop()
        }
    }

    private fun isPredominantlyNonSpeechAudio(
        tagger: AudioTagging,
        samples: FloatArray
    ): Boolean {
        val stream = tagger.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            val events = tagger.compute(stream, AUDIO_TAGGING_TOP_K)
            SpeechAudioGate.isPredominantlyNonSpeech(
                events.map { event -> event.name to event.prob }
            )
        } finally {
            stream.release()
        }
    }

    private fun decodeSpeechSegments() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_MORE_FAVORABLE)
        while (running.get() && !Thread.currentThread().isInterrupted) {
            try {
                val samples = speechSegments.take()
                queuedSpeechSamples.addAndGet(-samples.size.toLong())

                val tagger = audioTagger
                if (tagger != null && isPredominantlyNonSpeechAudio(tagger, samples)) {
                    // Suppress music, singing and non-verbal sounds before ASR can caption them.
                    continue
                }

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
                when {
                    recognized.isBlank() -> {
                        consecutiveUnrecognizedSegments++
                        if (consecutiveUnrecognizedSegments == 3) {
                            updateStatus("وصل صوت إلى المحرك، لكنه لم يتعرف على كلام واضح. تأكد من وضوح الحوار وخفّض الموسيقى.")
                        }
                    }
                    SpeechCaptionFilter.isNonSpeechLabel(recognized) -> {
                        // VAD rejects most non-speech audio; this second guard filters
                        // Whisper's explicit music and non-verbal sound descriptions.
                        consecutiveUnrecognizedSegments++
                    }
                    else -> {
                        // Whisper has the full natural speech turn, so use its language
                        // result immediately; waiting for multiple votes would discard
                        // the first one or more spoken phrases.
                        val language = normalizeDetectedLanguage(result.lang)
                        if (language.isBlank()) {
                            updateNotification("تعذر تحديد لغة الكلام؛ لم تُحذف الجملة من التسجيل")
                        } else {
                            consecutiveUnrecognizedSegments = 0
                            queueCaptionPhrase(recognized, language)
                        }
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: Exception) {
                val detail = error.localizedMessage ?: "خطأ في محرك التعرّف"
                updateStatus("تعذر تحليل الصوت: " + detail)
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
                updateNotification("محرك الترجمة المحلي يحتاج تنزيل نموذج 609 ميغابايت. اضغط «تنزيل الترجمة» في الإشعار؛ لا يوجد تنزيل تلقائي.")
            }
            return
        }

        if (!engine.supportsSourceLanguage(pending.sourceLanguage)) {
            pendingTranslations.removeFirst()
            if (lastUnsupportedLanguageNotice != pending.sourceLanguage) {
                lastUnsupportedLanguageNotice = pending.sourceLanguage
                updateNotification(
                    "لغة المصدر ${pending.sourceLanguage} غير موجودة ضمن لغات النموذج الـ100؛ لم يُرسل النص إلى أي خدمة خارجية."
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
                        updateNotification("لم يُنتج نموذج الترجمة نصاً لهذه العبارة؛ ستتم متابعة الجملة التالية.")
                    }
                    processNextPendingTranslation()
                }.onFailure { error ->
                    pendingTranslations.addFirst(pending.copy(attempt = pending.attempt + 1))
                    if (error is OutOfMemoryError) {
                        System.gc()
                        updateNotification("ذاكرة التلفاز لا تكفي لمعالجة الجملة حالياً. حُفظت الجملة ولم تتم إعادة المحاولة تلقائياً.")
                    } else {
                        val detail = error.localizedMessage ?: "خطأ محلي في محرك ONNX"
                        updateNotification("تعذرت الترجمة المحلية؛ حُفظت الجملة لإعادة المحاولة: $detail")
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
        if (!running.get() || small100Translator != null) return
        if (!translationModelSetup.compareAndSet(false, true)) return
        translationModelThread = Thread({
            try {
                val files = Small100ModelRepository.installedFiles(applicationContext)
                if (files == null) {
                    translationModelInstalled = false
                    translationModelSetup.set(false)
                    updateNotification("محرك الترجمة المحلي غير مثبت بعد. اضغط «تنزيل الترجمة» لتنزيل النموذج مرة واحدة عبر اتصال غير محدود.")
                    return@Thread
                }
                val engine = Small100Translator.create(files)
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
                    updateNotification("محرك SMaLL-100 المحلي جاهز؛ الترجمة تعمل دون اشتراك أو اتصال بخدمة خارجية.")
                    processNextPendingTranslation()
                }
            } catch (error: OutOfMemoryError) {
                System.gc()
                translationModelSetup.set(false)
                updateNotification("ذاكرة التلفاز لا تكفي لتحميل نموذج الترجمة الكبير حالياً. أوقف التطبيقات الأخرى ثم أعد المحاولة؛ لم يتم إرسال أي نص إلى الخارج.")
            } catch (error: Exception) {
                translationModelSetup.set(false)
                val detail = error.localizedMessage ?: "تعذر تهيئة نموذج الترجمة"
                updateNotification("تعذر تشغيل محرك الترجمة المحلي: $detail")
            }
        }, "RoyalSmall100Setup").apply { isDaemon = true; start() }
    }

    private fun startTranslationModelDownload() {
        if (!running.get() || small100Translator != null) return
        if (!translationModelSetup.compareAndSet(false, true)) {
            updateNotification("تنزيل أو تجهيز نموذج الترجمة جارٍ بالفعل.")
            return
        }
        translationModelThread = Thread({
            try {
                val files = Small100ModelRepository.ensureDownloaded(applicationContext) { progress ->
                    updateNotification("تنزيل نموذج الترجمة المحلي — $progress")
                }
                val engine = Small100Translator.create(files)
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
                    updateNotification("اكتمل تنزيل نموذج SMaLL-100 وأصبح جاهزاً للعمل محلياً دون إنترنت.")
                    processNextPendingTranslation()
                }
            } catch (error: OutOfMemoryError) {
                System.gc()
                translationModelSetup.set(false)
                updateNotification("ذاكرة التلفاز لا تكفي لتشغيل نموذج الترجمة. لم يتم تفعيل أي خدمة خارجية أو مدفوعة.")
            } catch (error: Exception) {
                translationModelSetup.set(false)
                val detail = error.localizedMessage ?: "تحقق من الاتصال والمساحة الحرة"
                updateNotification("تعذر تنزيل نموذج الترجمة: $detail")
            }
        }, "RoyalSmall100Download").apply { isDaemon = true; start() }
    }

    private fun showCaption(text: String, language: String) {
        mainHandler.post {
            val caption = captionView ?: return@post
            val safeArabicCaption = ArabicCaptionFormatter.format(text)
            if (safeArabicCaption.isBlank()) return@post
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
        val notification = buildNotification(if (usePlaybackCapture) "تهيئة التقاط صوت التطبيقات" else "تهيئة ميكروفون التلفاز")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && usePlaybackCapture) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
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
            .apply {
                if (!translationModelInstalled) {
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
        speechSegments.clear()
        queuedSpeechSamples.set(0L)

        try {
            audioRecord?.let { record ->
                if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
                record.release()
            }
        } catch (_: Exception) {
            // The capture device may already have been released by the system.
        }
        audioRecord = null
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
        val taggerToRelease = audioTagger
        audioTagger = null
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
        if (taggerToRelease != null) {
            val decoder = decodeThread
            if (decoder == null || !decoder.isAlive) {
                runCatching { taggerToRelease.release() }
            } else {
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
                        taggerToRelease.release()
                    } catch (_: Exception) {
                        // Native audio-tagging cleanup must not prevent service shutdown.
                    } finally {
                        if (interrupted) Thread.currentThread().interrupt()
                    }
                }, "RoyalAudioTaggerRelease").start()
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
        const val ACTION_DOWNLOAD_TRANSLATION_MODEL = "com.royalplus.launcher.action.DOWNLOAD_TRANSLATION_MODEL"
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
        private const val AUDIO_TAGGING_TOP_K = 5
        private const val CAPTURE_READ_SAMPLES = 1_600
        private const val VAD_WINDOW_SAMPLES = 512
        private const val MIN_SPEECH_SEGMENT_SAMPLES = SAMPLE_RATE / 5
        private const val SPEECH_BACKLOG_WARNING_MILLIS = 30_000L
        private const val BACKLOG_NOTICE_INTERVAL_MILLIS = 10_000L
        private const val TRANSLATION_RETRY_DELAY_MILLIS = 2_500L
    }
}
