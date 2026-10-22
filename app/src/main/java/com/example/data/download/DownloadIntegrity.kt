package com.example.data.download

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/** No remote addresses, signed links or account credentials are written into these receipts. */
internal data class DownloadChunk(val path: String, val offset: Long, val length: Long, val sha256: String)
internal data class DownloadReceipt(val version: Int = 1, val kind: String, val identity: String = "",
    val validator: String? = null, val chunks: List<DownloadChunk>)

enum class DownloadHealth { NOT_CHECKED, CHECKING, HEALTHY, DAMAGED, LEGACY, UNAVAILABLE }
data class DownloadInspection(val health: DownloadHealth, val totalBytes: Long = 0,
    val verifiedBytes: Long = 0, val damagedParts: Int = 0)

/** Receipts are recorded when media is received, not generated while checking possibly corrupt files. */
internal object DownloadIntegrity {
    const val CHUNK_BYTES = 1024 * 1024
    private val adapter = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build().adapter(DownloadReceipt::class.java)
    private val keyPattern = Regex("[A-Za-z0-9_-]{1,200}")
    fun receiptFile(directory: File, key: String): File { require(keyPattern.matches(key)); return File(directory, "$key.integrity.json") }
    fun read(directory: File, key: String): DownloadReceipt? = runCatching {
        val file = receiptFile(directory, key)
        if (!file.isFile || file.length() > 4 * 1024 * 1024) return null
        val receipt = adapter.fromJson(file.readText()) ?: return null
        require(receipt.version == 1 && receipt.kind in setOf("direct", "hls", "bundle") && receipt.chunks.size in 1..20_000)
        receipt.chunks.forEach { chunk ->
            require(chunk.length in 1..(256 * 1024 * 1024L) && chunk.offset >= 0 && chunk.offset <= Long.MAX_VALUE - chunk.length && chunk.sha256.matches(Regex("[a-f0-9]{64}")))
            require(chunk.path == "$key.ts" || chunk.path == "$key.mp4" || chunk.path == "$key.m3u8" || chunk.path.startsWith("$key.offline/"))
            safeFile(directory, chunk.path)
        }
        receipt.chunks.groupBy { it.path }.forEach { (_, chunks) ->
            var end = 0L
            chunks.forEach { require(it.offset == end); end += it.length }
        }
        receipt
    }.getOrNull()
    fun write(directory: File, key: String, receipt: DownloadReceipt, pending: Boolean = false) {
        val target = if (pending) File(directory, "$key.integrity.next") else receiptFile(directory, key)
        val temporary = File(directory, "$key.integrity.part")
        FileOutputStream(temporary).use { it.write(adapter.toJson(receipt).toByteArray()); it.fd.sync() }
        check(temporary.renameTo(target)) { "Could not save download verification data" }
    }
    fun promote(directory: File, key: String) {
        val next = File(directory, "$key.integrity.next")
        if (next.isFile) check(next.renameTo(receiptFile(directory, key))) { "Could not finalize download verification data" }
    }
    fun bundleReceipt(directory: File, key: String, entry: File, checkCancelled: () -> Unit = {}): DownloadReceipt {
        val files = File(directory, "$key.offline").walkTopDown().filter { it.isFile && (it.name.endsWith(".m3u8") || it.name.endsWith(".ts") || it.name.endsWith(".media") || it.name.endsWith(".m4s") || it.name.endsWith(".mp4") || it.name.endsWith(".aac")) }.sortedBy { it.path }.toList()
        val chunks = files.map { DownloadChunk(it.relativeTo(directory).invariantSeparatorsPath, 0, it.length(), checksum(it, checkCancelled = checkCancelled)) } +
            DownloadChunk("$key.m3u8", 0, entry.length(), checksum(entry, checkCancelled = checkCancelled))
        return DownloadReceipt(kind = "bundle", chunks = chunks)
    }
    fun safeFile(directory: File, relative: String): File {
        require(!relative.startsWith('/') && relative.split('/').none { it == ".." || it.isBlank() })
        val root = directory.canonicalFile
        return File(root, relative).canonicalFile.also { require(it.path.startsWith(root.path + File.separator)) }
    }
    fun checksum(file: File, offset: Long = 0, length: Long = file.length(), checkCancelled: () -> Unit = {}): String {
        require(offset >= 0 && length > 0 && file.isFile && offset <= file.length() - length)
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { input ->
            input.seek(offset); val buffer = ByteArray(64 * 1024); var remaining = length
            while (remaining > 0) {
                checkCancelled()
                val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                check(count > 0) { "Saved download is truncated" }
                digest.update(buffer, 0, count); remaining -= count
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    fun intact(directory: File, chunk: DownloadChunk, checkCancelled: () -> Unit = {}): Boolean = try {
        checksum(safeFile(directory, chunk.path), chunk.offset, chunk.length, checkCancelled) == chunk.sha256
    } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
      catch (_: Exception) { false }
    fun inspect(directory: File, key: String, entryPath: String?, checkCancelled: () -> Unit = {}): DownloadInspection {
        val entry = entryPath?.let { runCatching { File(it).canonicalFile }.getOrNull() }
        if (entry == null || !entry.path.startsWith(directory.canonicalPath + File.separator) || !entry.isFile || entry.length() == 0L)
            return DownloadInspection(DownloadHealth.DAMAGED, damagedParts = 1)
        val receipt = read(directory, key) ?: return DownloadInspection(DownloadHealth.LEGACY, entry.length())
        val total = receipt.chunks.sumOf { it.length }; var good = 0L; var bad = 0
        val badPaths = mutableSetOf<String>()
        val expectedSizes = receipt.chunks.groupBy { it.path }.mapValues { (_, chunks) -> chunks.sumOf { it.length } }
        for (chunk in receipt.chunks) {
            checkCancelled()
            if (intact(directory, chunk, checkCancelled)) good += chunk.length else { bad++; badPaths += chunk.path }
        }
        bad += expectedSizes.count { (path, size) -> path !in badPaths && safeFile(directory, path).length() != size }
        return DownloadInspection(if (bad == 0) DownloadHealth.HEALTHY else DownloadHealth.DAMAGED, total, good, bad)
    }
    fun directReceipt(file: File, destinationName: String, validator: String?, identity: String = "", checkCancelled: () -> Unit = {}): DownloadReceipt {
        require(file.length() > 0)
        val chunks = mutableListOf<DownloadChunk>(); var offset = 0L
        while (offset < file.length()) {
            val length = minOf(CHUNK_BYTES.toLong(), file.length() - offset)
            chunks += DownloadChunk(destinationName, offset, length, checksum(file, offset, length, checkCancelled)); offset += length
        }
        return DownloadReceipt(kind = "direct", identity = identity, validator = validator, chunks = chunks)
    }
    /** A new matching playlist can recover intact TS ranges even after the segment cache was removed. */
    fun sourceIdentity(url: String): String = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }
    fun seedHls(directory: File, key: String, identity: String, store: HlsSegmentStore, count: Int, checkCancelled: () -> Unit = {}) {
        val receipt = read(directory, key)?.takeIf { it.kind == "hls" && it.identity == identity && it.chunks.size == count } ?: return
        for ((index, chunk) in receipt.chunks.withIndex()) {
            checkCancelled()
            if (store.completed(index) != null || !intact(directory, chunk, checkCancelled)) continue
            val part = store.partial(index)
            RandomAccessFile(safeFile(directory, chunk.path), "r").use { input ->
                input.seek(chunk.offset)
                FileOutputStream(part).use { output ->
                    val buffer = ByteArray(64 * 1024); var remaining = chunk.length
                    while (remaining > 0) { checkCancelled(); val n = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt()); check(n > 0); output.write(buffer, 0, n); remaining -= n }
                    output.fd.sync()
                }
            }
            store.commit(index)
        }
    }
}
