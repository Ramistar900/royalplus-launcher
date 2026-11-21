package com.royalplus.launcher

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Optional Wi-Fi Direct transport for ROYAL CAST.
 *
 * The TV creates the group and remains its owner. The phone discovers that
 * exact peer (identified by the address embedded in the TV's one-session QR)
 * and joins it before opening the normal ROYAL CAST signaling socket.
 */
internal class RoyalCastWifiDirectController(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private var channel: WifiP2pManager.Channel? = null
    private var receiverRegistered = false
    private var localDeviceAddress: String? = null
    private var targetDeviceAddress: String? = null
    private var createGroupPending = false
    private var connectPending = false
    private var connectionReported = false
    private var receiverReadyReported = false
    private var groupPollCount = 0
    private var onState: (String) -> Unit = {}
    private var onReceiverReady: ((String, String) -> Unit)? = null
    private var onClientConnected: ((String) -> Unit)? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    val device = wifiP2pDevice(intent)
                    val address = device?.deviceAddress
                    if (isValidDeviceAddress(address)) {
                        localDeviceAddress = address
                        if (createGroupPending) requestGroupInfo()
                    }
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> requestTargetPeer()
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    if (state == WifiP2pManager.WIFI_P2P_STATE_DISABLED) {
                        onState("ميزة Wi-Fi Direct متوقفة في إعدادات الجهاز.")
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
            onState("هذا الجهاز لا يدعم Wi-Fi Direct.")
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
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
        receiverRegistered = true
    }

    @Suppress("DEPRECATION")
    private fun wifiP2pDevice(intent: Intent): WifiP2pDevice? =
        if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, WifiP2pDevice::class.java)
        } else {
            intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        }

    fun startGroupOwner(
        onStatus: (String) -> Unit,
        onReady: (host: String, deviceAddress: String) -> Unit
    ) {
        if (!initialize(onStatus)) return
        onReceiverReady = onReady
        onClientConnected = null
        createGroupPending = true
        receiverReadyReported = false
        groupPollCount = 0
        localDeviceAddress = null
        onState("يجري إنشاء اتصال Wi-Fi Direct على التلفاز...")
        manager?.createGroup(channel, actionListener(
            success = {
                onState("تم إنشاء شبكة Wi-Fi Direct. يجري تجهيز رمز الاقتران...")
                requestGroupInfo()
            },
            failure = { reason ->
                createGroupPending = false
                onState("تعذر إنشاء Wi-Fi Direct: $reason")
            }
        ))
        requestDeviceInfoWhenAvailable()
    }

    private fun requestDeviceInfoWhenAvailable() {
        // Android 9 reports the local P2P address with THIS_DEVICE_CHANGED.
        // Newer Android versions may also provide it directly through requestDeviceInfo.
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                manager?.requestDeviceInfo(channel) { device ->
                    val address = device?.deviceAddress
                    if (isValidDeviceAddress(address)) {
                        localDeviceAddress = address
                        if (createGroupPending) requestGroupInfo()
                    }
                }
            }
        }
    }

    private fun requestGroupInfo() {
        if (!createGroupPending || receiverReadyReported) return
        manager?.requestConnectionInfo(channel) { info ->
            val host = info?.groupOwnerAddress?.hostAddress
            val address = localDeviceAddress
            if (info?.groupFormed == true && info.isGroupOwner &&
                !host.isNullOrBlank() && isValidDeviceAddress(address)
            ) {
                receiverReadyReported = true
                createGroupPending = false
                onState("Wi-Fi Direct جاهز. امسح الرمز الظاهر على التلفاز من الهاتف.")
                onReceiverReady?.invoke(host, address!!)
            } else {
                scheduleGroupInfoRetry()
            }
        }
    }

    private fun scheduleGroupInfoRetry() {
        if (!createGroupPending || groupPollCount >= 30) {
            if (createGroupPending) {
                createGroupPending = false
                onState("لم يتمكن الجهاز من تجهيز عنوان Wi-Fi Direct. تحقق من دعم التلفاز.")
            }
            return
        }
        groupPollCount += 1
        android.os.Handler(Looper.getMainLooper()).postDelayed({ requestGroupInfo() }, 500)
    }

    fun connectToReceiver(
        deviceAddress: String,
        onStatus: (String) -> Unit,
        onConnected: (host: String) -> Unit
    ) {
        if (!isValidDeviceAddress(deviceAddress)) {
            onStatus("عنوان جهاز Wi-Fi Direct الموجود في الرمز غير صالح.")
            return
        }
        if (!initialize(onStatus)) return
        targetDeviceAddress = deviceAddress
        onClientConnected = onConnected
        onReceiverReady = null
        connectPending = true
        connectionReported = false
        onState("يجري البحث عن تلفاز ROYAL+ عبر Wi-Fi Direct...")
        manager?.discoverPeers(channel, actionListener(
            success = {
                onState("تم بدء البحث. يجري العثور على التلفاز...")
                requestTargetPeer()
            },
            failure = { reason ->
                connectPending = false
                onState("تعذر البحث عن أجهزة Wi-Fi Direct: $reason")
            }
        ))
    }

    private fun requestTargetPeer() {
        if (!connectPending || connectStarted) return
        val expected = targetDeviceAddress ?: return
        manager?.requestPeers(channel) { peers ->
            val peer = peers.deviceList.firstOrNull {
                it.deviceAddress.equals(expected, ignoreCase = true)
            }
            if (peer == null) {
                onState("يجري البحث عن التلفاز. تأكد أن شاشة Wi-Fi Direct ظاهرة على التلفاز.")
                return@requestPeers
            }
            connectStarted = true
            val config = WifiP2pConfig().apply {
                deviceAddress = peer.deviceAddress
                groupOwnerIntent = 0
            }
            manager?.connect(channel, config, actionListener(
                success = {
                    onState("تم طلب الاتصال بالتلفاز. يجري تجهيز الشبكة المباشرة...")
                },
                failure = { reason ->
                    connectPending = false
                    connectStarted = false
                    onState("فشل الاتصال المباشر بالتلفاز: $reason")
                }
            ))
        }
    }

    private var connectStarted = false

    private fun requestConnectionInfo() {
        if (!connectPending || connectionReported) return
        manager?.requestConnectionInfo(channel) { info ->
            val host = info?.groupOwnerAddress?.hostAddress
            if (info?.groupFormed == true && !info.isGroupOwner && !host.isNullOrBlank()) {
                connectionReported = true
                connectPending = false
                onState("اتصل الهاتف بالتلفاز عبر Wi-Fi Direct.")
                onClientConnected?.invoke(host)
            }
        }
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
        connectPending = false
        receiverReadyReported = false
        connectStarted = false
        targetDeviceAddress = null
        manager?.removeGroup(channel, actionListener({}, { }))
    }

    /** Unregisters the temporary receiver without breaking an established P2P link. */
    fun release() {
        if (receiverRegistered) {
            runCatching { appContext.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }

    private fun isValidDeviceAddress(value: String?): Boolean =
        !value.isNullOrBlank() && value.matches(Regex("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}"))
}
