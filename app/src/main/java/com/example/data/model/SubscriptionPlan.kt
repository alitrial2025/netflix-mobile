package com.example.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SubscriptionPlan(
    val id: String,
    val name: String,
    val priceKes: Int,
    val priceUsd: String,
    val quality: String,
    val resolution: String,
    val supportedDevices: String,
    val screens: Int,
    val maxProfiles: Int,
    val downloadDevices: Int,
    val spatialAudio: Boolean,
    val isPopular: Boolean = false,
    val catalogAccess: String = "Full catalog"
) {
    val durationDays: Int get() = 30
    val maxVideoHeight: Int get() = when (id) {
        "plan_mobile" -> 480; "plan_basic" -> 720; "plan_standard" -> 1080
        "plan_premium" -> 2160; else -> 0
    }
    val maxDownloads: Int get() = when (id) {
        "plan_mobile" -> 3; "plan_basic" -> 5; "plan_standard" -> 25
        "plan_premium" -> 999; else -> 0
    }
    val smartNextEpisode: Boolean get() = id == "plan_standard" || id == "plan_premium"
    val downloadsForYou: Boolean get() = id == "plan_premium"
    val clips: Boolean get() = id == "plan_premium"
    val games: Boolean get() = id == "plan_standard" || id == "plan_premium"
}


object SubscriptionPlans {
    val PLANS = listOf(
        SubscriptionPlan(
            id = "plan_mobile",
            name = "Mobile",
            priceKes = 150,
            priceUsd = "$2.00",
            quality = "Good",
            resolution = "480p (SD)",
            supportedDevices = "Mobile phone, tablet",
            screens = 1,
            maxProfiles = 1,
            downloadDevices = 1,
            spatialAudio = false,
            isPopular = false,
            catalogAccess = "Selected catalog (some titles locked)"
        ),
        SubscriptionPlan(
            id = "plan_basic",
            name = "Basic",
            priceKes = 550,
            priceUsd = "$6.00",
            quality = "Good",
            resolution = "720p (HD)",
            supportedDevices = "Android TV, mobile phone, tablet",
            screens = 1,
            maxProfiles = 2,
            downloadDevices = 1,
            spatialAudio = false,
            isPopular = false,
            catalogAccess = "Expanded catalog (fewer titles locked)"
        ),
        SubscriptionPlan(
            id = "plan_standard",
            name = "Standard",
            priceKes = 950,
            priceUsd = "$10.00",
            quality = "Great",
            resolution = "1080p (Full HD)",
            supportedDevices = "Android TV, mobile phone, tablet",
            screens = 2,
            maxProfiles = 4,
            downloadDevices = 2,
            spatialAudio = false,
            isPopular = true,
            catalogAccess = "Unlimited full catalog"
        ),
        SubscriptionPlan(
            id = "plan_premium",
            name = "Premium",
            priceKes = 1350,
            priceUsd = "$14.00",
            quality = "Best",
            resolution = "Up to 4K + HDR",
            supportedDevices = "Android TV, mobile phone, tablet",
            screens = 4,
            maxProfiles = 5,
            downloadDevices = 6,
            spatialAudio = true,
            isPopular = false,
            catalogAccess = "Unlimited full catalog + 4K HDR"
        )
    )

    fun getById(id: String): SubscriptionPlan {
        return PLANS.find { it.id == id } ?: PLANS[2]
    }

    const val PERIOD_MS = 30L * 24 * 60 * 60 * 1000

    /** Every payment adds exactly 30 days; early same-plan renewals retain paid time. */
    fun oneMonthExpiry(paymentTimeMs: Long, currentExpiryMs: Long = 0L): Long {
        require(paymentTimeMs > 0 && currentExpiryMs >= 0) { "Invalid membership time" }
        return Math.addExact(maxOf(paymentTimeMs, currentExpiryMs), PERIOD_MS)
    }

}

