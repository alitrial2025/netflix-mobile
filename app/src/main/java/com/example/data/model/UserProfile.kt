package com.example.data.model

import androidx.compose.runtime.Immutable
import java.security.MessageDigest

/** Same PIN digest format as the TV app, including legacy plaintext reads. */
object ProfilePin {
    private const val SALT = "netflix-pro-tv-2024"

    fun hash(pin: String?): String? {
        if (pin.isNullOrBlank()) return null
        if (pin.length == 64 && pin.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return pin.lowercase(java.util.Locale.ROOT)
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest((SALT + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun matches(stored: String?, entered: String): Boolean {
        if (stored.isNullOrBlank()) return true
        if (entered.isBlank()) return false
        return stored == entered || stored.equals(hash(entered), ignoreCase = true)
    }
}

@Immutable
data class UserProfile(
    val id: String,
    val name: String,
    val avatarColorHex: Long = 0xFF222222,
    val isKids: Boolean = false,
    val maxAge: Int = if (isKids) 12 else 18,
    val avatarType: AvatarType = AvatarType.CUSTOM,
    val avatarUrl: String? = null,
    val pin: String? = null,
    val language: String = "English",
    val autoplayNext: Boolean = true,
    val autoplayPreviews: Boolean = true,
    val gameHandle: String? = null,
    val audioLanguage: String = "Original",
    val subtitleLanguage: String = "Off"
) {
    val contentMaxAge: Int get() = if (isKids) maxAge.coerceIn(0, 12) else maxAge.coerceIn(0, 18)
    val hasMaturityRestriction: Boolean get() = contentMaxAge < 18
    val isKidProfile: Boolean
        get() = isKids || maxAge <= 12
}

enum class AvatarType {
    SMILEY, KIDS, CUSTOM
}

fun UserProfile.toEntity(): com.example.data.local.ProfileEntity {
    return com.example.data.local.ProfileEntity(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        avatarType = avatarType.name,
        isKids = isKids,
        maxAge = maxAge,
        pin = pin,
        language = language,
        autoplayNext = autoplayNext,
        autoplayPreviews = autoplayPreviews,
        gameHandle = gameHandle,
        audioLanguage = audioLanguage,
        subtitleLanguage = subtitleLanguage
    )
}

fun com.example.data.local.ProfileEntity.toUserProfile(): UserProfile {
    return UserProfile(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        avatarType = try {
            AvatarType.valueOf(avatarType)
        } catch (_: Exception) {
            AvatarType.CUSTOM
        },
        isKids = isKids,
        maxAge = maxAge,
        pin = pin,
        language = language,
        autoplayNext = autoplayNext,
        autoplayPreviews = autoplayPreviews,
        gameHandle = gameHandle,
        audioLanguage = audioLanguage,
        subtitleLanguage = subtitleLanguage
    )
}
