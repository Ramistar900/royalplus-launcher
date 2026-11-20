package com.royalplus.launcher

import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local-only caption transport. The TV challenges the phone, the pairing code is
 * never sent over Wi-Fi, and caption frames are authenticated and AES-GCM encrypted.
 */
internal object RoyalCaptionTransport {
    const val PORT = 45873
    const val MAX_CAPTION_CHARS = 900
    private const val KDF_ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun createPairingCode(): String = (10000000 + random.nextInt(90000000)).toString()

    fun randomChallengeBytes(): ByteArray = ByteArray(16).also(random::nextBytes)

    fun encodeBytes(bytes: ByteArray): String = bytes.joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    fun decodeBytes(value: String): ByteArray {
        require(value.length % 2 == 0 && value.length <= 32 * 1024) { "ترميز بيانات غير صالح" }
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    fun deriveSessionKey(pin: String, salt: ByteArray): ByteArray {
        require(pin.matches(Regex("\\d{8}"))) { "رمز الاقتران غير صالح" }
        val spec = PBEKeySpec(pin.toCharArray(), salt, KDF_ITERATIONS, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun authenticationProof(key: ByteArray, nonce: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(nonce)
    }

    fun verifyProof(expected: ByteArray, supplied: ByteArray): Boolean =
        MessageDigest.isEqual(expected, supplied)

    fun encryptPayload(key: ByteArray, payload: JSONObject): JSONObject {
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("type", "encrypted")
            .put("iv", encodeBytes(iv))
            .put("data", encodeBytes(encrypted))
    }

    fun decryptPayload(key: ByteArray, frame: JSONObject): JSONObject {
        require(frame.optString("type") == "encrypted") { "نوع رسالة غير صالح" }
        val iv = decodeBytes(frame.getString("iv"))
        require(iv.size == 12) { "IV غير صالح" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        val clear = cipher.doFinal(decodeBytes(frame.getString("data")))
        require(clear.size <= 8 * 1024) { "الرسالة كبيرة جداً" }
        return JSONObject(clear.toString(Charsets.UTF_8))
    }

    fun writeFrame(writer: BufferedWriter, frame: JSONObject) {
        val encoded = frame.toString()
        require(encoded.length <= 16 * 1024) { "إطار الشبكة كبير جداً" }
        writer.write(encoded)
        writer.newLine()
        writer.flush()
    }

    fun readFrame(reader: BufferedReader): JSONObject {
        val line = StringBuilder()
        while (true) {
            val next = reader.read()
            if (next < 0) throw IOException("انقطع اتصال الجهاز")
            if (next == '\\n'.code) break
            if (next == '\\r'.code) continue
            if (line.length >= 16 * 1024) throw IOException("إطار الشبكة كبير جداً")
            line.append(next.toChar())
        }
        return JSONObject(line.toString())
    }

    fun localIpv4Address(): String? = try {
        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan", ignoreCase = true)) 0 else 1 }
        interfaces.asSequence()
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }

    fun isPrivateIpv4Address(value: String): Boolean {
        if (!value.matches(Regex("(?:\\d{1,3}\\.){3}\\d{1,3}"))) return false
        return try {
            val address = InetAddress.getByName(value)
            address is Inet4Address && address.isSiteLocalAddress &&
                !address.isLoopbackAddress && !address.isMulticastAddress
        } catch (_: Exception) {
            false
        }
    }
}

/** Keeps translated captions queued during brief Wi-Fi interruptions and reconnects automatically. */
internal class RoyalCaptionSender(
    private val host: String,
    private val pin: String,
    private val onConnectionChanged: (Boolean, String) -> Unit
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val sequence = AtomicLong(0L)
    private val queue = LinkedBlockingDeque<JSONObject>(24)
    @Volatile private var activeSocket: Socket? = null
    private val worker: Thread

    init {
        require(RoyalCaptionTransport.isPrivateIpv4Address(host)) { "أدخل عنوان IPv4 محلياً للتلفاز" }
        require(pin.matches(Regex("\\d{8}"))) { "رمز الاقتران يجب أن يتكون من 8 أرقام" }
        worker = Thread({ runConnectionLoop() }, "RoyalCaptionWifiSender").apply {
            isDaemon = true
            start()
        }
    }

    fun sendCaption(text: String, sourceLanguage: String) {
        val safeText = text.trim().take(RoyalCaptionTransport.MAX_CAPTION_CHARS)
        if (safeText.isBlank() || closed.get()) return
        val payload = JSONObject()
            .put("type", "caption")
            .put("sequence", sequence.incrementAndGet())
            .put("text", safeText)
            .put("source_language", sourceLanguage.take(12))
            .put("sent_at_utc_ms", System.currentTimeMillis())
        if (!queue.offerLast(payload)) {
            queue.pollFirst()
            queue.offerLast(payload)
        }
    }

    private fun sendAndConfirm(
        reader: BufferedReader,
        writer: BufferedWriter,
        key: ByteArray,
        payload: JSONObject
    ) {
        val sequence = payload.getLong("sequence")
        RoyalCaptionTransport.writeFrame(
            writer,
            RoyalCaptionTransport.encryptPayload(key, payload)
        )
        val acknowledgement = RoyalCaptionTransport.decryptPayload(
            key,
            RoyalCaptionTransport.readFrame(reader)
        )
        if (acknowledgement.optString("type") != "ack" ||
            acknowledgement.optLong("sequence", -1L) != sequence
        ) {
            throw IOException("لم يصل تأكيد استلام الترجمة من التلفاز")
        }
    }

    private fun runConnectionLoop() {
        var retryDelay = 1_000L
        while (!closed.get()) {
            var socket: Socket? = null
            try {
                socket = Socket()
                activeSocket = socket
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.soTimeout = 10_000
                socket.connect(java.net.InetSocketAddress(host, RoyalCaptionTransport.PORT), 5_000)
                val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
                val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
                val challenge = RoyalCaptionTransport.readFrame(reader)
                if (challenge.optString("type") != "challenge") throw IOException("رد التلفاز غير صالح")
                val salt = RoyalCaptionTransport.decodeBytes(challenge.getString("salt"))
                val nonce = RoyalCaptionTransport.decodeBytes(challenge.getString("nonce"))
                val key = RoyalCaptionTransport.deriveSessionKey(pin, salt)
                val proof = RoyalCaptionTransport.authenticationProof(key, nonce)
                RoyalCaptionTransport.writeFrame(
                    writer,
                    JSONObject().put("type", "authenticate")
                        .put("proof", RoyalCaptionTransport.encodeBytes(proof))
                )
                val response = RoyalCaptionTransport.readFrame(reader)
                if (response.optBoolean("accepted").not()) throw SecurityException("رمز الاقتران غير صحيح")
                socket.soTimeout = 30_000
                retryDelay = 1_000L
                var lastHeartbeatElapsed = System.nanoTime() / 1_000_000L
                onConnectionChanged(true, "اتصل الهاتف بالتلفاز؛ يتم إرسال الترجمة محلياً")
                while (!closed.get() && !socket.isClosed) {
                    val next = queue.pollFirst(1, TimeUnit.SECONDS)
                    if (next == null) {
                        val nowElapsed = System.nanoTime() / 1_000_000L
                        if (nowElapsed - lastHeartbeatElapsed >= 10_000L) {
                            val heartbeat = JSONObject()
                                .put("type", "ping")
                                .put("sequence", sequence.incrementAndGet())
                                .put("sent_at_utc_ms", System.currentTimeMillis())
                            sendAndConfirm(reader, writer, key, heartbeat)
                            lastHeartbeatElapsed = nowElapsed
                        }
                        continue
                    }
                    // Do not flood the TV with stale captions after a long Wi-Fi outage.
                    val age = System.currentTimeMillis() - next.optLong("sent_at_utc_ms", 0L)
                    if (age > 12_000L) continue
                    sendAndConfirm(reader, writer, key, next)
                    lastHeartbeatElapsed = System.nanoTime() / 1_000_000L
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            } catch (error: SocketTimeoutException) {
                if (!closed.get()) {
                    onConnectionChanged(false, "انتهت مهلة الاقتران؛ ستتم إعادة المحاولة")
                    try {
                        Thread.sleep(retryDelay)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    retryDelay = (retryDelay * 2).coerceAtMost(8_000L)
                }
            } catch (error: Exception) {
                if (!closed.get()) {
                    onConnectionChanged(false, "بانتظار التلفاز على الشبكة المحلية…")
                    try {
                        Thread.sleep(retryDelay)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    retryDelay = (retryDelay * 2).coerceAtMost(8_000L)
                }
            } finally {
                runCatching { socket?.close() }
                if (activeSocket === socket) activeSocket = null
            }
        }
        onConnectionChanged(false, "تم إيقاف إرسال الترجمة")
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { activeSocket?.close() }
        worker.interrupt()
        queue.clear()
    }
}
