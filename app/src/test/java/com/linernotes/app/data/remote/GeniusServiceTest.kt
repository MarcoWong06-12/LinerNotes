package com.linernotes.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GeniusServiceTest {

    @Test
    fun testDataModelsInstantiation() {
        val searchResult = GeniusSongSearchResult(
            id = 452L,
            title = "Ms. Jackson",
            fullTitle = "Ms. Jackson by OutKast",
            artist = "OutKast",
            thumbUrl = "https://images.genius.com/thumb.jpg",
            coverUrl = "https://images.genius.com/cover.jpg",
            url = "https://genius.com/Outkast-ms-jackson-lyrics"
        )
        assertEquals(452L, searchResult.id)
        assertEquals("Ms. Jackson", searchResult.title)

        val detail = GeniusSongDetail(
            id = 452L,
            title = "Ms. Jackson",
            artist = "OutKast",
            descriptionPlain = "The second single from Stankonia...",
            releaseDate = "October 24, 2000",
            headerImageUrl = "https://images.genius.com/header.jpg",
            songArtImageUrl = "https://images.genius.com/art.jpg",
            producerCredits = "Earthtone III, Organized Noize",
            songUrl = "https://genius.com/Outkast-ms-jackson-lyrics"
        )
        assertEquals("October 24, 2000", detail.releaseDate)
        assertNotNull(detail.producerCredits)

        val annotation = GeniusAnnotationItem(
            id = 12345L,
            bodyPlain = "Andre 3000 explains the backstory...",
            bodyHtml = "<p>Andre 3000 explains the backstory...<img src=\"https://images.genius.com/pic.jpg\"></p>",
            verified = true,
            authorName = "Andre 3000",
            authorAvatarUrl = "https://images.genius.com/avatar.jpg",
            votesTotal = 42,
            url = "https://genius.com/12345",
            imageUrls = listOf("https://images.genius.com/pic.jpg")
        )
        assertEquals(true, annotation.verified)
        assertEquals(1, annotation.imageUrls.size)
        assertEquals("Andre 3000", annotation.authorName)
    }
}
