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
    fun wifiDirectQrPreservesPeerAddressAndTransport() {
        val endpoint = RoyalCastEndpoint(
            host = "192.168.49.1",
            port = 38471,
            token = "0123456789abcdef0123456789abcdef",
            pin = "042681",
            transport = RoyalCastTransport.WIFI_DIRECT,
            deviceAddress = "02:11:22:33:44:55",
            wifiDirectSsid = "DIRECT-RY-ROYAL",
            wifiDirectPassphrase = "RoyalCast4821"
        )
        val payload = RoyalCastPairing.createPayload(
            host = endpoint.host,
            port = endpoint.port,
            token = endpoint.token,
            pin = endpoint.pin,
            transport = endpoint.transport,
            deviceAddress = endpoint.deviceAddress,
            wifiDirectSsid = endpoint.wifiDirectSsid,
            wifiDirectPassphrase = endpoint.wifiDirectPassphrase
        )
        assertEquals(endpoint, RoyalCastPairing.parsePayload(payload))
    }

    @Test
    fun wifiDirectPayloadWithoutPeerAddressIsRejected() {
        assertNull(
            RoyalCastPairing.parsePayload(
                "royalcast://connect?host=192.168.49.1&port=38471&token=0123456789abcdef0123456789abcdef&pin=042681&transport=wifi_direct&wifiDirectSsid=DIRECT-RY-ROYAL&wifiDirectPassphrase=RoyalCast4821"
            )
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
