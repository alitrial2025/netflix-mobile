package com.example.data.model

val profilePlaybackLanguages = linkedMapOf(
    "English" to "en", "Spanish" to "es", "French" to "fr", "Portuguese" to "pt",
    "German" to "de", "Hindi" to "hi", "Japanese" to "ja", "Korean" to "ko",
    "Arabic" to "ar", "Swahili" to "sw", "Chinese" to "zh", "Italian" to "it"
)

fun playbackLanguageCode(preference: String): String? = profilePlaybackLanguages.entries
    .firstOrNull { preference.equals(it.key, true) || preference.startsWith("${it.key} ", true) }?.value
