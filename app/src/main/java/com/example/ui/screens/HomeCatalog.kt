package com.example.ui.screens

import androidx.compose.ui.unit.dp
import com.example.data.model.MediaItem
import com.example.data.model.MediaSection
import com.example.data.model.MediaType
import com.example.data.model.UserProfile
import com.example.ui.components.featuredTrendingMedia
import com.example.ui.viewmodel.CategoryFilter

internal data class HomeCatalog(val hero: MediaItem?, val sections: List<MediaSection>)

internal fun matchesHomeFilter(media: MediaItem, filter: CategoryFilter, genre: String?, profile: UserProfile) =
    (!profile.hasMaturityRestriction || media.isKidSafe(profile.contentMaxAge)) &&
        (filter != CategoryFilter.MOVIES || media.type == MediaType.MOVIE) &&
        (filter != CategoryFilter.TV_SHOWS || media.type == MediaType.TV_SHOW) && media.matchesGenre(genre)

/** Derive hero and rows together off Main so every row respects the same filter. */
internal fun buildHomeCatalog(
    source: List<MediaItem>, personalizedMedia: List<MediaItem>, categoryFilter: CategoryFilter,
    selectedGenre: String?, activeProfile: UserProfile
): HomeCatalog {
    val catalogMedia = source.filter { matchesHomeFilter(it, categoryFilter, selectedGenre, activeProfile) }
        .distinctBy { it.type to it.id }
    val playable = catalogMedia.filter { !it.isComingSoon }
    val heroMedia = featuredTrendingMedia(playable)
        ?: playable.firstOrNull { !it.backdropUrl.isNullOrBlank() }
        ?: playable.firstOrNull()
    val rawSections = mutableListOf<Triple<String, String, List<MediaItem>>>()
    val movies = catalogMedia.filter { it.type == MediaType.MOVIE && !it.isComingSoon }
    val tvShows = catalogMedia.filter { it.type == MediaType.TV_SHOW && !it.isComingSoon }
    val top10 = catalogMedia.filter { it.top10Rank != null }.sortedBy { it.top10Rank }
    val trending = catalogMedia.filter { it.isTrending && !it.isComingSoon }
    val originals = catalogMedia.filter { it.isOriginal && !it.isComingSoon }
    val comingSoon = catalogMedia.filter { it.isComingSoon }

    val usedMediaIds = mutableSetOf<String>()
    heroMedia?.let { usedMediaIds.add("${it.type}:${it.id}") }

    fun filterFresh(list: List<MediaItem>, minCount: Int = 4): List<MediaItem> {
        val fresh = list.filter { !usedMediaIds.contains("${it.type}:${it.id}") }
        val result = if (fresh.size >= minCount) fresh else list.distinctBy { it.type to it.id }
        result.take(10).forEach { usedMediaIds.add("${it.type}:${it.id}") }
        return result
    }

    when (categoryFilter) {
        CategoryFilter.ALL, CategoryFilter.GAMES -> {
            if (top10.isNotEmpty()) {
                rawSections.add(Triple("section_top10", "Top 10 Today in Your Country", top10))
                top10.take(5).forEach { usedMediaIds.add("${it.type}:${it.id}") }
            }
            val picks = personalizedMedia.filter { candidate -> catalogMedia.any { it.id == candidate.id && it.type == candidate.type } }
            if (picks.isNotEmpty()) rawSections.add(Triple("section_personalized", "Top Picks for ${activeProfile.name}", filterFresh(picks)))
            if (trending.isNotEmpty()) rawSections.add(Triple("section_trending", "Trending Now", filterFresh(trending)))
            if (comingSoon.isNotEmpty()) rawSections.add(Triple("section_coming_soon", "Worth the Wait / Coming Soon", comingSoon))
            if (originals.isNotEmpty()) rawSections.add(Triple("section_originals", "Only on NetflixPro", filterFresh(originals)))

            val actionSciFi = catalogMedia.filter { it.matchesGenre("Action") || it.matchesGenre("Sci-Fi") }
            if (actionSciFi.isNotEmpty()) rawSections.add(Triple("section_action_scifi", "Action & Sci-Fi Thrillers", filterFresh(actionSciFi)))

            val crimeSuspense = catalogMedia.filter { it.matchesGenre("Crime Thrillers") || it.matchesGenre("Crime") }
            if (crimeSuspense.isNotEmpty()) rawSections.add(Triple("section_crime", "Crime TV Shows & Mystery Thrillers", filterFresh(crimeSuspense)))

            val drama = catalogMedia.filter { it.matchesGenre("Drama") }
            if (drama.isNotEmpty()) rawSections.add(Triple("section_drama", "Critically Acclaimed Dramas", filterFresh(drama)))

            val comedy = catalogMedia.filter { it.matchesGenre("Comedy") }
            if (comedy.isNotEmpty()) rawSections.add(Triple("section_comedy", "Comedies & Feel-Good", filterFresh(comedy)))

            val newReleases = catalogMedia.filter { com.example.discovery.ReleasePolicy.isNew(it.releaseDate) }.sortedByDescending { it.releaseDate }
            if (newReleases.isNotEmpty()) rawSections.add(Triple("section_new_releases", "New Releases & Fresh Arrivals", filterFresh(newReleases)))

            val anime = catalogMedia.filter { it.matchesGenre("Anime") || it.matchesGenre("Animation") }
            if (anime.isNotEmpty()) rawSections.add(Triple("section_anime", "Anime & Animation Hits", filterFresh(anime)))

            val korean = catalogMedia.filter { it.matchesGenre("K-Dramas") }
            if (korean.isNotEmpty()) rawSections.add(Triple("section_korean", "Korean Dramas & Global Sensations", filterFresh(korean)))

            val award = catalogMedia.filter { it.matchPercentage > 85 }
            if (award.isNotEmpty()) rawSections.add(Triple("section_award_winning", "Award-Winning & Top Rated", filterFresh(award)))

            val doc = catalogMedia.filter { it.matchesGenre("Documentary") }
            if (doc.isNotEmpty()) rawSections.add(Triple("section_documentary", "Gripping Documentaries", filterFresh(doc)))

            if (tvShows.isNotEmpty()) rawSections.add(Triple("section_binge", "Binge-Worthy TV Series", filterFresh(tvShows)))
            if (movies.isNotEmpty()) rawSections.add(Triple("section_blockbusters", "Blockbuster Movies", filterFresh(movies)))
        }
        CategoryFilter.TV_SHOWS -> {
            val tvFiltered = tvShows

            val tvTop10 = tvFiltered.filter { it.top10Rank != null }
            if (tvTop10.isNotEmpty()) {
                rawSections.add(Triple("section_tv_top10", "Top 10 TV Shows Today", tvTop10))
                tvTop10.take(4).forEach { usedMediaIds.add("${it.type}:${it.id}") }
            }

            val tvTitle = if (selectedGenre != null) "Popular $selectedGenre TV Shows" else "Popular TV Shows"
            rawSections.add(Triple("section_tv_popular", tvTitle, filterFresh(tvFiltered)))

            val tvDrama = tvFiltered.filter { it.matchesGenre("Drama") }
            if (tvDrama.isNotEmpty()) rawSections.add(Triple("section_tv_drama", "Binge-Worthy TV Dramas", filterFresh(tvDrama)))

            val tvCrime = tvFiltered.filter { it.matchesGenre("Crime") }
            if (tvCrime.isNotEmpty()) rawSections.add(Triple("section_tv_crime", "Crime TV Shows & Thrillers", filterFresh(tvCrime)))

            val tvSciFi = tvFiltered.filter { it.matchesGenre("Sci-Fi") || it.matchesGenre("Fantasy") }
            if (tvSciFi.isNotEmpty()) rawSections.add(Triple("section_tv_scifi", "Sci-Fi & Supernatural Series", filterFresh(tvSciFi)))

            val tvComedy = tvFiltered.filter { it.matchesGenre("Comedy") }
            if (tvComedy.isNotEmpty()) rawSections.add(Triple("section_tv_comedy", "Sitcoms & Comedies", filterFresh(tvComedy)))

            val tvOriginals = tvFiltered.filter { it.isOriginal }
            if (tvOriginals.isNotEmpty()) rawSections.add(Triple("section_tv_originals", "NetflixPro Original Series", filterFresh(tvOriginals)))

            val tvNew = tvFiltered.filter { com.example.discovery.ReleasePolicy.isNew(it.releaseDate) }.sortedByDescending { it.releaseDate }
            if (tvNew.isNotEmpty()) rawSections.add(Triple("section_tv_new", "New TV Shows", filterFresh(tvNew)))
        }
        CategoryFilter.MOVIES -> {
            val movFiltered = movies

            val movTop10 = movFiltered.filter { it.top10Rank != null }
            if (movTop10.isNotEmpty()) {
                rawSections.add(Triple("section_movies_top10", "Top 10 Movies Today", movTop10))
                movTop10.take(4).forEach { usedMediaIds.add("${it.type}:${it.id}") }
            }

            val movTitle = if (selectedGenre != null) "Popular $selectedGenre Movies" else "Popular Movies"
            rawSections.add(Triple("section_movies_popular", movTitle, filterFresh(movFiltered)))

            val movAction = movFiltered.filter { it.matchesGenre("Action") }
            if (movAction.isNotEmpty()) rawSections.add(Triple("section_movies_action", "Blockbuster Action & Adventure", filterFresh(movAction)))

            val movCrime = movFiltered.filter { it.matchesGenre("Crime") }
            if (movCrime.isNotEmpty()) rawSections.add(Triple("section_movies_crime", "Crime Movies & Mystery Thrillers", filterFresh(movCrime)))

            val movSciFi = movFiltered.filter { it.matchesGenre("Sci-Fi") }
            if (movSciFi.isNotEmpty()) rawSections.add(Triple("section_movies_scifi", "Mind-Bending Sci-Fi & Dystopian", filterFresh(movSciFi)))

            val movDrama = movFiltered.filter { it.matchesGenre("Drama") }
            if (movDrama.isNotEmpty()) rawSections.add(Triple("section_movies_drama", "Critically Acclaimed Dramas", filterFresh(movDrama)))

            val movComedy = movFiltered.filter { it.matchesGenre("Comedy") }
            if (movComedy.isNotEmpty()) rawSections.add(Triple("section_movies_comedy", "Comedies", movComedy))

            val movOriginals = movFiltered.filter { it.isOriginal }
            if (movOriginals.isNotEmpty()) rawSections.add(Triple("section_movies_originals", "NetflixPro Original Movies", movOriginals))

            val movNew = movFiltered.filter { com.example.discovery.ReleasePolicy.isNew(it.releaseDate) }.sortedByDescending { it.releaseDate }
            if (movNew.isNotEmpty()) rawSections.add(Triple("section_movies_new", "New Releases", movNew))
        }
        CategoryFilter.CATEGORIES -> {
            val genreFilter = selectedGenre ?: "All"
            val filtered = catalogMedia

            val catTop10 = filtered.filter { it.top10Rank != null }.sortedBy { it.top10Rank }
            val catTrending = filtered.filter { it.isTrending }
            val catTvShows = filtered.filter { it.type == MediaType.TV_SHOW }
            val catMovies = filtered.filter { it.type == MediaType.MOVIE }
            val catOriginals = filtered.filter { it.isOriginal }
            val catNew = filtered.filter { com.example.discovery.ReleasePolicy.isNew(it.releaseDate) }.sortedByDescending { it.releaseDate }
            val catAcclaimed = filtered.filter { it.matchPercentage >= 85 }

            rawSections.add(Triple("section_category_highlights", "$genreFilter Highlights", filtered))

            if (catTop10.isNotEmpty()) {
                rawSections.add(Triple("section_cat_top10", "Top 10 in $genreFilter Today", catTop10))
            }
            if (catTrending.isNotEmpty()) {
                rawSections.add(Triple("section_cat_trending", "Trending in $genreFilter", catTrending))
            }
            if (catTvShows.isNotEmpty()) {
                rawSections.add(Triple("section_category_tv", "Binge-Worthy $genreFilter TV Shows", catTvShows))
            }
            if (catMovies.isNotEmpty()) {
                rawSections.add(Triple("section_category_movies", "$genreFilter Movies & Blockbusters", catMovies))
            }
            if (catOriginals.isNotEmpty()) {
                rawSections.add(Triple("section_category_originals", "Only on NetflixPro • $genreFilter", catOriginals))
            }
            if (catAcclaimed.isNotEmpty()) {
                rawSections.add(Triple("section_cat_acclaimed", "Critically Acclaimed $genreFilter", catAcclaimed))
            }
            if (catNew.isNotEmpty()) {
                rawSections.add(Triple("section_category_new", "New Releases in $genreFilter", catNew))
            }
            if (filtered.size < 6 && trending.isNotEmpty()) {
                rawSections.add(Triple("section_cat_popular_more", "Popular on NetflixPro", trending))
            }
        }
    }

    val sections = rawSections.filter { it.third.isNotEmpty() }.map { (key, title, items) ->
        val isTop10 = key.contains("top10")
        val isOriginals = key.contains("originals")
        MediaSection(
            id = key,
            title = title,
            items = if (isTop10) items.take(10) else items,
            isTop10 = isTop10,
            cardWidth = if (isOriginals) 210.dp else 115.dp,
            cardHeight = if (isOriginals) 330.dp else 165.dp
        )
    }
    return HomeCatalog(heroMedia, sections)
}
