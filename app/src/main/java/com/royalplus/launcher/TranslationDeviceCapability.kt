package com.royalplus.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock

/**
 * Conservative, on-device gate for the optional SMaLL-100 live translation feature.
 * It never checks network state and never sends sample text outside the device.
 */
internal object TranslationDeviceCapability {
    enum class Failure {
        UNSUPPORTED_ANDROID,
        UNSUPPORTED_CPU,
        INSUFFICIENT_MEMORY,
        SLOW_PERFORMANCE,
        ENGINE_UNAVAILABLE
    }

    data class Assessment(
        val supported: Boolean,
        val title: String,
        val message: String,
        val failure: Failure? = null
    )

    internal data class DeviceFacts(
        val apiLevel: Int,
        val supportedAbis: List<String>,
        val lowRamDevice: Boolean,
        val totalMemoryBytes: Long,
        val availableMemoryBytes: Long,
        val lowMemory: Boolean
    )

    private const val MIB = 1024L * 1024L
    private const val MIN_TOTAL_MEMORY_BYTES = 1536L * MIB
    private const val MIN_AVAILABLE_BEFORE_MODEL_BYTES = 384L * MIB
    private const val MIN_AVAILABLE_AFTER_MODEL_BYTES = 128L * MIB
    private const val MAX_AVERAGE_TRANSLATION_MILLIS = 15_000L
    private const val MAX_SINGLE_TRANSLATION_MILLIS = 22_000L
    private val supportedAbis = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    private val ready = Assessment(
        supported = true,
        title = "الجهاز متوافق",
        message = "اجتاز الجهاز فحص التوافق والأداء."
    )

    fun checkHardware(context: Context): Assessment = evaluateHardware(readDeviceFacts(context))

    fun checkBeforeModelLoad(context: Context): Assessment {
        val facts = readDeviceFacts(context)
        val hardware = evaluateHardware(facts)
        if (!hardware.supported) return hardware
        return evaluateAvailableMemory(facts, MIN_AVAILABLE_BEFORE_MODEL_BYTES)
    }

    /**
     * Runs a warm-up followed by two representative English-to-Arabic translations.
     * All calls run on the caller's worker thread; this must never run on the UI thread.
     */
    fun benchmark(context: Context, translator: Small100Translator): Assessment {
        val memoryBefore = evaluateAvailableMemory(readDeviceFacts(context), MIN_AVAILABLE_AFTER_MODEL_BYTES)
        if (!memoryBefore.supported) return memoryBefore

        return try {
            val warmup = translator.translate(
                "Hello. This is a local translation readiness check.",
                targetLanguage = "ar",
                maxOutputTokens = 32
            )
            if (warmup.isBlank()) return engineUnavailable("لم ينتج المحرك ترجمة صالحة في اختبار التهيئة.")

            val samples = listOf(
                "This is a short performance test for local translation on your television.",
                "The subtitles are processed directly on this device without an internet connection."
            )
            val durations = samples.map { sample ->
                val startedAt = SystemClock.elapsedRealtime()
                val translated = translator.translate(sample, targetLanguage = "ar", maxOutputTokens = 32)
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                if (translated.isBlank()) {
                    return engineUnavailable("لم ينتج المحرك ترجمة صالحة أثناء اختبار الأداء.")
                }
                elapsed
            }
            val performance = evaluateBenchmark(durations)
            if (!performance.supported) return performance

            evaluateAvailableMemory(readDeviceFacts(context), MIN_AVAILABLE_AFTER_MODEL_BYTES)
        } catch (_: OutOfMemoryError) {
            insufficientMemory("نفدت الذاكرة أثناء اختبار تشغيل نموذج الترجمة.")
        } catch (error: Exception) {
            engineUnavailable(error.localizedMessage ?: "تعذر تشغيل اختبار الترجمة المحلي.")
        }
    }

    internal fun evaluateHardware(facts: DeviceFacts): Assessment {
        if (facts.apiLevel < 24) {
            return unsupported(
                Failure.UNSUPPORTED_ANDROID,
                "يتطلب محرك الترجمة Android 7.0 أو أحدث."
            )
        }
        if (facts.supportedAbis.none { it in supportedAbis }) {
            return unsupported(
                Failure.UNSUPPORTED_CPU,
                "معمارية معالج هذا الجهاز غير مدعومة في ملفات التشغيل المضمنة."
            )
        }
        if (facts.lowRamDevice ||
            (facts.totalMemoryBytes > 0L && facts.totalMemoryBytes < MIN_TOTAL_MEMORY_BYTES)
        ) {
            return insufficientMemory("ذاكرة الجهاز الكلية أقل من الحد المناسب لتشغيل نموذج الترجمة.")
        }
        return ready
    }

    internal fun evaluateAvailableMemory(facts: DeviceFacts, minimumBytes: Long): Assessment {
        if (facts.lowMemory ||
            (facts.availableMemoryBytes > 0L && facts.availableMemoryBytes < minimumBytes)
        ) {
            return insufficientMemory(
                "الذاكرة المتاحة حالياً لا تكفي بأمان. أغلق التطبيقات الأخرى ثم أعد المحاولة."
            )
        }
        return ready
    }

    internal fun evaluateBenchmark(durationsMillis: List<Long>): Assessment {
        if (durationsMillis.isEmpty()) {
            return engineUnavailable("لم تكتمل قياسات أداء الترجمة المحلية.")
        }
        val average = durationsMillis.average()
        val slowest = durationsMillis.maxOrNull() ?: Long.MAX_VALUE
        if (average > MAX_AVERAGE_TRANSLATION_MILLIS || slowest > MAX_SINGLE_TRANSLATION_MILLIS) {
            return weakPerformance(
                "أظهر الاختبار أن سرعة الترجمة أبطأ من الحد المناسب للترجمة الحية."
            )
        }
        return ready
    }

    internal fun insufficientMemory(detail: String): Assessment = Assessment(
        supported = false,
        title = "أداء الجهاز غير كافٍ",
        message = "نأسف، ذاكرة جهازك لا تكفي لتشغيل الترجمة المحلية بصورة مستقرة. $detail",
        failure = Failure.INSUFFICIENT_MEMORY
    )

    internal fun slowSpeechRecognition(): Assessment = weakPerformance(
        "أظهر الاستخدام الفعلي أن التعرف على الكلام أبطأ من الصوت الوارد بشكل متكرر."
    )

    internal fun engineUnavailable(detail: String): Assessment = Assessment(
        supported = false,
        title = "الترجمة غير متاحة على هذا الجهاز",
        message = "نأسف، جهازك لا يستطيع تشغيل محرك الترجمة المحلية بصورة مستقرة. $detail",
        failure = Failure.ENGINE_UNAVAILABLE
    )

    private fun unsupported(failure: Failure, detail: String): Assessment = Assessment(
        supported = false,
        title = "هذه الميزة غير مدعومة",
        message = "نأسف، جهازك لا يدعم هذه الميزة. $detail",
        failure = failure
    )

    private fun weakPerformance(detail: String): Assessment = Assessment(
        supported = false,
        title = "أداء الجهاز ضعيف",
        message = "نأسف، أداء جهازك لا يسمح بتشغيل الترجمة المحلية بشكل مناسب. $detail",
        failure = Failure.SLOW_PERFORMANCE
    )

    private fun readDeviceFacts(context: Context): DeviceFacts {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager?.getMemoryInfo(memory)
        return DeviceFacts(
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            lowRamDevice = manager?.isLowRamDevice == true,
            totalMemoryBytes = memory.totalMem,
            availableMemoryBytes = memory.availMem,
            lowMemory = memory.lowMemory
        )
    }
}
