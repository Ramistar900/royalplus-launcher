package com.royalplus.launcher

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URI
import java.net.URLEncoder
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.UUID

internal enum class RoyalCastTransport { WIFI, WIFI_DIRECT }

internal data class RoyalCastEndpoint(
    val host: String,
    val port: Int,
    val token: String,
    val pin: String,
    val transport: RoyalCastTransport = RoyalCastTransport.WIFI,
    val deviceAddress: String? = null,
    val wifiDirectSsid: String? = null,
    val wifiDirectPassphrase: String? = null
)

/**
 * Pairing is deliberately LAN-only. The QR carries a random one-session token;
 * the short PIN is shown separately on the TV and is checked by the receiver.
 */
internal object RoyalCastPairing {
    private val secureRandom = SecureRandom()

    fun newToken(): String = UUID.randomUUID().toString().replace("-", "")

    fun newPin(): String = secureRandom.nextInt(1_000_000).toString().padStart(6, '0')

    fun createPayload(
        host: String,
        port: Int,
        token: String,
        pin: String,
        transport: RoyalCastTransport = RoyalCastTransport.WIFI,
        deviceAddress: String? = null,
        wifiDirectSsid: String? = null,
        wifiDirectPassphrase: String? = null
    ): String {
        require(isIpv4(host)) { "A local IPv4 address is required." }
        require(port in 1..65535) { "Invalid receiver port." }
        require(token.matches(Regex("[a-fA-F0-9]{32}"))) { "Invalid pairing token." }
        require(pin.matches(Regex("[0-9]{6}"))) { "Invalid pairing PIN." }
        if (transport == RoyalCastTransport.WIFI_DIRECT) {
            require(!wifiDirectSsid.isNullOrBlank() && wifiDirectSsid.length <= 32) {
                "A Wi-Fi Direct network name is required."
            }
            require(!wifiDirectPassphrase.isNullOrBlank() && wifiDirectPassphrase.length in 8..63) {
                "A valid Wi-Fi Direct passphrase is required."
            }
        }
        val mode = if (transport == RoyalCastTransport.WIFI_DIRECT) "wifi_direct" else "wifi"
        val directAddress = deviceAddress?.takeIf { transport == RoyalCastTransport.WIFI_DIRECT }
        return buildString {
            append("royalcast://connect?host=${encode(host)}&port=$port&token=$token&pin=$pin&transport=$mode")
            if (directAddress != null && isValidDeviceAddress(directAddress)) {
                append("&deviceAddress=${encode(directAddress)}")
            }
            if (transport == RoyalCastTransport.WIFI_DIRECT) {
                append("&wifiDirectSsid=${encode(wifiDirectSsid.orEmpty())}")
                append("&wifiDirectPassphrase=${encode(wifiDirectPassphrase.orEmpty())}")
            }
        }
    }

    fun parsePayload(raw: String): RoyalCastEndpoint? = runCatching {
        val uri = URI(raw.trim())
        require(uri.scheme.equals("royalcast", ignoreCase = true))
        require(uri.host.equals("connect", ignoreCase = true))
        val values = uri.rawQuery.orEmpty().split("&")
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) null else {
                    decode(part.substring(0, separator)) to decode(part.substring(separator + 1))
                }
            }.toMap()
        val host = values["host"].orEmpty()
        val port = values["port"]?.toIntOrNull() ?: error("Missing port.")
        val token = values["token"].orEmpty()
        val pin = values["pin"].orEmpty()
        val transport = when (values["transport"]?.lowercase()) {
            null, "", "wifi", "lan" -> RoyalCastTransport.WIFI
            "wifi_direct", "direct" -> RoyalCastTransport.WIFI_DIRECT
            else -> error("Unsupported ROYAL CAST transport.")
        }
        val deviceAddress = values["deviceAddress"]
        val wifiDirectSsid = values["wifiDirectSsid"]
        val wifiDirectPassphrase = values["wifiDirectPassphrase"]
        require(isIpv4(host))
        require(port in 1..65535)
        require(token.matches(Regex("[a-fA-F0-9]{32}")))
        require(pin.matches(Regex("[0-9]{6}")))
        if (transport == RoyalCastTransport.WIFI_DIRECT) {
            require(!wifiDirectSsid.isNullOrBlank() && wifiDirectSsid.length <= 32)
            require(!wifiDirectPassphrase.isNullOrBlank() && wifiDirectPassphrase.length in 8..63)
        }
        RoyalCastEndpoint(
            host, port, token, pin, transport, deviceAddress, wifiDirectSsid, wifiDirectPassphrase
        )
    }.getOrNull()

    fun localIpv4Address(context: Context): String? {
        val activeAddress = runCatching {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return@runCatching null
            val network = connectivity.activeNetwork ?: return@runCatching null
            val capabilities = connectivity.getNetworkCapabilities(network) ?: return@runCatching null
            val isLocalTransport =
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            if (!isLocalTransport) return@runCatching null
            connectivity.getLinkProperties(network)?.linkAddresses
                ?.asSequence()
                ?.map { it.address }
                ?.filterIsInstance<Inet4Address>()
                ?.firstOrNull { it.isSiteLocalAddress && isIpv4(it.hostAddress.orEmpty()) }
                ?.hostAddress
        }.getOrNull()
        if (!activeAddress.isNullOrBlank()) return activeAddress

        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching null
            val addresses = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp || network.isLoopback || network.isVirtual) continue
                val name = network.name.lowercase()
                if (name.startsWith("tun") || name.startsWith("ppp") ||
                    name.startsWith("lo") || name.startsWith("rmnet") ||
                    name.startsWith("ccmni")
                ) continue
                val values = network.inetAddresses
                while (values.hasMoreElements()) {
                    val address = values.nextElement()
                    if (address !is Inet4Address || address.isLoopbackAddress ||
                        address.isLinkLocalAddress || !address.isSiteLocalAddress
                    ) continue
                    val host = address.hostAddress.orEmpty()
                    if (isIpv4(host)) addresses += host
                }
            }
            addresses.firstOrNull()
        }.getOrNull()
    }
    private fun isValidDeviceAddress(value: String?): Boolean =
        !value.isNullOrBlank() && value.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))

    private fun isIpv4(value: String): Boolean {
        val parts = value.split('.')
        if (parts.size != 4 || !parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }) return false
        val octets = parts.map { it.toInt() }
        // Keep pairing on private LAN addresses; reject loopback and public hosts.
        return octets[0] == 10 ||
            (octets[0] == 172 && octets[1] in 16..31) ||
            (octets[0] == 192 && octets[1] == 168)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
}
