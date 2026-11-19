package com.royalplus.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationDeviceCapabilityTest {

    private fun facts(
        apiLevel: Int = 24,
        supportedAbis: List<String> = listOf("arm64-v8a"),
        lowRamDevice: Boolean = false,
        totalMemoryBytes: Long = 2L * 1024L * 1024L * 1024L,
        availableMemoryBytes: Long = 1024L * 1024L * 1024L,
        lowMemory: Boolean = false
    ) = TranslationDeviceCapability.DeviceFacts(
        apiLevel = apiLevel,
        supportedAbis = supportedAbis,
        lowRamDevice = lowRamDevice,
        totalMemoryBytes = totalMemoryBytes,
        availableMemoryBytes = availableMemoryBytes,
        lowMemory = lowMemory
    )

    @Test
    fun rejectsAndroidVersionsBelowSeven() {
        val assessment = TranslationDeviceCapability.evaluateHardware(facts(apiLevel = 23))
        assertFalse(assessment.supported)
        assertEquals(
            TranslationDeviceCapability.Failure.UNSUPPORTED_ANDROID,
            assessment.failure
        )
        assertTrue(assessment.message.contains("Android 7.0"))
    }

    @Test
    fun rejectsCpuArchitecturesWithoutBundledOnnxRuntime() {
        val assessment = TranslationDeviceCapability.evaluateHardware(
            facts(supportedAbis = listOf("mips"))
        )
        assertFalse(assessment.supported)
        assertEquals(TranslationDeviceCapability.Failure.UNSUPPORTED_CPU, assessment.failure)
    }

    @Test
    fun flagsLowRamDevicesBeforeDownloadingTheLargeModel() {
        val assessment = TranslationDeviceCapability.evaluateHardware(
            facts(lowRamDevice = true)
        )
        assertFalse(assessment.supported)
        assertEquals(TranslationDeviceCapability.Failure.INSUFFICIENT_MEMORY, assessment.failure)
        assertTrue(assessment.message.contains("نأسف"))
    }

    @Test
    fun acceptsACompatibleDeviceWithEnoughTotalMemory() {
        val assessment = TranslationDeviceCapability.evaluateHardware(facts())
        assertTrue(assessment.supported)
    }

    @Test
    fun rejectsInsufficientAvailableMemoryBeforeModelLoad() {
        val assessment = TranslationDeviceCapability.evaluateAvailableMemory(
            facts(availableMemoryBytes = 256L * 1024L * 1024L),
            384L * 1024L * 1024L
        )
        assertFalse(assessment.supported)
        assertEquals(TranslationDeviceCapability.Failure.INSUFFICIENT_MEMORY, assessment.failure)
    }

    @Test
    fun rejectsADeviceThatCannotKeepEnoughMemoryAfterModelLoad() {
        val assessment = TranslationDeviceCapability.evaluateAvailableMemory(
            facts(availableMemoryBytes = 96L * 1024L * 1024L),
            128L * 1024L * 1024L
        )
        assertFalse(assessment.supported)
        assertEquals(TranslationDeviceCapability.Failure.INSUFFICIENT_MEMORY, assessment.failure)
    }

    @Test
    fun rejectsSlowLocalInference() {
        val assessment = TranslationDeviceCapability.evaluateBenchmark(listOf(17_000L, 18_000L))
        assertFalse(assessment.supported)
        assertEquals(TranslationDeviceCapability.Failure.SLOW_PERFORMANCE, assessment.failure)
        assertTrue(assessment.message.contains("أداء جهازك"))
    }

    @Test
    fun acceptsFastLocalInference() {
        val assessment = TranslationDeviceCapability.evaluateBenchmark(listOf(3_500L, 4_200L))
        assertTrue(assessment.supported)
    }

    @Test
    fun rejectsMissingPerformanceMeasurements() {
        val assessment = TranslationDeviceCapability.evaluateBenchmark(emptyList())
        assertFalse(assessment.supported)
        assertEquals(TranslationDeviceCapability.Failure.ENGINE_UNAVAILABLE, assessment.failure)
    }
}
