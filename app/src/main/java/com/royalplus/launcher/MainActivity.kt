package com.royalplus.launcher

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.app.role.RoleManager
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
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
    private var isDefaultHome by mutableStateOf(false)
    private var voiceSearchAvailable by mutableStateOf(false)
    private var voiceSearchResult by mutableStateOf("")

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
            if (granted) {
                continueTranslationPermissionFlow()
            } else {
                Toast.makeText(this, "يلزم إذن الميكروفون لالتقاط صوت الفيديو محلياً", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceSearchAvailable = SpeechRecognizer.isRecognitionAvailable(this)
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

        setContent {
            MaterialTheme {
                RoyalHomeScreen(
                    apps = installedApps,
                    weather = weatherState,
                    onRequestLocation = { requestLocationPermission() },
                    onRefreshWeather = { refreshWeather() },
                    onChooseWeatherCity = { city -> saveAndLoadManualWeatherCity(city) },
                    onUseDeviceLocation = { clearManualWeatherCityAndUseDeviceLocation() },
                    onLaunchApp = { app ->
                        try {
                            startActivity(app.launchIntent)
                        } catch (_: Exception) {
                            // The app may have been removed or blocked after the launcher list was loaded.
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

        // Start weather without a mandatory GPS permission; the repository falls back to IP location.
        refreshWeather()
    }

    override fun onResume() {
        super.onResume()
        if (pendingTranslationOverlayStart) {
            pendingTranslationOverlayStart = false
            if (Settings.canDrawOverlays(this) &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            ) {
                startTranslationOverlayService()
            } else {
                Toast.makeText(this, "لم تكتمل أذونات الترجمة العائمة", Toast.LENGTH_LONG).show()
            }
        } else {
            translationOverlayEnabled = getSharedPreferences(
                FloatingTranslationOverlayService.PREFERENCES,
                MODE_PRIVATE
            ).getBoolean(FloatingTranslationOverlayService.KEY_ENABLED, false)
        }
        isDefaultHome = isRoyalPlusDefaultHome()
        installedApps = LauncherAppsRepository.load(
            packageManager = packageManager,
            ownPackageName = packageName
        )
        RoyalAdhanScheduler.scheduleNext(this)
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
                    "تلتقط الميزة صوت الفيديو عبر ميكروفون الجهاز وتعالجه محلياً دون رفع الصوت إلى الإنترنت. " +
                        "يتطلب أول تشغيل تنزيل نموذج صوت متعدد اللغات بحجم يقارب 104 ميغابايت، " +
                        "وقد تُنزّل نماذج ترجمة اللغات عند الحاجة. بعد ذلك يعمل المحرك دون اتصال."
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
        startTranslationOverlayService()
    }

    private fun startTranslationOverlayService() {
        try {
            val serviceIntent = Intent(this, FloatingTranslationOverlayService::class.java).apply {
                action = FloatingTranslationOverlayService.ACTION_SHOW
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    } else {
                        startActivity(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    }
                    return
                }
            } catch (_: Exception) {
                // Fall back to the device's supported Home settings screen.
            }
        }

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    return roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
            } catch (_: Exception) {
                // Use the standard Home intent check below as a fallback.
            }
        }
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
