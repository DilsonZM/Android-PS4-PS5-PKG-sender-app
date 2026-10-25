package com.remotesend.app.data.network

import com.remotesend.app.core.AppLogger
import com.remotesend.app.core.NetworkUtils
import com.remotesend.app.domain.model.ConsoleType
import com.remotesend.app.domain.model.LanCandidate
import com.remotesend.app.domain.model.ScanProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

class LanScanner(
    private val logger: AppLogger,
) {
    fun scan(
        localIp: String? = null,
        timeoutMs: Int = 220,
        preferPs5ForTcp: Boolean = true,
        rpiPort: Int = RPI_PORT,
        tcpPort: Int = TCP_PORT,
    ): Flow<ScanProgress> = channelFlow {
        val sourceIp = localIp ?: NetworkUtils.defaultIpv4()
        val prefix = sourceIp?.let { NetworkUtils.subnetPrefix(it) }
        if (prefix == null) {
            send(ScanProgress(0, 0))
            return@channelFlow
        }

        val hosts = NetworkUtils.hostsInSubnet(prefix, sourceIp)
        if (hosts.isEmpty()) {
            send(ScanProgress(0, 0))
            return@channelFlow
        }

        val total = hosts.size
        val scanned = AtomicInteger(0)
        val semaphore = Semaphore(48)
        val rpiPorts = listOf(rpiPort, RPI_PORT_PS4RPI, RPI_PORT).distinct()
        val tcpPorts = listOf(tcpPort, TCP_PORT).distinct()
        logger.info(
            "Escaneo",
            "Buscando consolas en $prefix.0/24 (RPI ${rpiPorts.joinToString()}, " +
                "TCP ${tcpPorts.joinToString()})",
        )

        coroutineScope {
            hosts.forEach { host ->
                launch(Dispatchers.IO) {
                    semaphore.withPermit {
                        val openPorts = mutableListOf<Int>()
                        var foundRpiPort: Int? = null
                        for (port in rpiPorts) {
                            if (probePort(host, port, timeoutMs)) {
                                foundRpiPort = port
                                openPorts += port
                                break
                            }
                        }

                        var foundControlPort: Int? = null
                        if (foundRpiPort == null) {
                            for (port in tcpPorts) {
                                if (probePort(host, port, timeoutMs)) {
                                    foundControlPort = port
                                    openPorts += port
                                    break
                                }
                            }
                        }

                        val count = scanned.incrementAndGet()
                        if (openPorts.isNotEmpty()) {
                            val type = when {
                                foundRpiPort != null -> ConsoleType.PS4_RPI
                                preferPs5ForTcp -> ConsoleType.PS5_ETAHEN
                                else -> ConsoleType.PS4_GOLDHEN
                            }
                            send(
                                ScanProgress(
                                    scanned = count,
                                    total = total,
                                    candidate = LanCandidate(
                                        host = host,
                                        openPorts = openPorts,
                                        suggestedType = type,
                                        rpiPort = foundRpiPort,
                                        controlPort = foundControlPort,
                                    ),
                                ),
                            )
                        } else if (count % 12 == 0 || count == total) {
                            send(ScanProgress(count, total))
                        }
                    }
                }
            }
        }

        send(ScanProgress(total, total))
    }

    private fun probePort(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val RPI_PORT = 12800
        const val RPI_PORT_PS4RPI = 12801
        const val TCP_PORT = 9090
    }
}
