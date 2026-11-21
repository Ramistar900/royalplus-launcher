package com.royalplus.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * The TV creates a Wi-Fi Direct group as group owner.
 *
 * Android 10+ phones join the temporary DIRECT- network through
 * WifiNetworkSpecifier. This intentionally avoids relying on P2P MAC addresses,
 * which Android 10+ anonymizes for ordinary applications.
 */
internal class RoyalCastWifiDirectController(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var createGroupPending = false
    private var groupPollCount = 0
    private var onState: (String) -> Unit = {}
    private var onReceiverReady: ((String, String, String, String?) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    if (createGroupPending) requestGroupDetails()
                }
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        onState("ميزة Wi-Fi Direct متوقفة في إعدادات التلفاز.")
                    }
                }
            }
        }
    }

    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasRequiredPermissions(): Boolean = requiredPermissions().all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun isSupported(): Boolean =
        manager != null && appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

    private fun initialize(onStatus: (String) -> Unit): Boolean {
        onState = onStatus
        if (!isSupported()) {
            onState("هذا التلفاز لا يدعم Wi-Fi Direct.")
            return false
        }
        if (!hasRequiredPermissions()) {
            onState("امنح إذن الأجهزة القريبة أو الموقع لتشغيل Wi-Fi Direct.")
            return false
        }
        if (channel == null) {
            channel = manager?.initialize(appContext, Looper.getMainLooper()) {
                onState("انقطع ارتباط Wi-Fi Direct بالنظام. أعد المحاولة.")
            }
        }
        if (channel == null) {
            onState("تعذر تهيئة Wi-Fi Direct على هذا الجهاز.")
            return false
        }
        registerReceiver()
        return true
    }

    @Suppress("DEPRECATION")
    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    fun startGroupOwner(
        onStatus: (String) -> Unit,
        onReady: (host: String, ssid: String, passphrase: String, deviceAddress: String?) -> Unit
    ) {
        if (!initialize(onStatus)) return
        onReceiverReady = onReady
        createGroupPending = true
        groupPollCount = 0
        onState("يجري إنشاء مجموعة Wi-Fi Direct على التلفاز...")
        manager?.createGroup(channel, actionListener(
            success = {
                onState("تم إنشاء الشبكة المباشرة. يجري تجهيز بيانات الاقتران...")
                requestGroupDetails()
            },
            failure = { reason ->
                createGroupPending = false
                onState("تعذر إنشاء Wi-Fi Direct: $reason")
            }
        ))
    }

    private fun requestGroupDetails() {
        if (!createGroupPending) return
        manager?.requestGroupInfo(channel) { group ->
            manager?.requestConnectionInfo(channel) { info ->
                val host = info?.groupOwnerAddress?.hostAddress
                val ssid = group?.networkName
                val passphrase = group?.passphrase
                val deviceAddress = group?.owner?.deviceAddress
                if (group?.isGroupOwner == true && info?.groupFormed == true &&
                    info.isGroupOwner && !host.isNullOrBlank() &&
                    !ssid.isNullOrBlank() && !passphrase.isNullOrBlank()
                ) {
                    createGroupPending = false
                    onState("Wi-Fi Direct جاهز. امسح رمز QR من الهاتف للانضمام إلى الشبكة.")
                    onReceiverReady?.invoke(host, ssid, passphrase, deviceAddress)
                } else {
                    scheduleGroupInfoRetry()
                }
            }
        }
    }

    private fun scheduleGroupInfoRetry() {
        if (!createGroupPending || groupPollCount >= 30) {
            if (createGroupPending) {
                createGroupPending = false
                onState("لم يتم تجهيز شبكة Wi-Fi Direct. تحقق من دعم التلفاز وحاول مجدداً.")
            }
            return
        }
        groupPollCount += 1
        Handler(Looper.getMainLooper()).postDelayed({ requestGroupDetails() }, 500)
    }

    /**
     * Android 10+ joins the TV's P2P group as a legacy Wi-Fi client using the
     * one-session SSID and passphrase in the QR. The system shows its consent UI.
     */
    fun connectToReceiver(
        endpoint: RoyalCastEndpoint,
        onStatus: (String) -> Unit,
        onConnected: () -> Unit
    ) {
        onState = onStatus
        val ssid = endpoint.wifiDirectSsid
        val passphrase = endpoint.wifiDirectPassphrase
        if (ssid.isNullOrBlank() || passphrase.isNullOrBlank()) {
            onStatus("رمز Wi-Fi Direct لا يحتوي على بيانات الشبكة المطلوبة.")
            return
        }
        if (!hasRequiredPermissions()) {
            onStatus("امنح إذن الأجهزة القريبة أو الموقع للانضمام إلى Wi-Fi Direct.")
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoyalCastWifiDirectSession.connect(
                appContext, ssid, passphrase, onStatus, onConnected
            )
        } else {
            connectLegacyPeer(endpoint.deviceAddress, onStatus, onConnected)
        }
    }

    private fun connectLegacyPeer(
        deviceAddress: String?,
        onStatus: (String) -> Unit,
        onConnected: () -> Unit
    ) {
        if (deviceAddress.isNullOrBlank() || !deviceAddress.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))) {
            onStatus("هذا الإصدار من Android يحتاج عنوان جهاز Wi-Fi Direct صالحاً.")
            return
        }
        if (channel == null) {
            channel = manager?.initialize(appContext, Looper.getMainLooper()) {
                onStatus("انقطع ارتباط Wi-Fi Direct بالنظام. أعد المحاولة.")
            }
        }
        if (channel == null || manager == null) {
            onStatus("ميزة Wi-Fi Direct غير متاحة على هذا الهاتف.")
            return
        }
        registerReceiver()
        val targetAddress = deviceAddress
        onStatus("يجري البحث عن التلفاز عبر Wi-Fi Direct...")
        manager.discoverPeers(channel, actionListener(
            success = { manager.requestPeers(channel) { peers ->
                val peer = peers.deviceList.firstOrNull {
                    it.deviceAddress.equals(targetAddress, ignoreCase = true)
                }
                if (peer == null) {
                    onStatus("لم يظهر التلفاز في البحث المباشر. أعد إظهار رمز الاقتران.")
                    return@requestPeers
                }
                val config = WifiP2pConfig().apply {
                    deviceAddress = peer.deviceAddress
                    groupOwnerIntent = 0
                }
                manager.connect(channel, config, actionListener(
                    success = {
                        manager.requestConnectionInfo(channel) { info ->
                            if (info?.groupFormed == true && !info.isGroupOwner) onConnected()
                            else onStatus("يجري انتظار اكتمال اتصال Wi-Fi Direct...")
                        }
                    },
                    failure = { reason -> onStatus("فشل الاتصال المباشر: $reason") }
                ))
            } },
            failure = { reason -> onStatus("تعذر البحث عن أجهزة Wi-Fi Direct: $reason") }
        ))
    }

    private fun actionListener(success: () -> Unit, failure: (String) -> Unit) =
        object : WifiP2pManager.ActionListener {
            override fun onSuccess() = success()
            override fun onFailure(reason: Int) {
                failure(
                    when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "الجهاز لا يدعم الميزة"
                        WifiP2pManager.BUSY -> "النظام مشغول، أعد المحاولة"
                        WifiP2pManager.ERROR -> "خطأ في خدمة Wi-Fi Direct"
                        else -> "رمز الخطأ $reason"
                    }
                )
            }
        }

    fun stopGroup() {
        createGroupPending = false
        manager?.removeGroup(channel, actionListener({}, {}))
    }

    /** Unregisters the temporary TV broadcast receiver. */
    fun release() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }
}

