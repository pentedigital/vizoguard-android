package com.vizoguard.vpn.vpn

import android.net.VpnService
import android.os.ParcelFileDescriptor
import com.vizoguard.vpn.util.Tag
import com.vizoguard.vpn.util.VizoLogger
import io.nekohasekai.libbox.InterfaceUpdateListener
import io.nekohasekai.libbox.NetworkInterface
import io.nekohasekai.libbox.NetworkInterfaceIterator
import io.nekohasekai.libbox.Notification
import io.nekohasekai.libbox.PlatformInterface
import io.nekohasekai.libbox.TunOptions
import io.nekohasekai.libbox.WIFIState

class PlatformInterfaceImpl(
    private val service: VpnService
) : PlatformInterface {

    private var tunFd: ParcelFileDescriptor? = null

    // === Real implementations ===

    override fun openTun(options: TunOptions): Int {
        val builder = (service as VpnTunnelService).Builder()
            .setSession("Vizoguard VPN")
            .setMtu(options.mtu)

        // Add addresses from TUN options
        val inet4Iter = options.inet4Address
        while (inet4Iter.hasNext()) {
            val prefix = inet4Iter.next()
            builder.addAddress(prefix.address(), prefix.prefix())
        }

        // Add all-traffic routes
        builder.addRoute("0.0.0.0", 0)
        builder.addRoute("::", 0)

        // DNS servers
        builder.addDnsServer("1.1.1.1")
        builder.addDnsServer("8.8.8.8")

        val fd = builder.establish()
            ?: throw Exception("VPN permission not granted")
        tunFd = fd
        return fd.fd
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        service.protect(fd)
    }

    override fun writeLog(message: String) {
        VizoLogger.d(Tag.SERVICE, "[libbox] $message")
    }

    override fun usePlatformAutoDetectInterfaceControl(): Boolean = true
    override fun useProcFS(): Boolean = false

    // === Stubs (safe defaults) ===

    override fun findConnectionOwner(ipProtocol: Int, srcAddr: String, srcPort: Int, dstAddr: String, dstPort: Int): Int = -1
    override fun packageNameByUid(uid: Int): String = ""
    override fun uidByPackageName(packageName: String): Int = -1

    override fun startDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}
    override fun closeDefaultInterfaceMonitor(listener: InterfaceUpdateListener) {}

    override fun getInterfaces(): NetworkInterfaceIterator = object : NetworkInterfaceIterator {
        override fun hasNext(): Boolean = false
        override fun next(): NetworkInterface? = null
    }

    override fun underNetworkExtension(): Boolean = false
    override fun includeAllNetworks(): Boolean = false
    override fun readWIFIState(): WIFIState? = null
    override fun clearDNSCache() {}
    override fun sendNotification(notification: Notification) {}

    fun closeTun() {
        tunFd?.close()
        tunFd = null
    }
}
