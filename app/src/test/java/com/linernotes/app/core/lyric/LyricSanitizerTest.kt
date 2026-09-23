package com.linernotes.app.core.lyric

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricSanitizerTest {

    @Test
    fun `test dictionary decensoring for common masked words`() {
        assertEquals("Life's a Bitch", LyricSanitizer.decensorLine("Life's a B***h"))
        assertEquals("life's a bitch", LyricSanitizer.decensorLine("life's a b***h"))
        assertEquals("straight out the fucking dungeons of rap", LyricSanitizer.decensorLine("straight out the ****ing dungeons of rap"))
        assertEquals("Where fake niggas don't make it back", LyricSanitizer.decensorLine("Where fake n****s don't make it back"))
        assertEquals("I don't know how to start this shit, yo, now", LyricSanitizer.decensorLine("I don't know how to start this s***, yo, now"))
    }

    @Test
    fun `test reference based decensoring for pure asterisks`() {
        val censored = "when you're gonna go Life's a ***** and then you die;"
        val reference = "when you're gonna go Life's a bitch and then you die;"
        val result = LyricSanitizer.decensorLine(censored, reference)
        assertEquals("when you're gonna go Life's a bitch and then you die;", result)
    }

    @Test
    fun `test full lyrics decensoring with timestamps and chinese translation preservation`() {
        val censoredLrc = """
            [01:04.06] Life's a ***** and then you die, that's why we get high
            [01:09.24] Life's a B***h and then you die, that's why we puff lye
            [01:14.58] Where fake n****s don't make it back
        """.trimIndent()

        val refLrc = """
            [01:04.06] Life's a bitch and then you die, that's why we get high
            [01:09.24] Life's a bitch and then you die, that's why we puff lye
            [01:14.58] Where fake niggas don't make it back
        """.trimIndent()

        val sanitized = LyricSanitizer.decensorLyrics(censoredLrc, refLrc)
        assertTrue(sanitized.contains("Life's a bitch and then you die, that's why we get high"))
        assertTrue(sanitized.contains("Life's a Bitch and then you die, that's why we puff lye"))
        assertTrue(sanitized.contains("Where fake niggas don't make it back"))
    }

    @Test
    fun `test decensor title`() {
        assertEquals("Life's a Bitch", LyricSanitizer.decensorTitle("Life's a B***h"))
    }
}
