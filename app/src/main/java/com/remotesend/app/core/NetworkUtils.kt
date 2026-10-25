package com.remotesend.app.core

import com.remotesend.app.domain.model.NetworkAddress
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {
    private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun localAddresses(): List<NetworkAddress> {
        val result = mutableListOf<NetworkAddress>()
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        for (networkInterface in interfaces) {
            try {
                if (!networkInterface.isUp || networkInterface.isLoopback) continue
                for (address in networkInterface.inetAddresses) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val ip = address.hostAddress ?: continue
                        if (ip.startsWith("169.254.")) continue
                        result += NetworkAddress(
                            interfaceName = networkInterface.name,
                            ip = ip,
                            prefixLength = 24,
                        )
                    }
                }
            } catch (_: Exception) {
                // Interface disappeared while enumerating.
            }
        }

        return result
            .distinctBy { it.ip }
            .sortedWith(compareByDescending<NetworkAddress> {
                it.interfaceName.startsWith("wlan") || it.interfaceName.startsWith("ap")
            }.thenBy { it.interfaceName })
    }

    fun defaultIpv4(): String? {
        val addresses = localAddresses()
        return addresses.firstOrNull { isPrivateIpv4(it.ip) }?.ip ?: addresses.firstOrNull()?.ip
    }

    fun isValidIpv4(value: String): Boolean {
        val match = ipv4Regex.matchEntire(value.trim()) ?: return false
        return match.groupValues.drop(1).all { octet ->
            octet.toIntOrNull()?.let { it in 0..255 } == true
        }
    }

    fun isPrivateIpv4(value: String): Boolean {
        if (!isValidIpv4(value)) return false
        val parts = value.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size != 4) return false
        return parts[0] == 10 ||
            (parts[0] == 172 && parts[1] in 16..31) ||
            (parts[0] == 192 && parts[1] == 168)
    }

    fun subnetPrefix(value: String): String? {
        if (!isValidIpv4(value)) return null
        return value.substringBeforeLast('.')
    }

    fun hostsInSubnet(prefix: String, skip: String? = null): List<String> {
        val parts = prefix.split('.')
        if (parts.size != 3) return emptyList()
        if (parts.any { part ->
                val value = part.toIntOrNull() ?: return@any true
                value !in 0..255
            }
        ) {
            return emptyList()
        }
        return (1..254).mapNotNull { host ->
            val ip = "$prefix.$host"
            if (ip == skip) null else ip
        }
    }
}
