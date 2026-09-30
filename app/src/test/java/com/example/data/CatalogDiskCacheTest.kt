package com.example.data

import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CatalogDiskCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val media = MediaItem("42", "Downloaded title", MediaType.MOVIE, "Description", "", 90, "13+", 2026,
        "2h", genres = listOf("Drama"), cast = emptyList(), director = "Director")

    @Test fun metadataSurvivesProcessRestartAndEmptyRefresh() {
        val file = folder.newFile("catalog.json")
        CatalogDiskCache(file).write(listOf(media))
        val restartedCache = CatalogDiskCache(file)
        restartedCache.write(emptyList())
        assertEquals(listOf(media), restartedCache.read())
    }

    @Test fun corruptCacheDoesNotCrashOfflineStartup() {
        val file = folder.newFile("catalog.json")
        file.writeText("unfinished response")
        assertTrue(CatalogDiskCache(file).read().isEmpty())
    }
}
