package com.royalplus.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class RoyalCaptionTransportTest {
    @Test
    fun pairingCodeContainsEightDigits() {
        repeat(50) {
            assertTrue(RoyalCaptionTransport.createPairingCode().matches(Regex("\\d{8}")))
        }
    }

    @Test
    fun rejectsPublicAndMalformedAddresses() {
        assertTrue(RoyalCaptionTransport.isPrivateIpv4Address("192.168.1.22"))
        assertTrue(RoyalCaptionTransport.isPrivateIpv4Address("10.0.0.3"))
        assertFalse(RoyalCaptionTransport.isPrivateIpv4Address("8.8.8.8"))
        assertFalse(RoyalCaptionTransport.isPrivateIpv4Address("not-an-ip"))
    }

    @Test
    fun challengeProofMatchesOnlyTheSameKeyAndNonce() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(16) { (it * 3).toByte() }
        val proof = RoyalCaptionTransport.authenticationProof(key, nonce)
        assertTrue(RoyalCaptionTransport.verifyProof(proof, proof.copyOf()))
        assertFalse(RoyalCaptionTransport.verifyProof(proof, RoyalCaptionTransport.authenticationProof(key, ByteArray(16))))
    }

    @Test
    fun encryptedCaptionRoundTripsAndRejectsTampering() {
        val key = RoyalCaptionTransport.deriveSessionKey("12345678", ByteArray(16) { (it + 1).toByte() })
        val original = JSONObject()
            .put("type", "caption")
            .put("sequence", 7)
            .put("text", "مرحبا بالعالم")
            .put("source_language", "en")
        val encrypted = RoyalCaptionTransport.encryptPayload(key, original)
        val decrypted = RoyalCaptionTransport.decryptPayload(key, encrypted)
        assertEquals(original.getString("text"), decrypted.getString("text"))
        assertEquals(original.getLong("sequence"), decrypted.getLong("sequence"))

        val data = encrypted.getString("data")
        val replacement = if (data.first() == '0') '1' else '0'
        val tampered = JSONObject(encrypted.toString())
            .put("data", replacement + data.substring(1))
        var rejected = false
        try {
            RoyalCaptionTransport.decryptPayload(key, tampered)
        } catch (_: Exception) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
