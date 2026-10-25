package com.terminalf.app.data

import java.net.Inet4Address
import java.net.NetworkInterface

/** Wi-Fi arayüzünün yerel IPv4 adresi. Bulunamazsa "Bulunamadı". */
class NetworkService {
    fun localIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()?.toList() ?: emptyList()
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                if (!nif.name.startsWith("wlan")) continue
                for (addr in nif.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty()) return host
                    }
                }
            }
            "Bulunamadı"
        } catch (_: Exception) {
            "Bulunamadı"
        }
    }
}
