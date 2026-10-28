package com.terminalf.app.data

import com.terminalf.app.core.tr
import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Cihazın yerel IPv4 adresi. Önce etkin ağın (Wi-Fi, Ethernet, mobil veri, hotspot) bağlantı
 * özelliklerine bakar; olmazsa ağ arayüzlerini tarar. Adres bulunamazsa "Bulunamadı".
 */
class NetworkService(context: Context? = null) {
    private val appContext = context?.applicationContext

    fun localIpAddress(): String =
        fromActiveNetwork()
            ?: fromInterfaces()
            ?: NOT_FOUND

    private fun fromActiveNetwork(): String? {
        return try {
            val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
            val active = cm.activeNetwork ?: return null
            val lp: LinkProperties = cm.getLinkProperties(active) ?: return null
            for (la in lp.linkAddresses) {
                val a = la.address
                if (a is Inet4Address && !a.isLoopbackAddress && !a.isLinkLocalAddress) {
                    val host = a.hostAddress
                    if (!host.isNullOrEmpty()) return host
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun fromInterfaces(): String? = try {
        val all = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
        val usable = all.filter { it.isUp && !it.isLoopback }
        // Wi-Fi / hotspot / Ethernet arayüzleri önce, sonra diğerleri (mobil veri vb.).
        val ordered = usable.sortedBy { nif ->
            val n = nif.name.lowercase()
            when {
                n.startsWith("wlan") -> 0
                n.startsWith("eth") || n.startsWith("ap") || n.startsWith("swlan") -> 1
                else -> 2
            }
        }
        var found: String? = null
        loop@ for (nif in ordered) {
            for (addr in nif.inetAddresses) {
                if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                    val host = addr.hostAddress
                    if (!host.isNullOrEmpty()) {
                        found = host
                        break@loop
                    }
                }
            }
        }
        found
    } catch (_: Exception) {
        null
    }

    private companion object {
        val NOT_FOUND: String get() = tr("Not found", "Bulunamadı")
    }
}
