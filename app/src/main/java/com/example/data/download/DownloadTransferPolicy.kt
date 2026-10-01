package com.example.data.download

import java.net.URI

internal data class DirectTransferPlan(val append: Boolean, val expectedBytes: Long)

/** Reject responses that would turn partial or unsupported content into a completed download. */
internal object DownloadTransferPolicy {
    /** Reserve 100% for finalized assets; unknown lengths have no invented percentage. */
    fun progress(completed: Long, total: Long): Float = if (total <= 0L) 0f
        else (completed.toDouble() / total).toFloat().coerceIn(0f, .99f)

    fun directPlan(code: Int, existingBytes: Long, contentRange: String?, contentLength: Long): DirectTransferPlan {
        require(existingBytes >= 0L)
        if (code == 200) return DirectTransferPlan(false, contentLength)
        require(code == 206) { "Download server returned HTTP $code" }
        val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(contentRange.orEmpty())
            ?: error("Missing or invalid Content-Range")
        val (start, end, total) = range.destructured.toList().map { it.toLong() }
        require(start == existingBytes && end >= start && end < total) { "Incorrect resumed byte range" }
        require(contentLength < 0L || contentLength == end - start + 1L) { "Incorrect response length" }
        return DirectTransferPlan(existingBytes > 0L, total)
    }

    fun hlsSegments(playlist: String, baseUrl: String): List<String> {
        val lines = playlist.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        require(lines.firstOrNull() == "#EXTM3U" && "#EXT-X-ENDLIST" in lines) {
            "Only complete on-demand playlists can be downloaded"
        }
        require(lines.none { it.startsWith("#EXT-X-MAP:") || it.startsWith("#EXT-X-BYTERANGE:") ||
            it == "#EXT-X-DISCONTINUITY" ||
            (it.startsWith("#EXT-X-KEY:") && it != "#EXT-X-KEY:METHOD=NONE") }) {
            "This playlist requires an offline format that is not supported yet"
        }
        val segments = mutableListOf<String>()
        var expectingSegment = false
        for (line in lines) {
            if (line.startsWith("#EXTINF:")) {
                require(!expectingSegment) { "Missing segment URL" }
                expectingSegment = true
            } else if (!line.startsWith('#')) {
                require(expectingSegment) { "Unexpected playlist URL" }
                val uri = URI(baseUrl).resolve(line)
                require(uri.scheme in listOf("https", "http") && uri.host != null && uri.userInfo == null)
                segments += uri.toString()
                expectingSegment = false
            }
        }
        require(!expectingSegment && segments.isNotEmpty()) { "No complete video segments" }
        return segments
    }

    fun isHls(url: String): Boolean = url.contains(".m3u8", true) ||
        url.contains("playlist", true) || url.contains("hls", true)

    fun isComplete(actualBytes: Long, expectedBytes: Long): Boolean =
        actualBytes > 0L && (expectedBytes < 0L || actualBytes == expectedBytes)
}