/**
 * Keeps the local-only Wi-Fi request alive for the duration of the screen cast.
 * The service releases the process binding when casting stops.
 */
internal object RoyalCastWifiDirectSession {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var connectivity: ConnectivityManager? = null
    @Volatile private var callback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var connectedNetwork: Network? = null

    @Synchronized
    fun connect(
        context: Context,
        ssid: String,
        passphrase: String,
        onStatus: (String) -> Unit,
        onConnected: () -> Unit
    ) {
        release()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            onStatus("خدمة اتصال Wi-Fi غير متاحة على الهاتف.")
            return
        }
        val specifier = runCatching {
            WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build()
        }.getOrElse {
            onStatus("بيانات شبكة Wi-Fi Direct غير صالحة.")
            return
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainHandler.post {
                    val bound = runCatching { manager.bindProcessToNetwork(network) }.getOrDefault(false)
                    if (!bound) {
                        onStatus("اتصل الهاتف بالشبكة المباشرة لكن تعذر توجيه البث إليها.")
                        return@post
                    }
                    connectedNetwork = network
                    onStatus("اتصل الهاتف بشبكة Wi-Fi Direct. يمكنك الآن بدء مشاركة الشاشة.")
                    onConnected()
                }
            }

            override fun onUnavailable() {
                mainHandler.post {
                    onStatus("لم يكتمل الانضمام إلى شبكة Wi-Fi Direct. وافق على طلب Android وتأكد من قرب التلفاز.")
                }
            }

            override fun onLost(network: Network) {
                mainHandler.post {
                    if (connectedNetwork == network) {
                        runCatching { manager.bindProcessToNetwork(null) }
                        connectedNetwork = null
                        onStatus("انقطع اتصال Wi-Fi Direct. أعد الاتصال من رمز التلفاز.")
                    }
                }
            }
        }
        connectivity = manager
        callback = networkCallback
        onStatus("سيظهر طلب Android للانضمام إلى شبكة Wi-Fi Direct المؤقتة.")
        runCatching { manager.requestNetwork(request, networkCallback) }
            .onFailure {
                release()
                onStatus(it.message ?: "تعذر طلب اتصال Wi-Fi Direct.")
            }
    }

    @Synchronized
    fun release() {
        val manager = connectivity
        runCatching { manager?.bindProcessToNetwork(null) }
        val networkCallback = callback
        if (manager != null && networkCallback != null) {
            runCatching { manager.unregisterNetworkCallback(networkCallback) }
        }
        callback = null
        connectivity = null
        connectedNetwork = null
    }
}
