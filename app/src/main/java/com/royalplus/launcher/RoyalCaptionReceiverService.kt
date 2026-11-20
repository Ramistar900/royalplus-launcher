package com.royalplus.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TV-only receiver. It never requests or opens a microphone; it only accepts
 * authenticated, encrypted caption messages from a paired phone on the same LAN.
 */
class RoyalCaptionReceiverService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var activeClient: Socket? = null
    private var serverThread: Thread? = null
    private var captionView: TextView? = null
    private var windowManager: WindowManager? = null
    private val hideCaption = Runnable { captionView?.visibility = View.GONE }
    private val pendingDisplayRunnables = mutableListOf<Runnable>()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(KEY_RECEIVER_ENABLED, false).remove(KEY_RECEIVER_PIN).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        if (TranslationDeviceRoleDetector.detect(this) != TranslationDeviceRole.TELEVISION_RECEIVER) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(KEY_RECEIVER_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val pin = intent?.getStringExtra(EXTRA_PIN)
            ?: preferences.getString(KEY_RECEIVER_PIN, null)
        if (pin.isNullOrBlank() || !pin.matches(Regex("\\d{8}"))) {
            preferences.edit().putBoolean(KEY_RECEIVER_ENABLED, false).apply()
            stopSelf()
            return START_NOT_STICKY
        }
        preferences.edit().putString(KEY_RECEIVER_PIN, pin)
            .putBoolean(KEY_RECEIVER_ENABLED, true).apply()
        createNotificationChannel()
        startReceiverForeground()
        if (running.compareAndSet(false, true)) {
            serverThread = Thread({ acceptConnections(pin) }, "RoyalCaptionLanReceiver").apply {
                isDaemon = true
                start()
            }
        }
        return START_STICKY
    }

    private fun acceptConnections(pin: String) {
        try {
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(InetSocketAddress(RoyalCaptionTransport.PORT), 2)
            serverSocket = server
            updateNotification("جاهز لاستقبال الترجمة من الهاتف عبر Wi-Fi")
            while (running.get()) {
                val socket = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                socket.use { client ->
                    activeClient = client
                    client.tcpNoDelay = true
                    client.keepAlive = true
                    client.soTimeout = 10_000
                    try {
                        receiveClient(client, pin)
                    } finally {
                        if (activeClient === client) activeClient = null
                    }
                }
            }
        } catch (error: Exception) {
            if (running.get()) updateNotification(
                "تعذر فتح منفذ الترجمة المحلي: ${error.localizedMessage ?: "خطأ شبكة"}"
            )
        } finally {
            running.set(false)
        }
    }

    private fun receiveClient(socket: Socket, pin: String) {
        try {
            val reader = socket.getInputStream().bufferedReader(Charsets.UTF_8)
            val writer = socket.getOutputStream().bufferedWriter(Charsets.UTF_8)
            val salt = RoyalCaptionTransport.randomChallengeBytes()
            val nonce = RoyalCaptionTransport.randomChallengeBytes()
            RoyalCaptionTransport.writeFrame(
                writer,
                JSONObject().put("type", "challenge")
                    .put("salt", RoyalCaptionTransport.encodeBytes(salt))
                    .put("nonce", RoyalCaptionTransport.encodeBytes(nonce))
            )
            val authentication = RoyalCaptionTransport.readFrame(reader)
            if (authentication.optString("type") != "authenticate") return
            val key = RoyalCaptionTransport.deriveSessionKey(pin, salt)
            val suppliedProof = runCatching {
                RoyalCaptionTransport.decodeBytes(authentication.getString("proof"))
            }.getOrNull() ?: return
            val expectedProof = RoyalCaptionTransport.authenticationProof(key, nonce)
            val accepted = RoyalCaptionTransport.verifyProof(expectedProof, suppliedProof)
            RoyalCaptionTransport.writeFrame(writer, JSONObject().put("accepted", accepted))
            if (!accepted) {
                Thread.sleep(600L)
                return
            }
            socket.soTimeout = 0
            updateNotification("الهاتف متصل؛ يتم عرض الترجمة العائمة فوق التطبيقات")
            var lastSequence = 0L
            while (running.get() && !socket.isClosed) {
                val frame = RoyalCaptionTransport.readFrame(reader)
                val payload = RoyalCaptionTransport.decryptPayload(key, frame)
                if (payload.optString("type") != "caption") continue
                val sequence = payload.optLong("sequence", -1L)
                val text = payload.optString("text").trim()
                if (sequence <= lastSequence || text.isBlank() ||
                    text.length > RoyalCaptionTransport.MAX_CAPTION_CHARS
                ) continue
                lastSequence = sequence
                showCaption(text)
            }
        } catch (_: IOException) {
            updateNotification("انقطع الهاتف؛ بانتظار إعادة الاتصال تلقائياً")
        } catch (_: Exception) {
            // A failed/invalid pairing closes only that client; the receiver keeps listening.
        }
    }

    private fun showCaption(text: String) {
        mainHandler.post {
            val manager = windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager
            windowManager = manager
            val delay = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
                .getInt(KEY_DISPLAY_DELAY_MS, 0).coerceIn(0, 5_000)
            lateinit var display: Runnable
            display = Runnable {
                pendingDisplayRunnables.remove(display)
                if (!running.get()) return@Runnable
                val caption = captionView ?: createCaptionView().also { view ->
                    val params = WindowManager.LayoutParams(
                    (resources.displayMetrics.widthPixels * 0.90f).toInt().coerceAtLeast(dp(240)),
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    },
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = dp(62)
                }
                    manager.addView(view, params)
                    captionView = view
                }
                caption.text = ArabicCaptionFormatter.format(text)
                caption.visibility = View.VISIBLE
                caption.bringToFront()
                mainHandler.removeCallbacks(hideCaption)
                mainHandler.postDelayed(hideCaption, 4_500L)
            }
            pendingDisplayRunnables.add(display)
            mainHandler.postDelayed(display, delay.toLong())
        }
    }

    private fun createCaptionView(): TextView = TextView(this).apply {
        textSize = 23f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        setTextColor(0xFFFFFFFF.toInt())
        setShadowLayer(dp(2).toFloat(), 0f, dp(1).toFloat(), 0xCC000000.toInt())
        gravity = Gravity.CENTER
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        textDirection = View.TEXT_DIRECTION_RTL
        layoutDirection = View.LAYOUT_DIRECTION_RTL
        includeFontPadding = false
        maxLines = 3
        setPadding(dp(18), dp(8), dp(18), dp(8))
        background = GradientDrawable().apply {
            setColor(0xB8000000.toInt())
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), 0x66D8B66A)
        }
        visibility = View.GONE
        contentDescription = "ترجمة مرسلة من الهاتف"
    }

    private fun startReceiverForeground() {
        val notification = buildNotification("جاهز لاستقبال الترجمة من الهاتف")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ترجمة الهاتف إلى التلفاز",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "حالة اتصال الهاتف واستقبال الترجمة العائمة على التلفاز"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, RoyalCaptionReceiverService::class.java).apply { action = ACTION_STOP }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val pending = PendingIntent.getService(this, 45874, stopIntent, flags)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("ROYAL+ — مستقبل الترجمة")
            .setContentText(message)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", pending).build())
            .build()
    }

    private fun updateNotification(message: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(message))
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        running.set(false)
        runCatching { activeClient?.close() }
        activeClient = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        mainHandler.removeCallbacks(hideCaption)
        pendingDisplayRunnables.forEach(mainHandler::removeCallbacks)
        pendingDisplayRunnables.clear()
        captionView?.let { view -> runCatching { windowManager?.removeView(view) } }
        captionView = null
        windowManager = null
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putBoolean(KEY_RECEIVER_ENABLED, false).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.royalplus.launcher.action.START_CAPTION_RECEIVER"
        const val ACTION_STOP = "com.royalplus.launcher.action.STOP_CAPTION_RECEIVER"
        const val EXTRA_PIN = "royal_caption_receiver_pin"
        const val PREFERENCES = "royalplus_launcher_preferences"
        const val KEY_RECEIVER_ENABLED = "royal_caption_receiver_enabled"
        const val KEY_RECEIVER_PIN = "royal_caption_receiver_pin_saved"
        const val KEY_DISPLAY_DELAY_MS = "royal_caption_display_delay_ms"
        private const val CHANNEL_ID = "royal_caption_receiver"
        private const val NOTIFICATION_ID = 45873
    }
}
