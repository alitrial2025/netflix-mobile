package com.example.ui.viewmodel

data class IntroWindow(val startSec: Int, val endSec: Int)

/** Conservative subtitle markers: generic music and silence are not evidence of an intro. */
internal object EpisodePlaybackPolicy {
    private val timestamp = Regex("(\\d{1,2}:)?(\\d{2}):(\\d{2})[.,](\\d{3})")

    fun detectIntro(subtitles: String, durationSec: Int): IntroWindow? {
        if (durationSec < 300) return null
        val cues = subtitles.replace("\r\n", "\n").replace('\r', '\n')
            .split(Regex("\\n\\s*\\n")).mapNotNull { block ->
                val lines = block.lines()
                val index = lines.indexOfFirst { it.contains("-->") }
                if (index < 0) return@mapNotNull null
                val times = timestamp.findAll(lines[index]).toList()
                if (times.size != 2) return@mapNotNull null
                fun seconds(match: MatchResult): Int {
                    val hours = match.groupValues[1].trimEnd(':').toIntOrNull() ?: 0
                    return hours * 3600 + match.groupValues[2].toInt() * 60 + match.groupValues[3].toInt()
                }
                Triple(seconds(times[0]), seconds(times[1]), lines.drop(index + 1).joinToString(" ").lowercase())
            }.filter { it.second > it.first && it.first >= 0 }.sortedBy { it.first }
        val index = cues.indexOfFirst { cue -> cue.first in 10..300 &&
            listOf("opening theme", "theme song", "title theme", "main title theme").any { it in cue.third } }
        if (index < 0) return null
        val cue = cues[index]
        val end = cues.getOrNull(index + 1)?.first?.takeIf { it > cue.second && it - cue.first <= 90 } ?: cue.second
        return IntroWindow(cue.first, end).takeIf { end - cue.first in 10..90 && end < durationSec }
    }
}