@Immutable
data class UserSubscription(
    val status: String = "NONE", // ACTIVE, NONE, EXPIRED
    val planId: String = "plan_guest",
    val planName: String = "Guest",
    val amount: Int = 0,
    val currency: String = "KES",
    val paymentReference: String = "",
    val mpesaReceipt: String = "",
    val subscribedAt: Long = 0L,
    val expiresAt: Long = 0L
) {
    companion object {
        @Volatile var clock: () -> Long = { System.currentTimeMillis() }
    }

    val isGuest: Boolean
        get() = planId == "plan_guest"

    val isActive: Boolean
        get() = SubscriptionPlans.PLANS.any { it.id == planId } &&
            com.example.data.RenewalPolicy.grantsAccess(status, expiresAt, clock())

    val accessEndsAt: Long get() = com.example.data.RenewalPolicy.accessEndsAt(expiresAt)
    val isInRenewalGrace: Boolean get() = isActive && expiresAt <= clock()
    val renewalReminderDue: Boolean get() = isActive && com.example.data.RenewalPolicy.reminderDue(expiresAt, clock())

    val isTvAllowed: Boolean
        get() = isActive && !planId.equals("plan_mobile", ignoreCase = true)

    val isGamesAllowed: Boolean
        get() = isActive && (planId == "plan_standard" || planId == "plan_premium")

    val isClipsAllowed: Boolean
        get() = isActive && planId == "plan_premium"

    val maxProfiles: Int
        get() = if (!isActive) 1 else when (planId) {
            "plan_guest" -> 1
            "plan_mobile" -> 1
            "plan_basic" -> 2
            "plan_standard" -> 4
            "plan_premium" -> 5
            else -> 1
        }

    val maxDownloads: Int
        get() = if (!isActive) 0 else SubscriptionPlans.PLANS.firstOrNull { it.id == planId }?.maxDownloads ?: 0

    val maxVideoHeight: Int
        get() = if (!isActive) 0 else SubscriptionPlans.PLANS.firstOrNull { it.id == planId }?.maxVideoHeight ?: 0

    val isSmartNextEpisodeAllowed: Boolean
        get() = isActive && SubscriptionPlans.PLANS.firstOrNull { it.id == planId }?.smartNextEpisode == true

    val isDownloadsForYouAllowed: Boolean
        get() = isActive && SubscriptionPlans.PLANS.firstOrNull { it.id == planId }?.downloadsForYou == true

    val isSpatialAudioAllowed: Boolean
        get() = isActive && SubscriptionPlans.PLANS.firstOrNull { it.id == planId }?.spatialAudio == true


    val daysRemaining: Int
        get() = if (isActive) {
            val remaining = (expiresAt - clock()).coerceAtLeast(0L)
            ((remaining + 86_399_999L) / 86_400_000L).toInt().coerceAtLeast(0)
        } else 0

    /**
     * Determines whether a movie or TV show is locked based on the current subscription plan.
     * - Mobile plan: Locks some movies and TV shows (~38% of catalog).
     * - Basic plan: Locks movies and TV shows, but not as many as mobile (~18% of catalog).
     * - Standard & Premium: 0 locked (unlimited full access).
     * - Guest: Full streaming locked (trailer preview only).
     */
    fun isMediaLocked(mediaId: String, mediaTitle: String = ""): Boolean {
        if (!isActive) return true
        if (planId == "plan_standard" || planId == "plan_premium") return false

        // Deterministic pseudo-random distribution based on media identifier
        val hash = kotlin.math.abs((mediaId.hashCode() * 31 + mediaTitle.hashCode() * 17 + "netflix_tier_catalog_lock".hashCode()).toLong())
        val pct = hash % 100

        return when (planId) {
            "plan_mobile" -> pct < 38 // ~38% locked on Mobile plan
            "plan_basic" -> pct < 18  // ~18% locked on Basic plan (subset of mobile)
            else -> true
        }
    }

    /**
     * Backward compatibility wrapper for movie/tv lock checks.
     */
    fun isMovieLocked(
        releaseYear: String = "",
        isTrendingOrVip: Boolean = false,
        isTvDevice: Boolean = false,
        mediaId: String = ""
    ): Boolean {
        if (!isActive) return true
        if (isTvDevice && !isTvAllowed) return true
        if (mediaId.isNotEmpty()) {
            return isMediaLocked(mediaId)
        }
        return false
    }
}
