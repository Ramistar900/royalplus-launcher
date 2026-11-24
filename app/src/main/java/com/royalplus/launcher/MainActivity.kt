package com.royalplus.launcher

import com.royal.adaptive.RoyalAdaptiveHost
import com.royalplus.launcher.royalsounds.RoyalUiSound
import com.royalplus.launcher.royalsounds.RoyalUiSounds

import android.Manifest
import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.text.InputType
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val captionNsdServiceType = RoyalCaptionReceiverService.CAPTION_NSD_SERVICE_TYPE
    private var translationOverlayEnabled by mutableStateOf(false)
    private var isTelevisionDevice = false
    private var pendingPhoneTvHost: String? = null
    private var pendingPhoneTvPin: String? = null
    private var captionDiscoveryManager: NsdManager? = null
    private var captionDiscoveryListener: NsdManager.DiscoveryListener? = null
    private var discoveredCaptionHost: String? = null
    private var captionDiscoveryMulticastLock: WifiManager.MulticastLock? = null
    private var weatherState by mutableStateOf(WeatherUiState())
    private var lastWeatherRefreshAt = 0L
    private var installedApps by mutableStateOf<List<LauncherApp>>(emptyList())
    private val installedAppsExecutor = Executors.newSingleThreadExecutor()
    private var installedAppsLoadGeneration = 0
    private var isDefaultHome by mutableStateOf(false)
    private var voiceSearchAvailable by mutableStateOf(false)
    private var voiceSearchResult by mutableStateOf("")
    private var showingCrashReport = false
    private var translationCapabilityReceiverRegistered = false
    private var captionReceiverRegistered = false

    private val captionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val caption = intent?.getStringExtra(RoyalCaptionReceiverService.EXTRA_CAPTION_TEXT)
                ?.takeIf { it.isNotBlank() } ?: return
            if (isTelevisionDevice && !isFinishing && !isDestroyed) {
                Toast.makeText(this@MainActivity, caption, Toast.LENGTH_LONG).show()
            }
        }
    }

    private val translationCapabilityReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            translationOverlayEnabled = false
            val title = intent?.getStringExtra(FloatingTranslationOverlayService.EXTRA_CAPABILITY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: "الترجمة غير متاحة على هذا الجهاز"
            val message = intent?.getStringExtra(FloatingTranslationOverlayService.EXTRA_CAPABILITY_MESSAGE)
                ?.takeIf { it.isNotBlank() }
                ?: "نأسف، جهازك لا يستطيع تشغيل الترجمة المحلية بصورة مستقرة."
            showTranslationCapabilityDialog(title, message)
        }
    }

    private val voiceSearchLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                voiceSearchResult = matches?.firstOrNull().orEmpty()
                if (voiceSearchResult.isBlank()) {
                    Toast.makeText(this, "لم يتم التعرف على الكلام؛ يمكنك استخدام البحث الكتابي", Toast.LENGTH_LONG).show()
                }
            }
        }

    private val apkPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) installSelectedApk(uri)
        }

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                refreshWeather()
            } else {
                weatherState = WeatherUiState(
                    city = "الموقع غير متاح",
                    description = "اسمح بالوصول إلى الموقع لعرض الطقس",
                    isLoading = false,
                    needsPermission = true
                )
            }
        }

    private val translationMicrophonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startPhoneCaptionCapture()
            else {
                pendingPhoneTvHost = null
                pendingPhoneTvPin = null
                Toast.makeText(this, "يلزم إذن الميكروفون على الهاتف لالتقاط صوت التلفاز.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTelevisionDevice = TranslationDeviceRoleDetector.detect(this) ==
            TranslationDeviceRole.TELEVISION_RECEIVER
        val launcherPreferences = getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
        if (!launcherPreferences.contains("ui_click_sounds_enabled") && isTelevisionDevice) {
            launcherPreferences.edit().putBoolean("ui_click_sounds_enabled", true).apply()
        }
        RoyalColors.applyLightTheme(
            getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
                .getString("launcher_theme_mode", "dark") == "light"
        )
        RoyalUiSounds.initialize(applicationContext, setOf(
            RoyalUiSound.NAV_BACK,
            RoyalUiSound.ACTION_DOWNLOAD,
            RoyalUiSound.MEDIA_PAUSE,
            RoyalUiSound.MEDIA_PLAY,
            RoyalUiSound.ACTION_SUCCESS,
            RoyalUiSound.ACTION_ERROR,
            RoyalUiSound.NAV_SELECT,
            RoyalUiSound.NAV_FOCUS_MOVE,
            RoyalUiSound.NAV_TV_FOCUS,
            RoyalUiSound.SELECT_TOGGLE_OFF,
            RoyalUiSound.SELECT_TOGGLE_ON,
            RoyalUiSound.NAV_OPEN_MENU,
            RoyalUiSound.NAV_CLOSE_MENU,
            RoyalUiSound.ACTION_ADD,
            RoyalUiSound.MEDIA_PREV,
            RoyalUiSound.MEDIA_NEXT
        ), televisionMode = isTelevisionDevice)
        val savedCrashReport = RoyalPlusApplication.readPendingReport(this)
        if (savedCrashReport != null) {
            showingCrashReport = true
            showCrashReport(savedCrashReport)
            return
        }

        RoyalPlusApplication.markStage(this, "MainActivity.onCreate: checking speech recognition")
        voiceSearchAvailable = runCatching {
            SpeechRecognizer.isRecognitionAvailable(this)
        }.getOrDefault(false)

        RoyalPlusApplication.markStage(this, "MainActivity.onCreate: scheduling adhan")
        RoyalAdhanScheduler.scheduleNext(this)
        window.statusBarColor = android.graphics.Color.rgb(8, 10, 18)
        window.navigationBarColor = android.graphics.Color.rgb(8, 10, 18)
        window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)

        RoyalPlusApplication.markStage(this, "MainActivity.onCreate: attaching Compose home screen")
        setContent {
            RoyalAdaptiveHost {
                RoyalAppTheme {
                    RoyalHomeScreen(
                    apps = installedApps,
                    weather = weatherState,
                    onRequestLocation = { requestLocationPermission() },
                    onRefreshWeather = { refreshWeather() },
                    onRefreshApps = { loadInstalledApps(forceRefresh = true) },
                    onChooseWeatherCity = { city -> saveAndLoadManualWeatherCity(city) },
                    onChooseWeatherCityByName = { city -> saveAndLoadManualWeatherCityByName(city) },
                    onUseDeviceLocation = { clearManualWeatherCityAndUseDeviceLocation() },
                    onLaunchApp = { app ->
                        try {
                            startActivity(app.launchIntent)
                        } catch (_: Exception) {
                            // The app may have been removed or blocked after the launcher list was loaded.
                            RoyalUiSounds.play(this, RoyalUiSound.NAV_ERROR)
                            Toast.makeText(
                                this,
                                "تعذر فتح ${app.label}. حدّث قائمة التطبيقات أو تحقق من تثبيته.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    onOpenSettings = {
                        try {
                            startActivity(Intent(Settings.ACTION_SETTINGS))
                        } catch (_: Exception) {
                            // The device may not expose a system settings activity.
                            RoyalUiSounds.play(this, RoyalUiSound.NAV_ERROR)
                        }
                    },
                    isDefaultHome = isDefaultHome,
                    onChooseDefaultHome = { openDefaultHomeSettings() },
                    onInstallApk = { apkPicker.launch(arrayOf("application/vnd.android.package-archive")) },
                    onManageApp = { app -> openAppDetails(app) },
                    onUninstallApp = { app -> requestAppUninstall(app) },
                    onVoiceSearch = { startVoiceSearch() },
                    voiceSearchAvailable = voiceSearchAvailable,
                    voiceSearchResult = voiceSearchResult,
                    translationOverlayEnabled = translationOverlayEnabled,
                    translationIsTelevision = isTelevisionDevice,
                    onToggleTranslationOverlay = { toggleTranslationOverlay() }
                    )
                }
            }
        }

        RoyalPlusApplication.markStage(this, "MainActivity.onCreate: Compose attached; loading weather")
        // Start weather without a mandatory GPS permission; the repository falls back to IP location.
        refreshWeather()
    }

    private fun showCrashReport(report: String) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(Color.rgb(8, 10, 18))
        }
        val title = TextView(this).apply {
            text = "ROYAL+ — تقرير تشخيص الانهيار"
            textSize = 22f
            setTextColor(Color.rgb(232, 190, 105))
        }
        val hint = TextView(this).apply {
            text = "تم إيقاف تشغيل الواجهة مؤقتاً لحماية التقرير. صوّر هذه الشاشة وأرسلها، خصوصاً اسم الخطأ والأسطر الأولى."
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        }
        val details = TextView(this).apply {
            text = report
            textSize = 12f
            setTextColor(Color.rgb(225, 225, 225))
            setTextIsSelectable(true)
            setPadding(12, 12, 12, 12)
        }
        val scroll = ScrollView(this).apply { addView(details) }
        val copyButton = Button(this).apply {
            text = "نسخ تقرير الخطأ"
            setOnClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("ROYAL+ crash report", report))
                    Toast.makeText(this@MainActivity, "تم نسخ التقرير؛ يمكنك لصقه في رسالة", Toast.LENGTH_LONG).show()
                }
            }
        }
        val clearButton = Button(this).apply {
            text = "مسح التقرير وإعادة المحاولة"
            setOnClickListener {
                RoyalPlusApplication.clearPendingReport(this@MainActivity)
                recreate()
            }
        }
        root.addView(title)
        root.addView(hint)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        root.addView(copyButton)
        root.addView(clearButton)
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(FloatingTranslationOverlayService.ACTION_DEVICE_CAPABILITY_FAILED)
        ContextCompat.registerReceiver(
            this,
            translationCapabilityReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        translationCapabilityReceiverRegistered = true
        if (isTelevisionDevice) {
            ContextCompat.registerReceiver(
                this,
                captionReceiver,
                IntentFilter(RoyalCaptionReceiverService.ACTION_CAPTION_RECEIVED),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            captionReceiverRegistered = true
        }
    }

    override fun onStop() {
        if (translationCapabilityReceiverRegistered) {
            runCatching { unregisterReceiver(translationCapabilityReceiver) }
            translationCapabilityReceiverRegistered = false
        }
        if (captionReceiverRegistered) {
            runCatching { unregisterReceiver(captionReceiver) }
            captionReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        installedAppsLoadGeneration++
        installedAppsExecutor.shutdownNow()
        RoyalUiSounds.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (showingCrashReport) return
        if (System.currentTimeMillis() - lastWeatherRefreshAt >= 3 * 60 * 60 * 1000L) {
            refreshWeather()
        }
        RoyalPlusApplication.markStage(this, "MainActivity.onResume: restoring phone/TV caption role")
        val translationPreferences = getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE)
        if (isTelevisionDevice) {
            val receiverEnabled = translationPreferences.getBoolean(
                RoyalCaptionReceiverService.KEY_RECEIVER_ENABLED, false
            )
            translationOverlayEnabled = receiverEnabled
            if (receiverEnabled &&
                !isServiceRunning(RoyalCaptionReceiverService::class.java)
            ) {
                val pin = translationPreferences.getString(RoyalCaptionReceiverService.KEY_RECEIVER_PIN, null)
                if (!pin.isNullOrBlank()) {
                    val serviceIntent = Intent(this, RoyalCaptionReceiverService::class.java).apply {
                        action = RoyalCaptionReceiverService.ACTION_START
                        putExtra(RoyalCaptionReceiverService.EXTRA_PIN, pin)
                    }
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                        else startService(serviceIntent)
                    }
                }
            }
        } else {
            val captureEnabled = translationPreferences.getBoolean(
                FloatingTranslationOverlayService.KEY_PHONE_CAPTURE_ENABLED, false
            )
            translationOverlayEnabled = captureEnabled
            if (captureEnabled &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                !isServiceRunning(FloatingTranslationOverlayService::class.java)
            ) {
                val host = translationPreferences.getString(FloatingTranslationOverlayService.KEY_PHONE_TV_HOST, null)
                val pin = translationPreferences.getString(FloatingTranslationOverlayService.KEY_PHONE_TV_PIN, null)
                if (!host.isNullOrBlank() && !pin.isNullOrBlank()) {
                    val serviceIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
                        action = FloatingTranslationOverlayService.ACTION_START_PHONE_CAPTURE
                        putExtra(FloatingTranslationOverlayService.EXTRA_TV_HOST, host)
                        putExtra(FloatingTranslationOverlayService.EXTRA_TV_PIN, pin)
                    }
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                        else startService(serviceIntent)
                    }.onFailure { translationOverlayEnabled = false }
                } else {
                    translationPreferences.edit().putBoolean(
                        FloatingTranslationOverlayService.KEY_PHONE_CAPTURE_ENABLED, false
                    ).apply()
                    translationOverlayEnabled = false
                }
            }
        }
        RoyalPlusApplication.markStage(this, "MainActivity.onResume: checking default launcher")
        isDefaultHome = isRoyalPlusDefaultHome()
        RoyalPlusApplication.markStage(this, "MainActivity.onResume: scheduling background app scan")
        loadInstalledApps(forceRefresh = true)
        RoyalAdhanScheduler.scheduleNext(this)
    }

    private fun loadInstalledApps(forceRefresh: Boolean) {
        val requestGeneration = ++installedAppsLoadGeneration
        try {
            installedAppsExecutor.execute {
                val apps = runCatching {
                    LauncherAppsRepository.load(
                        packageManager = packageManager,
                        ownPackageName = packageName,
                        forceRefresh = forceRefresh
                    )
                }.getOrNull() ?: return@execute

                runOnUiThread {
                    if (
                        requestGeneration == installedAppsLoadGeneration &&
                        !isFinishing &&
                        !isDestroyed
                    ) {
                        installedApps = apps
                        RoyalPlusApplication.markStage(
                            this,
                            "MainActivity.onResume: installed applications loaded"
                        )
                        RoyalPlusApplication.markStage(
                            this,
                            "MainActivity.onResume: startup checks completed"
                        )
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            // The Activity is already shutting down; no UI update is needed.
        }
    }

    private fun toggleTranslationOverlay() {
        if (isTelevisionDevice) {
            toggleTvCaptionReceiver()
        } else {
            togglePhoneCaptionCapture()
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean =
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClass.name }

    private fun showTranslationCapabilityDialog(title: String, message: String) {
        if (isFinishing || isDestroyed) return
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("حسناً", null)
            .show()
    }

    private fun toggleTvCaptionReceiver() {
        val preferences = getSharedPreferences(RoyalCaptionReceiverService.PREFERENCES, MODE_PRIVATE)
        if (translationOverlayEnabled) {
            stopService(Intent(this, RoyalCaptionReceiverService::class.java))
            preferences.edit().putBoolean(RoyalCaptionReceiverService.KEY_RECEIVER_ENABLED, false)
                .remove(RoyalCaptionReceiverService.KEY_RECEIVER_PIN).apply()
            translationOverlayEnabled = false
            Toast.makeText(this, "تم إيقاف استقبال الترجمة على التلفاز", Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("ROYAL+ — الترجمة")
            .setMessage("هل تسمح لـ ROYAL+ باستقبال الترجمة وعرضها؟")
            .setPositiveButton("السماح") { _, _ -> startTvCaptionReceiver(preferences) }
            .setNegativeButton("رفض", null)
            .show()
    }

    private fun startTvCaptionReceiver(preferences: android.content.SharedPreferences) {
        val localAddress = RoyalCaptionTransport.localIpv4Address()
        if (localAddress.isNullOrBlank()) {
            Toast.makeText(this, "اتصل التلفاز بشبكة Wi-Fi نفسها التي سيتصل بها الهاتف، ثم أعد المحاولة.", Toast.LENGTH_LONG).show()
            return
        }
        val pin = RoyalCaptionTransport.createPairingCode()
        val serviceIntent = Intent(this, RoyalCaptionReceiverService::class.java).apply {
            action = RoyalCaptionReceiverService.ACTION_START
            putExtra(RoyalCaptionReceiverService.EXTRA_PIN, pin)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            preferences.edit()
                .putBoolean(RoyalCaptionReceiverService.KEY_RECEIVER_ENABLED, true)
                .putString(RoyalCaptionReceiverService.KEY_RECEIVER_PIN, pin)
                .apply()
            translationOverlayEnabled = true
            showTvPairingDetails(pin)
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "سيظهر النص داخل ROYAL+ لأن التلفاز لا يتيح إذن النافذة العائمة.", Toast.LENGTH_LONG).show()
            }
        } catch (error: Exception) {
            translationOverlayEnabled = false
            Toast.makeText(this, error.localizedMessage ?: "تعذر تشغيل مستقبل الترجمة على التلفاز.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showTvPairingDetails(pin: String) {
        android.app.AlertDialog.Builder(this)
            .setTitle("ROYAL+ — استقبال الترجمة من الهاتف")
            .setMessage(
                "اتصل الهاتف والتلفاز بشبكة Wi-Fi نفسها. سيعثر الهاتف على هذا التلفاز تلقائياً.\n\n" +
                    "رمز الاقتران: $pin\n\n" +
                    "أدخل الرمز في الهاتف. التلفاز يستقبل النص ويعرضه فقط؛ لا يستخدم الميكروفون."
            )
            .setPositiveButton("تم", null)
            .setNeutralButton("ضبط تأخير العرض") { _, _ -> showCaptionDelayDialog() }
            .setNegativeButton("إيقاف الاستقبال") { _, _ ->
                stopService(Intent(this, RoyalCaptionReceiverService::class.java))
                getSharedPreferences(RoyalCaptionReceiverService.PREFERENCES, MODE_PRIVATE).edit()
                    .putBoolean(RoyalCaptionReceiverService.KEY_RECEIVER_ENABLED, false)
                    .remove(RoyalCaptionReceiverService.KEY_RECEIVER_PIN).apply()
                translationOverlayEnabled = false
            }
            .show()
    }

    private fun showCaptionDelayDialog() {
        val preferences = getSharedPreferences(RoyalCaptionReceiverService.PREFERENCES, MODE_PRIVATE)
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            hint = "من 0 إلى 5000 مللي ثانية"
            setText(preferences.getInt(RoyalCaptionReceiverService.KEY_DISPLAY_DELAY_MS, 0).toString())
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("ضبط تأخير ظهور الترجمة")
            .setMessage("أضف مهلة عرض يدوية عند الحاجة. هذا الضبط يؤخر ظهور النص ولا يستطيع تقديمه قبل وصوله من الهاتف.")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val delay = input.text.toString().toIntOrNull()
                if (delay == null || delay !in 0..5_000) {
                    Toast.makeText(this, "أدخل قيمة بين 0 و5000 مللي ثانية.", Toast.LENGTH_LONG).show()
                } else {
                    preferences.edit().putInt(RoyalCaptionReceiverService.KEY_DISPLAY_DELAY_MS, delay).apply()
                    Toast.makeText(this, "تم حفظ تأخير عرض الترجمة: $delay ms", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun togglePhoneCaptionCapture() {
        if (translationOverlayEnabled) {
            stopService(Intent(this, FloatingTranslationOverlayService::class.java))
            getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(FloatingTranslationOverlayService.KEY_PHONE_CAPTURE_ENABLED, false).apply()
            translationOverlayEnabled = false
            pendingPhoneTvHost = null
            pendingPhoneTvPin = null
            Toast.makeText(this, "توقف الهاتف عن التقاط صوت التلفاز وإرسال الترجمة", Toast.LENGTH_SHORT).show()
            return
        }
        showPhonePairingDialog()
    }

    private fun showPhonePairingDialog() {
        discoveredCaptionHost = null
        val statusView = TextView(this).apply {
            text = "جارٍ البحث تلقائياً عن تلفاز ROYAL+ على شبكة Wi-Fi..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(24, 18, 24, 12)
        }
        val pinInput = EditText(this).apply {
            hint = "رمز الاقتران الظاهر على التلفاز (8 أرقام)"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 4, 20, 4)
            addView(statusView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(pinInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("ربط الهاتف بالتلفاز")
            .setMessage(
                "ضع الهاتف والتلفاز على شبكة Wi-Fi نفسها. سيعثر الهاتف على التلفاز تلقائياً؛ لا حاجة لإدخال أي عنوان يدوياً. أدخل رمز الاقتران الذي يعرضه التلفاز."
            )
            .setView(form)
            .setPositiveButton("بدء الالتقاط", null)
            .setNeutralButton("إعدادات البطارية", null)
            .setNegativeButton("إلغاء", null)
            .create()
        dialog.setOnShowListener {
            startCaptionTvDiscovery(statusView)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: Exception) {
                    Toast.makeText(this, "افتح إعدادات البطارية ثم اجعل ROYAL+ دون قيود، إذا كان هذا الخيار متاحاً.", Toast.LENGTH_LONG).show()
                }
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val host = discoveredCaptionHost
                val pin = pinInput.text.toString().trim()
                if (host.isNullOrBlank()) {
                    statusView.text = "لم يتم العثور على التلفاز بعد. تأكد من تشغيل استقبال الترجمة على التلفاز واتصال الجهازين بالشبكة نفسها."
                    return@setOnClickListener
                }
                if (!pin.matches(Regex("\\d{8}"))) {
                    pinInput.error = "أدخل رمز الاقتران المؤلف من 8 أرقام"
                    return@setOnClickListener
                }
                pendingPhoneTvHost = host
                pendingPhoneTvPin = pin
                stopCaptionTvDiscovery()
                dialog.dismiss()
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    translationMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    startPhoneCaptionCapture()
                }
            }
        }
        dialog.setOnDismissListener { stopCaptionTvDiscovery() }
        dialog.show()
    }

    private fun startCaptionTvDiscovery(statusView: TextView) {
        stopCaptionTvDiscovery()
        val manager = getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (manager == null) {
            statusView.text = "ميزة اكتشاف الأجهزة غير متاحة على هذا الهاتف."
            return
        }
        captionDiscoveryManager = manager
        runCatching {
            val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager
            captionDiscoveryMulticastLock = wifi.createMulticastLock("RoyalCaptionNsdDiscovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                statusView.text = "جارٍ البحث عن تلفاز ROYAL+ على الشبكة المحلية..."
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.contains(captionNsdServiceType.trimEnd('.'))) return
                runCatching {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            statusView.post { statusView.text = "تم العثور على تلفاز، جارٍ إكمال الاتصال..." }
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            val address = info.host?.hostAddress
                            if (!address.isNullOrBlank() &&
                                RoyalCaptionTransport.isPrivateIpv4Address(address) &&
                                info.port == RoyalCaptionTransport.PORT
                            ) {
                                discoveredCaptionHost = address
                                statusView.post {
                                    statusView.text = "تم العثور على التلفاز: ${info.serviceName}. أدخل رمز الاقتران الظاهر على شاشة التلفاز."
                                }
                            }
                        }
                    })
                }.onFailure {
                    statusView.post { statusView.text = "جارٍ البحث عن التلفاز..." }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discoveredCaptionHost = null
                statusView.post { statusView.text = "انقطع ظهور التلفاز على الشبكة؛ جارٍ البحث مجدداً..." }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                statusView.post { statusView.text = "تعذر بدء اكتشاف التلفاز. تحقق من Wi-Fi ثم أعد المحاولة." }
                runCatching { manager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        captionDiscoveryListener = listener
        runCatching {
            manager.discoverServices(captionNsdServiceType, NsdManager.PROTOCOL_DNS_SD, listener)
        }.onFailure {
            statusView.text = "تعذر بدء اكتشاف التلفاز على الشبكة المحلية."
        }
    }

    private fun stopCaptionTvDiscovery() {
        val manager = captionDiscoveryManager
        val listener = captionDiscoveryListener
        if (manager != null && listener != null) {
            runCatching { manager.stopServiceDiscovery(listener) }
        }
        captionDiscoveryListener = null
        captionDiscoveryManager = null
        runCatching {
            captionDiscoveryMulticastLock?.takeIf { it.isHeld }?.release()
        }
        captionDiscoveryMulticastLock = null
    }

    private fun startPhoneCaptionCapture() {
        val host = pendingPhoneTvHost
        val pin = pendingPhoneTvPin
        if (host.isNullOrBlank() || pin.isNullOrBlank()) {
            Toast.makeText(this, "أدخل عنوان التلفاز ورمز الاقتران أولاً.", Toast.LENGTH_LONG).show()
            return
        }
        if (TranslationDeviceRoleDetector.detect(this) != TranslationDeviceRole.PHONE_CAPTURE) {
            Toast.makeText(this, "التقاط الصوت مخصص للهاتف؛ التلفاز يعمل كمستقبل فقط.", Toast.LENGTH_LONG).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            translationMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        val serviceIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
            action = FloatingTranslationOverlayService.ACTION_START_PHONE_CAPTURE
            putExtra(FloatingTranslationOverlayService.EXTRA_TV_HOST, host)
            putExtra(FloatingTranslationOverlayService.EXTRA_TV_PIN, pin)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE).edit()
                .putBoolean(FloatingTranslationOverlayService.KEY_PHONE_CAPTURE_ENABLED, true)
                .putString(FloatingTranslationOverlayService.KEY_PHONE_TV_HOST, host)
                .putString(FloatingTranslationOverlayService.KEY_PHONE_TV_PIN, pin)
                .apply()
            translationOverlayEnabled = true
            pendingPhoneTvHost = null
            pendingPhoneTvPin = null
            Toast.makeText(
                this,
                "بدأ الهاتف الالتقاط. يمكنك إطفاء الشاشة؛ سيواصل الإرسال ما دامت الخدمة والشبكة تعملان.",
                Toast.LENGTH_LONG
            ).show()
        } catch (error: Exception) {
            translationOverlayEnabled = false
            Toast.makeText(this, error.localizedMessage ?: "تعذر تشغيل خدمة التقاط الهاتف.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVoiceSearch() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceSearchAvailable = false
            Toast.makeText(this, "البحث الصوتي غير مدعوم على هذا الجهاز؛ استخدم البحث الكتابي", Toast.LENGTH_LONG).show()
            return
        }
        voiceSearchResult = ""
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "قل اسم التطبيق الذي تريد البحث عنه")
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (_: Exception) {
            voiceSearchAvailable = false
            Toast.makeText(this, "تعذر تشغيل خدمة الصوت؛ يمكنك استخدام البحث الكتابي", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestAppUninstall(app: LauncherApp) {
        if (app.packageName == packageName) {
            Toast.makeText(this, "لا يمكن إزالة ROYAL+ Launcher من داخل نفسه؛ استخدم إعدادات أندرويد", Toast.LENGTH_LONG).show()
            return
        }
        try {
            // Android owns the confirmation and decides whether this package can be removed.
            startActivity(
                Intent(
                    Intent.ACTION_DELETE,
                    Uri.parse("package:${app.packageName}")
                )
            )
        } catch (_: Exception) {
            openAppDetails(app)
        }
    }

    private fun openAppDetails(app: LauncherApp) {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${app.packageName}")
                )
            )
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح إعدادات هذا التطبيق على الجهاز", Toast.LENGTH_LONG).show()
        }
    }

    private fun installSelectedApk(uri: Uri) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                Toast.makeText(this, "فعّل السماح بالتثبيت لهذا التطبيق، ثم اختر ملف APK مرة أخرى", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "هذا الجهاز لا يتيح إعداد السماح بالتثبيت من هنا", Toast.LENGTH_LONG).show()
            }
            return
        }
        try {
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
        } catch (_: Exception) {
            Toast.makeText(this, "تعذر فتح مُثبّت أندرويد لهذا الملف", Toast.LENGTH_LONG).show()
        }
    }

    private fun openDefaultHomeSettings() {
        if (HomeRoleCompat.openDefaultHomeSettings(this)) return

        val actions = listOf(
            Settings.ACTION_HOME_SETTINGS,
            Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS,
            Settings.ACTION_SETTINGS
        )
        for (action in actions) {
            try {
                startActivity(Intent(action))
                return
            } catch (_: android.content.ActivityNotFoundException) {
                // Try the next supported system settings screen.
            } catch (_: SecurityException) {
                // Try the next supported system settings screen.
            }
        }
        Toast.makeText(this, "لم يعثر أندرويد على شاشة اختيار الواجهة الرئيسية", Toast.LENGTH_LONG).show()
    }

    private fun isRoyalPlusDefaultHome(): Boolean {
        HomeRoleCompat.isRoyalPlusDefaultHome(this)?.let { return it }
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == packageName
    }

    private fun hasLocationPermission(): Boolean =
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasSavedManualWeatherCity(): Boolean =
        !getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
            .getString("weather_manual_city", null).isNullOrBlank()

    private fun saveAndLoadManualWeatherCity(city: WeatherCitySuggestion) {
        val normalized = city.displayName.trim()
        if (normalized.isBlank() || city.latitude !in -90.0..90.0 ||
            city.longitude !in -180.0..180.0
        ) return
        getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
            .edit()
            .putString("weather_manual_city", normalized)
            .putString("weather_manual_latitude", city.latitude.toString())
            .putString("weather_manual_longitude", city.longitude.toString())
            .apply()
        weatherState = WeatherUiState(
            city = normalized,
            description = "جارٍ تحميل الطقس للمدينة المختارة",
            isLoading = true,
            isManualLocation = true
        )
        RoyalWeatherRepository.fetchForLocation(this, normalized, city.latitude, city.longitude) { result ->
            runOnUiThread { weatherState = result }
        }
    }

    private fun saveAndLoadManualWeatherCityByName(city: String) {
        val normalized = city.trim()
        if (normalized.isBlank()) return
        getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
            .edit()
            .putString("weather_manual_city", normalized)
            .remove("weather_manual_latitude")
            .remove("weather_manual_longitude")
            .apply()
        weatherState = WeatherUiState(
            city = normalized,
            description = "جارٍ تحميل الطقس للمدينة المختارة",
            isLoading = true,
            isManualLocation = true
        )
        RoyalWeatherRepository.fetchForCity(this, normalized) { result ->
            runOnUiThread { weatherState = result }
        }
    }

    private fun clearManualWeatherCityAndUseDeviceLocation() {
        getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
            .edit()
            .remove("weather_manual_city")
            .remove("weather_manual_latitude")
            .remove("weather_manual_longitude")
            .apply()
        refreshWeather()
    }

    private fun requestLocationPermission() {
        if (hasLocationPermission()) {
            refreshWeather()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun refreshWeather() {
        lastWeatherRefreshAt = System.currentTimeMillis()
        val weatherPreferences = getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
        val manualCity = weatherPreferences.getString("weather_manual_city", null)
        if (!manualCity.isNullOrBlank()) {
            val manualLatitude = weatherPreferences.getString("weather_manual_latitude", null)?.toDoubleOrNull()
            val manualLongitude = weatherPreferences.getString("weather_manual_longitude", null)?.toDoubleOrNull()
            weatherState = WeatherUiState(
                city = manualCity,
                description = "جارٍ تحميل الطقس للمدينة المختارة",
                isLoading = true,
                isManualLocation = true
            )
            if (manualLatitude != null && manualLongitude != null &&
                manualLatitude in -90.0..90.0 && manualLongitude in -180.0..180.0
            ) {
                RoyalWeatherRepository.fetchForLocation(
                    this, manualCity, manualLatitude, manualLongitude
                ) { result ->
                    runOnUiThread { weatherState = result }
                }
            } else {
                RoyalWeatherRepository.fetchForCity(this, manualCity) { result ->
                    runOnUiThread { weatherState = result }
                }
            }
            return
        }
        weatherState = WeatherUiState(
            city = "جارٍ تحديد الموقع",
            description = "جارٍ تحميل الطقس",
            isLoading = true
        )

        RoyalWeatherRepository.fetch(this) { result ->
            runOnUiThread {
                weatherState = result
            }
        }
    }
}
