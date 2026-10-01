package com.example.data.download

import java.io.File
import java.io.FileOutputStream

/** Manifests are published only after every referenced resource is synced and verified. */
internal object OfflineHlsBundle {
    fun write(file: File, text: String) {
        file.parentFile!!.mkdirs()
        val temp = File(file.parentFile, file.name + ".part")
        FileOutputStream(temp).use { it.write(text.toByteArray()); it.fd.sync() }
        check(temp.renameTo(file)) { "Could not save offline playlist" }
    }

    fun master(key: String, variant: OfflineHlsVariant): String {
        fun quoted(value: String) = value.replace("\"", "").replace("\n", "").replace("\r", "")
        val codec = variant.codecs?.let { ",CODECS=\"${quoted(it)}\"" }.orEmpty()
        val language = variant.audioLanguage?.let { ",LANGUAGE=\"${quoted(it)}\"" }.orEmpty()
        return "#EXTM3U\n#EXT-X-VERSION:7\n" +
            "#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID=\"offline\",NAME=\"${quoted(variant.audioName ?: "Default")}\",DEFAULT=YES,AUTOSELECT=YES$language,URI=\"$key.offline/audio/index.m3u8\"\n" +
            "#EXT-X-STREAM-INF:BANDWIDTH=${variant.bandwidth.coerceAtLeast(1)}$codec,AUDIO=\"offline\"\n" +
            "$key.offline/video/index.m3u8\n"
    }
}
