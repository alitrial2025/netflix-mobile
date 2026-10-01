package com.example.data.download

import java.net.URI

internal data class OfflineHlsVariant(val url: String, val bandwidth: Long,
    val codecs: String?, val audioUrl: String?, val audioName: String?, val audioLanguage: String?)
internal data class OfflineHlsResource(val url: String, val initialization: Boolean = false, val fileName: String)
internal data class OfflineHlsTrack(val playlist: String, val resources: List<OfflineHlsResource>)

/** Preserve the selected variant's audio group; unrelated groups must never reject a title. */
internal object OfflineHlsPlan {
    private val attributes = Regex("""([A-Z0-9-]+)=("[^"]*"|[^,]*)""")
    fun attributes(line: String): Map<String, String> = attributes.findAll(line.substringAfter(':'))
        .associate { it.groupValues[1] to it.groupValues[2].removeSurrounding("\"") }

    fun variant(master: String, url: String, highQuality: Boolean, maxVideoHeight: Int = Int.MAX_VALUE): OfflineHlsVariant {
        val lines = master.lines().map { it.trim() }
        require(lines.firstOrNull() == "#EXTM3U") { "Invalid download playlist" }
        val variants = lines.mapIndexedNotNull { index, line ->
            if (!line.startsWith("#EXT-X-STREAM-INF:")) null else {
                val data = attributes(line)
                val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() && !it.startsWith('#') }
                    ?: error("Missing video variant")
                data to resolve(url, next)
            }
        }.sortedByDescending { it.first["BANDWIDTH"]?.toLongOrNull() ?: 0L }
        if (variants.isEmpty()) return OfflineHlsVariant(url, 0, null, null, null, null)
        val limit = if (highQuality) maxVideoHeight else minOf(maxVideoHeight, 720)
        val eligible = variants.filter { (it.first["RESOLUTION"]?.substringAfter('x')?.toIntOrNull() ?: 0) <= limit }
        // A fixed-resolution provider stream cannot be transcoded here. Preserve playback
        // using the lowest available variant when the provider offers no smaller source.
        val choices = eligible.ifEmpty { listOf(variants.last()) }
        val (data, video) = choices.first()
        val group = data["AUDIO"]
        val audios = lines.filter { it.startsWith("#EXT-X-MEDIA:") }.map(::attributes)
            .filter { it["TYPE"] == "AUDIO" && it["GROUP-ID"] == group }
        val audio = audios.firstOrNull { it["DEFAULT"] == "YES" }
            ?: audios.firstOrNull { it["AUTOSELECT"] == "YES" } ?: audios.firstOrNull()
        return OfflineHlsVariant(video, data["BANDWIDTH"]?.toLongOrNull() ?: 0L, data["CODECS"],
            audio?.get("URI")?.let { resolve(url, it) }, audio?.get("NAME"), audio?.get("LANGUAGE"))
    }

    /** Complete local VOD manifests retain init segments and discontinuities for Media3. */
    fun track(playlist: String, base: String): OfflineHlsTrack {
        val lines = playlist.lines().map { it.trim() }.filter { it.isNotEmpty() }
        require(lines.firstOrNull() == "#EXTM3U" && "#EXT-X-ENDLIST" in lines) { "Incomplete download playlist" }
        val resources = mutableListOf<OfflineHlsResource>()
        var expectingSegment = false
        val local = lines.map { line ->
            when {
                line.startsWith("#EXT-X-KEY:") -> {
                    require(attributes(line)["METHOD"] == "NONE") { "Encrypted offline format is unavailable" }
                    line
                }
                line.startsWith("#EXT-X-BYTERANGE:") -> error("Byte-range offline format is unavailable")
                line.startsWith("#EXT-X-MAP:") -> {
                    val data = attributes(line)
                    require("BYTERANGE" !in data) { "Byte-range offline format is unavailable" }
                    val remote = data["URI"] ?: error("Missing initialization segment")
                    val index = resources.size
                    val resolved = resolve(base, remote)
                    val fileName = localName(index, resolved, true)
                    resources += OfflineHlsResource(resolved, true, fileName)
                    "#EXT-X-MAP:URI=\"$fileName\""
                }
                line.startsWith("#EXTINF:") -> {
                    require(!expectingSegment) { "Missing media segment" }
                    expectingSegment = true
                    line
                }
                !line.startsWith('#') -> {
                    require(expectingSegment) { "Unexpected playlist resource" }
                    expectingSegment = false
                    val index = resources.size
                    val resolved = resolve(base, line)
                    val fileName = localName(index, resolved, false)
                    resources += OfflineHlsResource(resolved, false, fileName)
                    fileName
                }
                else -> {
                    require(!line.contains("URI=")) { "Unsupported playlist resource" }
                    line
                }
            }
        }.joinToString("\n", postfix = "\n")
        require(!expectingSegment && resources.any { !it.initialization }) { "No complete media segments" }
        return OfflineHlsTrack(local, resources)
    }

    private fun localName(index: Int, url: String, initialization: Boolean): String {
        val extension = URI(url).path.substringAfterLast('.', "").lowercase()
            .takeIf { it in setOf("ts", "m4s", "mp4", "aac") } ?: if (initialization) "mp4" else "ts"
        return "$index.$extension"
    }

    private fun resolve(base: String, value: String): String = URI(base).resolve(value).also {
        require(it.scheme in setOf("http", "https") && it.host != null && it.userInfo == null)
    }.toString()
}
