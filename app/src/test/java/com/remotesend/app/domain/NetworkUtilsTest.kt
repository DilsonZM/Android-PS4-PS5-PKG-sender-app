package com.remotesend.app.domain

import com.remotesend.app.core.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsTest {

    @Test
    fun `validates ipv4 addresses`() {
        assertTrue(NetworkUtils.isValidIpv4("192.168.1.10"))
        assertTrue(NetworkUtils.isValidIpv4("0.0.0.0"))
        assertTrue(NetworkUtils.isValidIpv4("255.255.255.255"))
        assertFalse(NetworkUtils.isValidIpv4("192.168.1.256"))
        assertFalse(NetworkUtils.isValidIpv4("192.168.1"))
        assertFalse(NetworkUtils.isValidIpv4("not-an-ip"))
    }

    @Test
    fun `detects private ranges`() {
        assertTrue(NetworkUtils.isPrivateIpv4("10.1.2.3"))
        assertTrue(NetworkUtils.isPrivateIpv4("172.16.0.1"))
        assertTrue(NetworkUtils.isPrivateIpv4("192.168.0.50"))
        assertFalse(NetworkUtils.isPrivateIpv4("8.8.8.8"))
        assertFalse(NetworkUtils.isPrivateIpv4("172.32.0.1"))
    }

    @Test
    fun `builds subnet prefix`() {
        assertEquals("192.168.1", NetworkUtils.subnetPrefix("192.168.1.25"))
        assertNull(NetworkUtils.subnetPrefix("bad"))
    }

    @Test
    fun `enumerates 254 hosts and skips local address`() {
        val hosts = NetworkUtils.hostsInSubnet("192.168.1", skip = "192.168.1.10")
        assertEquals(253, hosts.size)
        assertFalse("192.168.1.10" in hosts)
        assertTrue("192.168.1.1" in hosts)
        assertTrue("192.168.1.254" in hosts)
    }
}
