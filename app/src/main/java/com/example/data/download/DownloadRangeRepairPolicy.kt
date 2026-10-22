package com.example.data.download

/** A conditional range is reusable only for the same validated byte representation. */
internal object DownloadRangeRepairPolicy {
    fun strongValidator(value: String?): Boolean = value != null && value.matches(Regex("\"[!#-~]{0,510}\""))
    fun matches(code: Int, contentRange: String?, contentLength: Long, start: Long, length: Long,
        total: Long, expectedValidator: String, actualValidator: String?): Boolean {
        if (code != 206 || length <= 0 || start < 0 || total <= 0 || start > total - length ||
            !strongValidator(expectedValidator) || actualValidator != expectedValidator) return false
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(contentRange.orEmpty()) ?: return false
        val values = match.groupValues.drop(1).map { it.toLongOrNull() ?: return false }
        return values[0] == start && values[1] == start + length - 1 && values[2] == total &&
            (contentLength < 0 || contentLength == length)
    }
}
