package com.royalplus.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder

internal class RoyalAdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopPlayback()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!RoyalAdhanScheduler.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val prayerKey = intent?.getStringExtra("prayer_key").orEmpty()
        val city = intent?.getStringExtra("city").orEmpty()
        val language = intent?.getStringExtra("language") ?: "ar"
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(prayerKey, city, language))
        startPlayback()
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        stopPlayback()
        try {
            val descriptor = resources.openRawResourceFd(R.raw.royal_adhan) ?: run {
                stopSelf()
                return
            }
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mediaPlayer.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            descriptor.close()
            mediaPlayer.setOnPreparedListener { prepared ->
                runCatching { prepared.start() }.onFailure { stopSelf() }
            }
            mediaPlayer.setOnCompletionListener {
                stopPlayback()
                stopForegroundCompat()
                stopSelf()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                stopPlayback()
                stopForegroundCompat()
                stopSelf()
                true
            }
            player = mediaPlayer
            mediaPlayer.prepareAsync()
        } catch (_: Exception) {
            stopPlayback()
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun stopPlayback() {
        val active = player
        player = null
        if (active != null) {
            runCatching { if (active.isPlaying) active.stop() }
            runCatching { active.reset() }
            runCatching { active.release() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "ROYAL+ Adhan",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Prayer time playback status"
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }

    private fun buildNotification(prayerKey: String, city: String, language: String): Notification {
        val isEnglish = language == "en"
        val isTurkish = language == "tr"
        val prayerName = when (prayerKey) {
            "Fajr" -> if (isEnglish) "Fajr" else if (isTurkish) "Sabah" else "الفجر"
            "Dhuhr" -> if (isEnglish) "Dhuhr" else if (isTurkish) "Öğle" else "الظهر"
            "Asr" -> if (isEnglish) "Asr" else if (isTurkish) "İkindi" else "العصر"
            "Maghrib" -> if (isEnglish) "Maghrib" else if (isTurkish) "Akşam" else "المغرب"
            "Isha" -> if (isEnglish) "Isha" else if (isTurkish) "Yatsı" else "العشاء"
            else -> if (isEnglish) "Prayer time" else if (isTurkish) "Namaz vakti" else "موعد الصلاة"
        }
        val title = when {
            isEnglish -> "ROYAL+ • $prayerName prayer"
            isTurkish -> "ROYAL+ • $prayerName namazı"
            else -> "الملكي • أذان $prayerName"
        }
        val citySuffix = city.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
        val content = when {
            isEnglish -> "Adhan is playing$citySuffix"
            isTurkish -> "Ezan çalıyor$citySuffix"
            else -> "يجري تشغيل الأذان$citySuffix"
        }
        val stopIntent = Intent(this, RoyalAdhanPlaybackService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isEnglish) "Stop" else if (isTurkish) "Durdur" else "إيقاف الأذان",
                stopPending
            )
        return builder.build()
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP = "com.royalplus.launcher.action.STOP_ADHAN"
        private const val CHANNEL_ID = "royalplus_adhan_playback"
        private const val NOTIFICATION_ID = 84125
        private const val STOP_REQUEST_CODE = 84126
    }
}
