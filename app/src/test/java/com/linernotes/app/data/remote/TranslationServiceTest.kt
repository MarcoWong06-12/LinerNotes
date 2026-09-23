package com.linernotes.app.data.remote

import com.linernotes.app.core.preference.AiPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class TranslationServiceTest {

    @Test
    fun testYoudaoTranslationLive() = runBlocking {
        val mockPrefs = mock(AiPreferences::class.java)
        val service = TranslationService(mockPrefs)

        val lrc = """[00:01.00]Hello world
[00:04.00]The world is yours
[00:07.00]Music is life"""

        val result = service.translateTrack("The World Is Yours", lrc, "zh")
        assertNotNull(result)
        assertFalse("Translated lyrics should not be empty", result.translatedLyrics.isBlank())
        assertTrue("Should contain Chinese translation", result.translatedLyrics.contains("世界") || result.translatedLyrics.contains("你好"))
        assertTrue("Should preserve timestamps", result.translatedLyrics.contains("[00:01.00]"))
    }
}
