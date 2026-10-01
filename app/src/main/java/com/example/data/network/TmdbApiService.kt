package com.example.data.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApiService {
    @GET("discover/movie")
    suspend fun discoverReleaseMovies(@retrofit2.http.QueryMap parameters: Map<String, String>): TmdbResponse

    @GET("discover/tv")
    suspend fun discoverReleaseTv(@retrofit2.http.QueryMap parameters: Map<String, String>): TmdbResponse

    @GET("trending/movie/week")
    suspend fun getTrendingMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("trending/tv/week")
    suspend fun getTrendingTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("movie/top_rated")
    suspend fun getTopRatedMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("tv/top_rated")
    suspend fun getTopRatedTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("movie/upcoming")
    suspend fun getUpcomingMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("movie/popular")
    suspend fun getPopularMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("tv/popular")
    suspend fun getPopularTv(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("with_genres") withGenres: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key") apiKey: String,
        @Query("with_genres") withGenres: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("api_key") apiKey: String,
        @Query("query") query: String,
        @Query("page") page: Int = 1
    ): TmdbResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,similar,images"
    ): TmdbMovieDetails

    @GET("tv/{tv_id}")
    suspend fun getTvShowDetails(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") appendToResponse: String = "credits,similar,images"
    ): TmdbTvShowDetails

    @GET("movie/{movie_id}/images")
    suspend fun getMovieImages(
        @Path("movie_id") movieId: Int,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): TmdbImagesResponse

    @GET("tv/{tv_id}/images")
    suspend fun getTvShowImages(
        @Path("tv_id") tvId: Int,
        @Query("api_key") apiKey: String,
        @Query("include_image_language") includeImageLanguage: String = "en,null"
    ): TmdbImagesResponse

    @GET("tv/{tv_id}/season/{season_number}")
    suspend fun getTvSeasonDetails(
        @Path("tv_id") tvId: Int,
        @Path("season_number") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbSeasonResponse
}
