package com.example.ui.screens

import org.junit.Assert.*
import org.junit.Test

class SeekThumbnailsTest {
    @Test fun relativeSpriteCuesUseExclusiveBoundariesAndResolveAgainstTheManifest() {
        val cues = parseSeekThumbnails("""
            WEBVTT

            1
            00:00:00.000 --> 00:00:10.000
            sprites/sheet.jpg#xywh=0,0,160,90

            00:00:10.000 --> 00:00:20.000
            sprites/sheet.jpg#xywh=160,0,160,90
        """.trimIndent(), "https://cdn.example.com/title/thumbs.vtt?token=test")
        assertEquals(2, cues.size)
        assertEquals("https://cdn.example.com/title/sprites/sheet.jpg", cues[0].imageUrl)
        assertEquals(0, seekThumbnailAt(cues, 9999)?.x)
        assertEquals(160, seekThumbnailAt(cues, 10000)?.x)
        assertNull(seekThumbnailAt(cues, 20000))
    }
    @Test fun malformedOrUnsafeThumbnailCuesAreIgnored() {
        val cues = parseSeekThumbnails("""
            00:00:10.000 --> 00:00:01.000
            sprite.jpg#xywh=0,0,160,90

            00:00:00.000 --> 00:00:10.000
            javascript:alert(1)

            00:00:00.000 --> 00:00:10.000
            http://insecure.example.com/sprite.jpg

            00:00:00.000 --> 00:00:10.000
            sprite.jpg#xywh=-10,0,0,90
        """.trimIndent(), "https://cdn.example.com/thumbs.vtt")
        assertTrue(cues.isEmpty())
    }
    @Test fun wholeImageCuesAndCueGapsDoNotInventFrames() {
        val cues = parseSeekThumbnails("00:00.000 --> 00:02.000\nframe.jpg\n\n00:04.000 --> 00:06.000\nframe2.jpg", "https://cdn.example.com/thumbs.vtt")
        assertEquals(2, cues.size)
        assertEquals(0, cues[0].width)
        assertNull(seekThumbnailAt(cues, 3000))
        assertNotNull(seekThumbnailAt(cues, 4000))
    }
}
