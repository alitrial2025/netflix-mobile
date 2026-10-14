package com.example.data

import org.junit.Assert.*
import org.junit.Test

class StreamSessionPolicyTest {
    @Test fun legitimateHlsPathsAndJsonTitlesCannotStartARateLimit() {
        assertFalse(StreamSessionPolicy.isRateLimited(200,
            "#EXTM3U\n#EXT-X-STREAM-INF:FRAME-RATE=24.000\nvideo.m3u8"))
        assertFalse(StreamSessionPolicy.isRateLimited(200,
            "#EXTM3U\n#EXTINF:10,\nhttps://cdn.example/rate-limit/segment.ts\n#EXT-X-ENDLIST"))
        assertFalse(StreamSessionPolicy.isRateLimited(200,
            """{"title":"Rate Limited","description":"Too many requests for this movie","sources":[]}"""))
    }

    @Test fun realProviderRejectionsAndWaitingClipsStillStartARateLimit() {
        assertTrue(StreamSessionPolicy.isRateLimited(429, ""))
        assertTrue(StreamSessionPolicy.isRateLimited(200, "Too many requests"))
        assertTrue(StreamSessionPolicy.isRateLimited(200, """{"error":"rate_limit"}"""))
        assertTrue(StreamSessionPolicy.isRateLimited(200, """{"status":"error","message":"Too many requests"}"""))
        assertTrue(StreamSessionPolicy.isRateLimited(200,
            "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nhttps://cdn.example/files/220884/video.m3u8"))
    }
}
