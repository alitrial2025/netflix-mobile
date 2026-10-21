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
        val withArtwork = media.copy(logoUrl = "https://image.tmdb.org/t/p/w500/logo.png",
            heroPosterUrl = "https://image.tmdb.org/t/p/w780/textless.jpg")
        CatalogDiskCache(file).write(listOf(withArtwork))
        val restartedCache = CatalogDiskCache(file)
        restartedCache.write(emptyList())
        assertEquals(listOf(withArtwork), restartedCache.read())
    }

    @Test fun corruptCacheDoesNotCrashOfflineStartup() {
        val file = folder.newFile("catalog.json")
        file.writeText("unfinished response")
        assertTrue(CatalogDiskCache(file).read().isEmpty())
    }
    @Test fun olderCatalogWithoutTheNewArtworkFieldStillLoads() {
        val file = folder.newFile("old-catalog.json")
        CatalogDiskCache(file).write(listOf(media.copy(heroPosterUrl = "remove-me")))
        val adapter = com.squareup.moshi.Moshi.Builder().build().adapter(Any::class.java)
        @Suppress("UNCHECKED_CAST")
        val rows = adapter.fromJson(file.readText()) as List<MutableMap<String, Any?>>
        rows.single().remove("heroPosterUrl")
        file.writeText(adapter.toJson(rows))
        assertEquals(listOf(media), CatalogDiskCache(file).read())
    }

}
