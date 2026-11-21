package com.royalplus.launcher

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.util.concurrent.atomic.AtomicBoolean

internal data class RoyalCaptionTvDevice(
    val name: String,
    val host: String,
    val port: Int
)

/**
 * Discovers ROYAL+ caption receivers on the current local network.
 * The user enters only the short pairing code shown on the TV; IP addresses
 * are resolved automatically by Android Network Service Discovery (mDNS).
 */
internal class RoyalCaptionNsdDiscovery(context: Context) {
    private val manager = context.applicationContext
        .getSystemService(Context.NSD_SERVICE) as NsdManager
    private val stopped = AtomicBoolean(false)
    private val resolving = mutableSetOf<String>()
    @Volatile private var listener: NsdManager.DiscoveryListener? = null

    fun start(
        onFound: (RoyalCaptionTvDevice) -> Unit,
        onFailure: (String) -> Unit
    ) {
        if (listener != null) return
        stopped.set(false)
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (stopped.get() || !serviceInfo.serviceType.contains(SERVICE_TYPE_PREFIX)) return
                val key = serviceInfo.serviceName
                synchronized(resolving) {
                    if (!resolving.add(key)) return
                }
                runCatching {
                    manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            synchronized(resolving) { resolving.remove(key) }
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            synchronized(resolving) { resolving.remove(key) }
                            if (stopped.get()) return
                            val host = info.host?.hostAddress.orEmpty()
                            val port = info.port
                            if (RoyalCaptionTransport.isPrivateIpv4Address(host) &&
                                port == RoyalCaptionTransport.PORT
                            ) {
                                onFound(
                                    RoyalCaptionTvDevice(
                                        name = info.serviceName.ifBlank { "ROYAL+ TV" },
                                        host = host,
                                        port = port
                                    )
                                )
                            }
                        }
                    })
                }.onFailure {
                    synchronized(resolving) { resolving.remove(key) }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(resolving) { resolving.remove(serviceInfo.serviceName) }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
                listener = null
                onFailure("تعذر البحث عن التلفاز على الشبكة المحلية (رمز $errorCode).")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                runCatching { manager.stopServiceDiscovery(this) }
            }
        }
        listener = discovery
        runCatching {
            manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discovery)
        }.onFailure {
            listener = null
            onFailure(it.localizedMessage ?: "تعذر بدء البحث عن التلفاز.")
        }
    }

    fun stop() {
        if (!stopped.compareAndSet(false, true)) return
        val current = listener
        listener = null
        if (current != null) runCatching { manager.stopServiceDiscovery(current) }
        synchronized(resolving) { resolving.clear() }
    }

    companion object {
        const val SERVICE_TYPE = "_royal-caption._tcp."
        private const val SERVICE_TYPE_PREFIX = "_royal-caption._tcp"
    }
}

/** Advertises the TV receiver only while its local caption server is running. */
internal object RoyalCaptionNsdRegistration {
    fun register(
        context: Context,
        onFailure: (String) -> Unit
    ): NsdManager.RegistrationListener {
        val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "ROYAL+ Caption"
            serviceType = RoyalCaptionNsdDiscovery.SERVICE_TYPE
            port = RoyalCaptionTransport.PORT
            setAttribute("version", "1")
        }
        val registration = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onFailure("تعذر إظهار التلفاز في البحث التلقائي (رمز $errorCode).")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        manager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registration)
        return registration
    }

    fun unregister(context: Context, listener: NsdManager.RegistrationListener?) {
        if (listener == null) return
        val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        runCatching { manager.unregisterService(listener) }
    }
}
