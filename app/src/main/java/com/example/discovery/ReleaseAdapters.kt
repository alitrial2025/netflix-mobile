package com.example.discovery

import android.content.Context
import com.example.data.network.TmdbClient
import com.example.data.network.mapGenreIds
import com.example.data.model.MediaItem
import com.example.data.model.MediaType

fun createReleaseDiscovery(context: Context): LiveReleaseDiscovery {
    val cache = ReleaseDiskCache(java.io.File(context.filesDir, "release-discovery-v1.json"))
    return LiveReleaseDiscovery(fetch = { query, page ->
        val parameters = query.parameters + mapOf("api_key" to TmdbClient.apiKey, "page" to page.toString())
        val result = if(query.kind == "movie") TmdbClient.service.discoverReleaseMovies(parameters)
            else TmdbClient.service.discoverReleaseTv(parameters)
        result.results.map { item -> ReleaseTitle(item.id.toString(),query.kind,item.title ?: item.name ?: "",item.overview.orEmpty(),
            item.posterPath,item.backdropPath,item.releaseDate ?: item.firstAirDate,item.genreIds,
            item.voteAverage ?: 0.0,item.voteCount,item.popularity,query.majorOtt) }
    }, readCache = { cache.read() }, writeCache = { cache.write(it) })
}
fun ReleaseTitle.toDiscoveryMedia(upcoming: Boolean): MediaItem = MediaItem(
    id = id, title = title, type = if(kind == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
    description = overview, tagline = "", matchPercentage = (voteAverage * 10).toInt().coerceIn(0,100),
    maturityRating = "18+", releaseYear = date?.take(4)?.toIntOrNull() ?: 0,
    durationOrSeasons = if(kind == "tv") "Series" else "Feature", isOriginal = false,
    genres = mapGenreIds(genreIds), cast = emptyList(), director = "",
    backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
    posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
    isComingSoon = upcoming, releaseDateBadge = if (upcoming) date?.let(ReleasePolicy::badge) else null,
    releaseDate = date, genreIds = genreIds, voteAverage = voteAverage, voteCount = voteCount, popularity = popularity)
fun MediaItem.recommendationTitle(): RecommendationTitle = RecommendationTitle(
    "${if(type == MediaType.TV_SHOW) "tv" else "movie"}:$id",RecommendationEngine.genres(genreIds),popularity,voteAverage,voteCount,releaseDate)
