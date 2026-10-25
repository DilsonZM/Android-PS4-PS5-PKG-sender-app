package com.remotesend.app.data.server

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeHeaderTest {

    @Test
    fun `returns ignore without header`() {
        assertTrue(RangeHeader.parse(null, 100) is RangeResult.Ignore)
        assertTrue(RangeHeader.parse("", 100) is RangeResult.Ignore)
    }

    @Test
    fun `parses closed range`() {
        val result = RangeHeader.parse("bytes=10-19", 100) as RangeResult.Valid
        assertEquals(10L, result.start)
        assertEquals(10L, result.length)
    }

    @Test
    fun `parses open ended range`() {
        val result = RangeHeader.parse("bytes=90-", 100) as RangeResult.Valid
        assertEquals(90L, result.start)
        assertEquals(10L, result.length)
    }

    @Test
    fun `parses suffix range`() {
        val result = RangeHeader.parse("bytes=-25", 100) as RangeResult.Valid
        assertEquals(75L, result.start)
        assertEquals(25L, result.length)
    }

    @Test
    fun `clamps end to file size`() {
        val result = RangeHeader.parse("bytes=95-500", 100) as RangeResult.Valid
        assertEquals(95L, result.start)
        assertEquals(5L, result.length)
    }

    @Test
    fun `rejects out of bounds start`() {
        assertTrue(RangeHeader.parse("bytes=100-", 100) is RangeResult.Unsatisfiable)
    }

    @Test
    fun `rejects invalid spec`() {
        assertTrue(RangeHeader.parse("items=0-2", 100) is RangeResult.Ignore)
        assertTrue(RangeHeader.parse("bytes=abc-def", 100) is RangeResult.Ignore)
    }
}
