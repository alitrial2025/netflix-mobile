package com.example.data.download

import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest

/** Only whole, synced and checksum-verified segments can be reused after interruption. */
internal class HlsSegmentStore(private val directory: File, identity: String) {
    init {
        val marker = File(directory, "identity")
        if (marker.takeIf { it.isFile }?.readText() != identity) {
            check(!directory.exists() || directory.deleteRecursively()) { "Could not clear stale segment data" }
            directory.mkdirs()
            FileOutputStream(marker).use { it.write(identity.toByteArray()); it.fd.sync() }
        }
    }

    fun completed(index: Int): File? {
        val file = File(directory, "$index.ts")
        val receipt = File(directory, "$index.sha256")
        if (!file.isFile || file.length() == 0L || !receipt.isFile) return null
        return file.takeIf { runCatching { checksum(it) == receipt.readText() }.getOrDefault(false) }
    }

    fun partial(index: Int) = File(directory, "$index.part")

    fun commit(index: Int): File {
        val part = partial(index)
        require(part.isFile && part.length() >= 188) { "Incomplete video segment" }
        part.inputStream().use { input ->
            val sample = ByteArray(minOf(188 * 5L, part.length()).toInt())
            var offset = 0
            while (offset < sample.size) { val count = input.read(sample, offset, sample.size - offset); if (count < 0) break; offset += count }
            require((0 until offset step 188).all { sample[it] == 0x47.toByte() }) { "Unsupported or invalid video segment" }
        }
        val digest = checksum(part)
        val file = File(directory, "$index.ts")
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
