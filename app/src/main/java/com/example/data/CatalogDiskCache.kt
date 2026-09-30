package com.example.data

import com.example.data.model.MediaItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File

/** Public catalog metadata only; memberships and account data never enter this cache. */
internal class CatalogDiskCache(private val file: File) {
    private val adapter by lazy {
        Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
            .adapter<List<MediaItem>>(Types.newParameterizedType(List::class.java, MediaItem::class.java))
    }

    fun read(): List<MediaItem> = try {
        if (!file.isFile || file.length() > MAX_BYTES) emptyList()
        else adapter.fromJson(file.readText()).orEmpty()
    } catch (_: Exception) { emptyList() }

    fun write(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val json = adapter.toJson(items)
        if (json.toByteArray().size > MAX_BYTES) return
        val temporary = File(file.parentFile, "${file.name}.tmp")
        try {
            temporary.outputStream().use { it.write(json.toByteArray()); it.fd.sync() }
            check(temporary.renameTo(file)) { "Could not finalize catalog cache" }
        } catch (_: Exception) {
            temporary.delete()
        }
    }

    private companion object { const val MAX_BYTES = 4L * 1024 * 1024 }
}
