package com.example.data.download

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest

/** Only whole, synced and checksum-verified segments can be reused after interruption. */
internal class HlsSegmentStore(private val directory: File, identity: String, private val offlineResource: Boolean = false, private val resourceNames: List<String>? = null) {
    init {
        val marker = File(directory, "identity")
        if (marker.takeIf { it.isFile }?.readText() != identity) {
            check(!directory.exists() || directory.deleteRecursively()) { "Could not clear stale segment data" }
            directory.mkdirs()
            FileOutputStream(marker).use { it.write(identity.toByteArray()); it.fd.sync() }
        }
    }

    fun completed(index: Int): File? {
        val file = File(directory, resourceNames?.get(index) ?: "$index.${if (offlineResource) "media" else "ts"}")
        val receipt = File(directory, "$index.sha256")
        if (!file.isFile || file.length() == 0L || !receipt.isFile) return null
        return file.takeIf { runCatching { checksum(it) == receipt.readText() }.getOrDefault(false) }
    }

    fun partial(index: Int) = File(directory, "$index.part")

    fun commit(index: Int): File {
        val part = partial(index)
        require(part.isFile && part.length() >= if (offlineResource) 8 else 188) { "Incomplete media segment" }
        part.inputStream().use { input ->
            val sample = ByteArray(minOf(1024L, part.length()).toInt())
            var offset = 0
            while (offset < sample.size) { val count = input.read(sample, offset, sample.size - offset); if (count < 0) break; offset += count }
            val ts = offset >= 188 && (0 until offset step 188).all { sample[it] == 0x47.toByte() }
            val bmff = offset >= 8 && String(sample, 4, 4, Charsets.US_ASCII) in setOf("ftyp", "styp", "moov", "moof", "sidx", "free")
            var audioStart = 0
            if (offset >= 10 && String(sample, 0, 3, Charsets.US_ASCII) == "ID3") {
                require((6..9).all { sample[it].toInt() and 0x80 == 0 }) { "Invalid audio metadata" }
                audioStart = 10 + (6..9).fold(0) { size, i -> (size shl 7) or (sample[i].toInt() and 0x7f) }
                if (audioStart + 2 > offset && audioStart + 2 < part.length()) {
                    part.inputStream().use { audio ->
                        var skipped = 0L
                        while (skipped < audioStart) { val count = audio.skip(audioStart - skipped); check(count > 0); skipped += count }
                        require(audio.read() == 0xff && audio.read() and 0xf6 == 0xf0) { "Invalid audio segment" }
                    }
                    audioStart = -1
                }
            }
            val aac = audioStart == -1 || (audioStart + 2 <= offset &&
                sample[audioStart].toInt() and 0xff == 0xff && sample[audioStart + 1].toInt() and 0xf6 == 0xf0)
            require(ts || (offlineResource && (bmff || aac))) { "Unsupported or invalid media segment" }
        }
        val digest = checksum(part)
        val file = File(directory, resourceNames?.get(index) ?: "$index.${if (offlineResource) "media" else "ts"}")
        check(part.renameTo(file)) { "Could not save video segment" }
        val receiptPart = File(directory, "$index.sha256.part")
        FileOutputStream(receiptPart).use { it.write(digest.toByteArray()); it.fd.sync() }
        check(receiptPart.renameTo(File(directory, "$index.sha256"))) { "Could not save segment checkpoint" }
        return file
    }

    fun clear() { directory.deleteRecursively() }

    companion object {
        fun identity(playlist: String, segments: List<String>, quality: Boolean): String {
            // Query tokens may renew; media sequence, timing and resource paths must still agree.
            val structure = playlist.lineSequence().map { it.trim() }.filter { it.startsWith("#") }.joinToString("\n")
            val resources = segments.joinToString("\n") { URI(it).let { uri -> "${uri.scheme}://${uri.authority}${uri.path}" } }
            return hash(("$quality\n$structure\n$resources").toByteArray())
        }
        private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        private fun checksum(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
