package com.example.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.R
import com.example.data.model.AvatarType
import com.example.data.model.Episode
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.UserProfile

object CatalogData {

    val defaultProfiles = emptyList<UserProfile>()

    // Premium handcrafted offline catalog
    val offlineMedia = emptyList<MediaItem>()

    val gamesList = listOf(
        com.example.data.model.GameItem(
            id = "game_squid_unleashed",
            title = "Squid Game: Unleashed",
            developer = "Netflix Games Studio",
            category = "Multiplayer Battle Royale",
            maturityRating = "16+",
            sizeDisplay = "1.4 GB",
            description = "Prepare for fast-paced, heart-pounding action in this 32-player multiplayer showdown. Team up with friends or face deadly challenges in iconic childhood games.",
            bannerRes = R.drawable.img_game_squid_unleashed_1787645966569
        ),
        com.example.data.model.GameItem(
            id = "game_gta_sa",
            title = "GTA: San Andreas – The Definitive Edition",
            developer = "Rockstar Games",
            category = "Action & Open World",
            maturityRating = "18+",
            sizeDisplay = "2.8 GB",
            description = "Experience the blockbuster classic, updated for mobile with across-the-board enhancements including brilliant lighting, high-resolution textures, and increased draw distances.",
            bannerRes = R.drawable.img_game_gta_san_andreas_1787645978423
        ),
        com.example.data.model.GameItem(
            id = "game_hades",
            title = "Hades (Netflix Edition)",
            developer = "Supergiant Games",
            category = "Roguelike Action RPG",
            maturityRating = "16+",
            sizeDisplay = "1.8 GB",
            description = "Defy the god of the dead as you hack and slash out of the Underworld in this god-like rogue-like dungeon crawler. Wield the powers and mythic weapons of Olympus.",
            bannerRes = R.drawable.img_game_hades_1787645991083
        ),
        com.example.data.model.GameItem(
            id = "game_monument_3",
            title = "Monument Valley 3",
            developer = "ustwo games",
            category = "Puzzle & Adventure",
            maturityRating = "Everyone",
            sizeDisplay = "520 MB",
            description = "Embark on an emotional voyage through impossible architecture and geometric wonders in the latest chapter of the award-winning series, only on Netflix.",
            bannerRes = R.drawable.img_game_monument_valley_1787646003139
        ),
        com.example.data.model.GameItem(
            id = "game_oxenfree_2",
            title = "Oxenfree II: Lost Signals",
            developer = "Night School Studio",
            category = "Supernatural Mystery",
            maturityRating = "16+",
            sizeDisplay = "980 MB",
            description = "TVs turn on and off. Planes lose radar. Radio stations can't broadcast through the static. Riley Povermire returns to her hometown to investigate a new paranormal signal.",
            bannerRes = R.drawable.img_hero_stranger
        ),
        com.example.data.model.GameItem(
            id = "game_dead_cells",
            title = "Dead Cells: Return to Castlevania",
            developer = "Motion Twin / Playdigious",
            category = "Metroidvania Action",
            maturityRating = "16+",
            sizeDisplay = "1.1 GB",
            description = "Play as an alchemical experiment exploring an ever-changing castle. Features all DLCs including Return to Castlevania with classic weapons and Richter Belmont.",
            bannerRes = R.drawable.img_game_hades_1787645991083
        ),
        com.example.data.model.GameItem(
            id = "game_tmnt",
            title = "TMNT: Shredder's Revenge",
            developer = "Tribute Games / Dotemu",
            category = "Arcade Beat 'em Up",
            maturityRating = "10+",
            sizeDisplay = "1.2 GB",
            description = "Leonardo, Michelangelo, Donatello and Raphael reunite in a bodacious, pixel-perfect beat 'em up featuring blistering ninja combos and full controller support.",
            bannerRes = R.drawable.img_game_squid_unleashed_1787645966569
        ),
        com.example.data.model.GameItem(
            id = "game_fm26",
            title = "Football Manager 2026 Touch",
            developer = "Sports Interactive / SEGA",
            category = "Sports Strategy",
            maturityRating = "Everyone",
            sizeDisplay = "1.5 GB",
            description = "Take the fast track to football glory. Craft a powerhouse squad, dominate tactical masterclasses, and lead world-class clubs to the summit of the beautiful game.",
            bannerRes = R.drawable.img_game_gta_san_andreas_1787645978423
        ),
        com.example.data.model.GameItem(
            id = "game_stranger_things",
            title = "Stranger Things: 1984",
            developer = "BonusXP / Netflix",
            category = "Retro Action Adventure",
            maturityRating = "12+",
            sizeDisplay = "380 MB",
            description = "Join Hopper and the kids on action-packed retro missions around Hawkins and the Upside Down in this stylized 80s pixel-art adventure.",
            bannerRes = R.drawable.img_hero_stranger
        )
    )

    // Reactive list of MediaItems initialized with offline content
    var allMedia: List<MediaItem> by mutableStateOf(offlineMedia)

    fun getTrending(): List<MediaItem> = allMedia.filter { it.isTrending && !it.isComingSoon }
    fun getTop10(): List<MediaItem> = allMedia.filter { it.top10Rank != null }.sortedBy { it.top10Rank }
    fun getTop10TvShows(): List<MediaItem> = allMedia.filter { it.type == MediaType.TV_SHOW && !it.isComingSoon }.sortedBy { it.top10Rank ?: 99 }.take(10)
    fun getTop10Movies(): List<MediaItem> = allMedia.filter { it.type == MediaType.MOVIE && !it.isComingSoon }.sortedBy { it.top10Rank ?: 99 }.take(10)
    fun getTvShows(): List<MediaItem> = allMedia.filter { it.type == MediaType.TV_SHOW && !it.isComingSoon }
    fun getMovies(): List<MediaItem> = allMedia.filter { it.type == MediaType.MOVIE && !it.isComingSoon }
    fun getOriginals(): List<MediaItem> = allMedia.filter { it.isOriginal && !it.isComingSoon }
    fun getComingSoon(): List<MediaItem> = allMedia.filter { it.isComingSoon }
    fun getAction(): List<MediaItem> = allMedia.filter { it.genres.any { g -> g.contains("Action", ignoreCase = true) } && !it.isComingSoon }
    fun getSciFi(): List<MediaItem> = allMedia.filter { it.genres.any { g -> (g.contains("Sci-Fi", ignoreCase = true) || g.contains("Science Fiction", ignoreCase = true) || g.contains("Cyberpunk", ignoreCase = true)) } && !it.isComingSoon }
    fun getDrama(): List<MediaItem> = allMedia.filter { it.genres.any { g -> (g.contains("Drama", ignoreCase = true) || g.contains("Romance", ignoreCase = true)) } && !it.isComingSoon }
    fun getById(id: String): MediaItem? = allMedia.find { it.id == id }
}
