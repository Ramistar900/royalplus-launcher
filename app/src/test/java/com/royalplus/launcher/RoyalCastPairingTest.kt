package com.royalplus.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoyalCastPairingTest {
    @Test
    fun validQrPayloadRoundTrips() {
        val payload = RoyalCastPairing.createPayload(
            host = "192.168.1.24",
            port = 38471,
            token = "0123456789abcdef0123456789abcdef",
            pin = "042681"
        )

        assertEquals(
            RoyalCastEndpoint(
                host = "192.168.1.24",
                port = 38471,
                token = "0123456789abcdef0123456789abcdef",
                pin = "042681"
            ),
            RoyalCastPairing.parsePayload(payload)
        )
    }

    @Test
    fun malformedOrPublicPayloadIsRejected() {
        assertNull(RoyalCastPairing.parsePayload("https://example.com"))
        assertNull(
            RoyalCastPairing.parsePayload(
                "royalcast://connect?host=127.0.0.1&port=38471&token=0123456789abcdef0123456789abcdef&pin=042681"
            )
        )
        assertNull(
            RoyalCastPairing.parsePayload(
                "royalcast://connect?host=192.168.1.24&port=99999&token=0123456789abcdef0123456789abcdef&pin=042681"
            )
        )
    }
}
