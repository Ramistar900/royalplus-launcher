package com.royalplus.launcher

import com.royal.adaptive.RoyalAdaptiveHost
import com.royalplus.launcher.royalsounds.RoyalUiSound
import com.royalplus.launcher.royalsounds.RoyalUiSounds

import android.Manifest
import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private companion object { const val KEY_TRANSLATION_CONSENT = "floating_translation_consent" }
    private var translationOverlayEnabled by mutableStateOf(false)
    private var pendingTranslationOverlayStart = false
    private var weatherState by mutableStateOf(WeatherUiState())
    private var installedApps by mutableStateOf<List<LauncherApp>>(emptyList())
    private val installedAppsExecutor = Executors.newSingleThreadExecutor()
    private var installedAppsLoadGeneration = 0
    private var isDefaultHome by mutableStateOf(false)
    private var voiceSearchAvailable by mutableStateOf(false)
    private var voiceSearchResult by mutableStateOf("")
    private var showingCrashReport = false

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
            if (granted) continueTranslationPermissionFlow()
            else Toast.makeText(this, "يلزم إذن الميكروفون لتشغيل الترجمة", Toast.LENGTH_LONG).show()
        }

    private val translationProjectionPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val projectionData = result.data
            if (result.resultCode == RESULT_OK && projectionData != null) {
                startTranslationOverlayService(result.resultCode, projectionData)
            } else {
                translationOverlayEnabled = false
                getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE)
                    .edit().putBoolean(FloatingTranslationOverlayService.KEY_ENABLED, false).apply()
                Toast.makeText(this, "لم تمنح إذن التقاط صوت التطبيقات؛ لم يتم تشغيل الترجمة.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RoyalUiSounds.initialize(applicationContext, setOf(
            RoyalUiSound.NAV_BACK,
            RoyalUiSound.ACTION_DOWNLOAD,
            RoyalUiSound.MEDIA_PAUSE,
            RoyalUiSound.MEDIA_PLAY,
            RoyalUiSound.ACTION_SUCCESS,
            RoyalUiSound.ACTION_ERROR,
            RoyalUiSound.NAV_SELECT,
            RoyalUiSound.NAV_FOCUS_MOVE,
            RoyalUiSound.SELECT_TOGGLE_OFF,
            RoyalUiSound.SELECT_TOGGLE_ON,
            RoyalUiSound.NAV_OPEN_MENU,
            RoyalUiSound.NAV_CLOSE_MENU,
            RoyalUiSound.ACTION_ADD,
            RoyalUiSound.MEDIA_PREV,
            RoyalUiSound.MEDIA_NEXT
        ))
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
                MaterialTheme {
                    RoyalHomeScreen(
                    apps = installedApps,
                    weather = weatherState,
                    onRequestLocation = { requestLocationPermission() },
                    onRefreshWeather = { refreshWeather() },
                    onRefreshApps = { loadInstalledApps(forceRefresh = true) },
                    onChooseWeatherCity = { city -> saveAndLoadManualWeatherCity(city) },
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

    override fun onDestroy() {
        installedAppsLoadGeneration++
        installedAppsExecutor.shutdownNow()
        RoyalUiSounds.release()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (showingCrashReport) return
        RoyalPlusApplication.markStage(this, "MainActivity.onResume: restoring translation overlay state")
        if (pendingTranslationOverlayStart) {
            pendingTranslationOverlayStart = false
            if (Settings.canDrawOverlays(this) &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) continueTranslationPermissionFlow()
            else Toast.makeText(this, "لم تكتمل أذونات الترجمة العائمة", Toast.LENGTH_LONG).show()
        } else {
            val translationPreferences = getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE)
            val savedOverlayEnabled = translationPreferences.getBoolean(FloatingTranslationOverlayService.KEY_ENABLED, false)
            translationOverlayEnabled = savedOverlayEnabled
            if (savedOverlayEnabled &&
                Settings.canDrawOverlays(this) &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                val serviceIsRunning = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
                    .getRunningServices(Int.MAX_VALUE)
                    .any { it.service.className == FloatingTranslationOverlayService::class.java.name }
                if (serviceIsRunning || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    // Android 8/9 can restart its microphone session; API 29+ requires a fresh system grant.
                    val serviceIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
                        action = FloatingTranslationOverlayService.ACTION_SHOW
                    }
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
                        else startService(serviceIntent)
                    }
                } else {
                    translationPreferences.edit().putBoolean(FloatingTranslationOverlayService.KEY_ENABLED, false).apply()
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
        if (translationOverlayEnabled) {
            stopService(Intent(this, FloatingTranslationOverlayService::class.java))
            getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(FloatingTranslationOverlayService.KEY_ENABLED, false).apply()
            translationOverlayEnabled = false
            Toast.makeText(this, "تم إيقاف الترجمة وإيقاف التقاط الميكروفون", Toast.LENGTH_SHORT).show()
            return
        }

        val preferences = getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_TRANSLATION_CONSENT, false)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("تشغيل الترجمة العربية العائمة")
                .setMessage(
                    "على Android 10 فأحدث تطلب الميزة إذن النظام لالتقاط صوت التطبيقات مباشرةً. " +
                        "على Android 8/9 تستخدم ميكروفون التلفاز؛ لا يتيح هذان الإصداران التقاط الصوت الداخلي للتطبيقات. " +
                        "يُعالج الصوت محلياً دون رفعه، ويتطلب أول تشغيل تنزيل نموذج الصوت (~104 ميغابايت) ونموذج ترجمة اللغة عند الحاجة."
                )
                .setPositiveButton("متابعة") { _, _ ->
                    preferences.edit().putBoolean(KEY_TRANSLATION_CONSENT, true).apply()
                    continueTranslationPermissionFlow()
                }
                .setNegativeButton("إلغاء", null)
                .show()
            return
        }
        continueTranslationPermissionFlow()
    }

    private fun continueTranslationPermissionFlow() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            translationMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            pendingTranslationOverlayStart = true
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                pendingTranslationOverlayStart = false
                Toast.makeText(this, "تعذر فتح إعداد إذن الظهور فوق التطبيقات", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projectionManager = getSystemService(MediaProjectionManager::class.java)
            if (projectionManager == null) {
                Toast.makeText(this, "التقاط صوت التطبيقات غير مدعوم من نظام هذا الجهاز", Toast.LENGTH_LONG).show()
                return
            }
            translationProjectionPermission.launch(projectionManager.createScreenCaptureIntent())
        } else startTranslationOverlayService()
    }

    private fun startTranslationOverlayService(projectionResultCode: Int? = null, projectionData: Intent? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (projectionResultCode != RESULT_OK || projectionData == null)
        ) {
            translationOverlayEnabled = false
            Toast.makeText(this, "يلزم إذن النظام لالتقاط صوت التطبيقات.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            val serviceIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
                action = FloatingTranslationOverlayService.ACTION_SHOW
                if (projectionResultCode != null && projectionData != null) {
                    putExtra(FloatingTranslationOverlayService.EXTRA_PROJECTION_RESULT_CODE, projectionResultCode)
                    putExtra(FloatingTranslationOverlayService.EXTRA_PROJECTION_DATA, projectionData)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
            getSharedPreferences(FloatingTranslationOverlayService.PREFERENCES, MODE_PRIVATE)
                .edit().putBoolean(FloatingTranslationOverlayService.KEY_ENABLED, true).apply()
            translationOverlayEnabled = true
        } catch (_: Exception) {
            translationOverlayEnabled = false
            Toast.makeText(this, "تعذر تشغيل الترجمة العائمة على هذا الجهاز", Toast.LENGTH_LONG).show()
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

    private fun saveAndLoadManualWeatherCity(city: String) {
        val normalized = city.trim()
        if (normalized.isBlank()) return
        getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
            .edit().putString("weather_manual_city", normalized).apply()
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
            .edit().remove("weather_manual_city").apply()
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
        val manualCity = getSharedPreferences("royalplus_launcher_preferences", MODE_PRIVATE)
            .getString("weather_manual_city", null)
        if (!manualCity.isNullOrBlank()) {
            weatherState = WeatherUiState(
                city = manualCity,
                description = "جارٍ تحميل الطقس للمدينة المختارة",
                isLoading = true,
                isManualLocation = true
            )
            RoyalWeatherRepository.fetchForCity(this, manualCity) { result ->
                runOnUiThread { weatherState = result }
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
