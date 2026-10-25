package com.remotesend.app.domain

import com.remotesend.app.domain.util.PkgNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PkgNamingTest {

    @Test
    fun `safe file name removes unsafe characters`() {
        val safe = PkgNaming.safeFileName("My Game (v1.0) / CUSA00001.pkg")
        assertEquals("My_Game__v1.0____CUSA00001.pkg", safe)
        assertTrue(safe.endsWith(".pkg"))
    }

    @Test
    fun `safe file name appends extension`() {
        assertEquals("game.pkg", PkgNaming.safeFileName("game"))
    }

    @Test
    fun `extracts title id from file name`() {
        assertEquals("CUSA09311", PkgNaming.titleIdFromName("UP0001-cusa09311_00-test.pkg"))
        assertEquals("PPSA01234", PkgNaming.titleIdFromName("PPSA01234-game.pkg"))
        assertNull(PkgNaming.titleIdFromName("game.pkg"))
    }

    @Test
    fun `extracts content id from file name`() {
        assertEquals(
            "UP0001-CUSA09311_00-ULCQUEST00000002",
            PkgNaming.contentIdFromName(
                "UP0001-CUSA09311_00-ULCQUEST00000002.pkg",
            ),
        )
    }

    @Test
    fun `derives title id from content id`() {
        assertEquals(
            "CUSA09311",
            PkgNaming.titleIdFromContentId("UP0001-CUSA09311_00-ULCQUEST00000002"),
        )
    }

    @Test
    fun `pkg id is deterministic and short`() {
        val first = PkgNaming.pkgId("content://test/one")
        val second = PkgNaming.pkgId("content://test/one")
        assertEquals(first, second)
        assertEquals(24, first.length)
    }

    @Test
    fun `formats bytes`() {
        assertEquals("0 B", PkgNaming.formatBytes(0))
        assertEquals("1.00 KB", PkgNaming.formatBytes(1024))
        assertEquals("1.00 MB", PkgNaming.formatBytes(1024L * 1024))
    }

    @Test
    fun `formats durations`() {
        assertEquals("30s", PkgNaming.formatDuration(30))
        assertEquals("2m 5s", PkgNaming.formatDuration(125))
        assertEquals("1h 1m", PkgNaming.formatDuration(3_660))
        assertNull(PkgNaming.formatDuration(0))
    }
}
