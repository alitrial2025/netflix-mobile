package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbResponse(
    val results: List<TmdbMediaResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbMediaResult(
    val id: Int,
    val title: String?,
    val name: String?,
    @Json(name = "original_title") val originalTitle: String?,
    @Json(name = "original_name") val originalName: String?,
    val overview: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "media_type") val mediaType: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    @Json(name = "genre_ids") val genreIds: List<Int> = emptyList(),
    @Json(name = "vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val overview: String?,
    val tagline: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "release_date") val releaseDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val genres: List<TmdbGenre> = emptyList(),
    val runtime: Int?,
    val credits: TmdbCredits?,
    val similar: TmdbResponse?,
    val images: TmdbImagesResponse? = null
)

@JsonClass(generateAdapter = true)
data class TmdbTvShowDetails(
    val id: Int,
    val name: String,
    val overview: String?,
    val tagline: String?,
    @Json(name = "poster_path") val posterPath: String?,
    @Json(name = "backdrop_path") val backdropPath: String?,
    @Json(name = "first_air_date") val firstAirDate: String?,
    @Json(name = "vote_average") val voteAverage: Double?,
    val genres: List<TmdbGenre> = emptyList(),
    @Json(name = "number_of_seasons") val numberOfSeasons: Int?,
    @Json(name = "number_of_episodes") val numberOfEpisodes: Int? = null,
    val credits: TmdbCredits?,
    val similar: TmdbResponse?,
    val images: TmdbImagesResponse? = null
)

@JsonClass(generateAdapter = true)
data class TmdbImagesResponse(
    val id: Int? = null,
    val logos: List<TmdbImage> = emptyList(),
    val backdrops: List<TmdbImage> = emptyList(),
    val posters: List<TmdbImage> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbImage(
    @Json(name = "file_path") val filePath: String?,
    @Json(name = "aspect_ratio") val aspectRatio: Double? = null,
    val width: Int? = null,
    val height: Int? = null,
    @Json(name = "iso_639_1") val language: String? = null
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    val id: Int,
    val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(
    val cast: List<TmdbCast> = emptyList(),
    val crew: List<TmdbCrew> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbCast(
    val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbCrew(
    val job: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonResponse(
    @Json(name = "episodes") val episodes: List<TmdbEpisode> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbEpisode(
    val id: Int,
    @Json(name = "episode_number") val episodeNumber: Int,
    val name: String,
    val overview: String?,
    val runtime: Int?,
    @Json(name = "still_path") val stillPath: String? = null
)

fun mapGenreIds(genreIds: List<Int>): List<String> {
    val set = mutableSetOf<String>()
    genreIds.forEach { id ->
        when (id) {
            28 -> { set.add("Action"); set.add("Action & Adventure"); set.add("Thrillers") }
            12 -> { set.add("Adventure"); set.add("Action & Adventure") }
            16 -> { set.add("Animation"); set.add("Anime"); set.add("Kids & Family") }
            35 -> { set.add("Comedy"); set.add("Comedies") }
            80 -> { set.add("Crime"); set.add("Crime Thrillers"); set.add("Crime TV Shows"); set.add("Thrillers") }
            99 -> { set.add("Documentary"); set.add("Documentaries") }
            18 -> { set.add("Drama"); set.add("Dramas") }
            10751 -> { set.add("Family"); set.add("Kids & Family"); set.add("Kids") }
            14 -> { set.add("Fantasy"); set.add("Sci-Fi & Fantasy") }
            36 -> { set.add("History"); set.add("Drama") }
            27 -> { set.add("Horror"); set.add("Horror Movies"); set.add("Thrillers") }
            10402 -> { set.add("Music"); set.add("Musicals") }
            9648 -> { set.add("Mystery"); set.add("Mystery Thrillers"); set.add("Crime Thrillers") }
            10749 -> { set.add("Romance"); set.add("Romantic") }
            878 -> { set.add("Sci-Fi"); set.add("Science Fiction"); set.add("Sci-Fi & Fantasy") }
            10770 -> { set.add("TV Movie"); set.add("Drama") }
            53 -> { set.add("Thriller"); set.add("Thrillers"); set.add("Suspense") }
            10752 -> { set.add("War"); set.add("Action") }
            37 -> { set.add("Western"); set.add("Action") }
            10759 -> { set.add("Action & Adventure"); set.add("Action"); set.add("Adventure") }
            10762 -> { set.add("Kids"); set.add("Kids & Family") }
            10764 -> { set.add("Reality TV"); set.add("Documentaries") }
            10765 -> { set.add("Sci-Fi & Fantasy"); set.add("Sci-Fi"); set.add("Fantasy") }
            10766 -> { set.add("Soap"); set.add("Drama"); set.add("Romance") }
            10767 -> { set.add("Talk Show"); set.add("Comedy") }
            10768 -> { set.add("War & Politics"); set.add("Drama") }
        }
    }
    return if (set.isEmpty()) listOf("Drama", "Popular") else set.toList()
}

