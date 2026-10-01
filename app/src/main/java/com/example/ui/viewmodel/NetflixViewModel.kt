package com.example.ui.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import com.example.MainActivity
import com.example.R
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.Color
import com.example.data.CatalogData
import com.example.data.FirebaseSyncManager
import com.example.data.CloudWatchHistoryItem
import com.example.data.TvSessionData
import com.example.data.network.TmdbClient
import com.example.data.network.TmdbMediaResult
import com.example.data.network.mapGenreIds
import com.example.data.local.AppDatabase
import com.example.data.local.DownloadEntity
import com.example.data.local.ReminderEntity
import com.example.data.local.WatchProgressEntity
import com.example.data.model.CastDevice
import com.example.data.model.Episode
import com.example.data.model.GameItem
import com.example.data.model.MediaItem
import com.example.data.model.MediaType
import com.example.data.model.NotificationIconType
import com.example.data.model.NotificationItem
import com.example.data.model.TrailerItem
import com.example.data.model.UserProfile
import com.example.data.model.ProfilePin
import com.example.data.repository.NetflixRepository
import com.example.data.NetMirrorResolver
import com.example.data.NetMirrorStream
import com.example.data.MobileSettingsStore
import com.example.data.MobileSetting
import com.example.data.CellularDataMode
import com.example.data.MobilePlaybackPolicy
import com.example.data.playbackNetwork
import com.example.data.observePlaybackNetwork
import com.example.data.NetworkDiagnostic
import com.example.data.TrailerResolver
import com.example.data.TrailerResolverCallback
import com.example.data.TrailerStream
import com.example.data.model.toEntity
import com.example.data.model.toUserProfile
import com.example.data.download.NetflixDownloadManager
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.DownloadTaskStatus
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

enum class NavigationTab {
    HOME, CLIPS, NEW_HOT, SEARCH, MY_NETFLIX
}

enum class CategoryFilter {
    ALL, TV_SHOWS, MOVIES, GAMES, CATEGORIES
}

data class PlayerState(
    val media: MediaItem? = null,
    val episode: Episode? = null,
    val isPlaying: Boolean = true,
    val currentPositionSec: Int = 0,
    val durationSec: Int = 120,
    val playbackSpeed: Float = 1.0f,
    val audioTrack: String = "English [Original]",
    val subtitleTrack: String = "Off",
    val showControls: Boolean = true,
    val isLocked: Boolean = false,
    val showAudioSubtitleDialog: Boolean = false,
    val showEpisodeDrawer: Boolean = false,
    val showSkipIntro: Boolean = false,
    val introWindow: IntroWindow? = null,
    val isResolving: Boolean = false,
    val resolvedUrl: String? = null,
    val resolveHeaders: Map<String, String> = emptyMap(),
    val resolveError: String? = null,
    val sourceId: String? = null,
    val nextEpisode: Episode? = null,
    val nextEpisodeMedia: MediaItem? = null,
    val nextEpisodeLoading: Boolean = false,
    val nextEpisodeError: String? = null,
    val nextEpisodeChecked: Boolean = false,
    val hasEnded: Boolean = false,
    val audioLanguage: String = "Original",
    val subtitleLanguage: String = "Off",
    val captions: List<com.example.data.Caption> = emptyList()
)

class NetflixViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NetflixRepository = NetflixRepository(AppDatabase.getInstance(application).netflixDao())
    private val netMirrorResolver = NetMirrorResolver(application)
    val syncManager: FirebaseSyncManager = FirebaseSyncManager(application)

    val lastSyncStatus: StateFlow<String> = syncManager.lastSyncStatus
    val pairedTvSessions: StateFlow<List<TvSessionData>> = syncManager.pairedTvSessions

    private val _isWarmupFinished = MutableStateFlow(false)
    val isWarmupFinished: StateFlow<Boolean> = _isWarmupFinished.asStateFlow()

    private val _hasCompletedSplash = MutableStateFlow(false)
    val hasCompletedSplash: StateFlow<Boolean> = _hasCompletedSplash.asStateFlow()

    fun completeSplash() {
        _hasCompletedSplash.value = true
    }

    private val _isLoadingCatalog = MutableStateFlow(true)
    val isLoadingCatalog: StateFlow<Boolean> = _isLoadingCatalog.asStateFlow()

    private fun startNetMirrorWarmup() {
        viewModelScope.launch {
            if (!hasCatalogNetwork()) { _isWarmupFinished.value = true; return@launch }
            android.util.Log.d("NetMirror", "🚀 starting NetMirror background session warmup...")
            val restored = netMirrorResolver.restoreSessionFromStorage()
            if (restored && netMirrorResolver.isSessionWarm()) {
                android.util.Log.d("NetMirror", "🚀 session restored successfully, warm-up skipped!")
                _isWarmupFinished.value = true
                return@launch
            }
            try {
                netMirrorResolver.warmNetMirrorSession()
                android.util.Log.d("NetMirror", "🚀 NetMirror background session warmup finished!")
            } catch (e: Exception) {
                android.util.Log.e("NetMirror", "🚀 NetMirror background session warmup failed with exception", e)
            } finally {
                _isWarmupFinished.value = true
            }
        }
    }

    private val catalogCache = com.example.data.CatalogDiskCache(java.io.File(application.filesDir, "catalog-cache.json"))

    private fun setupOfflineFallbacks() {
        // A failed refresh must not erase metadata needed by offline downloads and history.
        CatalogData.allMedia = _catalogMedia.value
    }

    private var catalogRefreshJob: Job? = null

    fun refreshCatalog() = fetchTmdbCatalog()

    private fun hasCatalogNetwork(): Boolean {
        val connectivity = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val network = connectivity?.activeNetwork ?: return false
        val caps = connectivity.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun fetchTmdbCatalog() {
        if (catalogRefreshJob?.isActive == true) return
        catalogRefreshJob = viewModelScope.launch(Dispatchers.Default) {
            _isLoadingCatalog.value = true
            try {
                val cached = withContext(Dispatchers.IO) { catalogCache.read() }
                if (_catalogMedia.value.isEmpty() && cached.isNotEmpty()) {
                    _catalogMedia.value = cached
                    CatalogData.allMedia = cached
                }
                if (!hasCatalogNetwork()) { setupOfflineFallbacks(); return@launch }
                val requestSlots = kotlinx.coroutines.sync.Semaphore(4)
                fun request(block: suspend () -> com.example.data.network.TmdbResponse) = async {
                    requestSlots.acquire()
                    try { requestCatalogSection(block) } finally { requestSlots.release() }
                }
                val apiKey = TmdbClient.apiKey
                if (apiKey.isEmpty() || apiKey == "PLACEHOLDER") {
                    setupOfflineFallbacks()
                    _isLoadingCatalog.value = false
                    return@launch
                }

                val trendingMoviesDeferred = request { TmdbClient.service.getTrendingMovies(apiKey) }
                val trendingTvDeferred = request { TmdbClient.service.getTrendingTv(apiKey) }
                val topRatedMoviesDeferred = request { TmdbClient.service.getTopRatedMovies(apiKey) }
                val topRatedTvDeferred = request { TmdbClient.service.getTopRatedTv(apiKey) }
                val upcomingMoviesDeferred = request { TmdbClient.service.getUpcomingMovies(apiKey) }
                val popularMoviesDeferred = request { TmdbClient.service.getPopularMovies(apiKey) }
                val popularTvDeferred = request { TmdbClient.service.getPopularTv(apiKey) }

                // Category & Genre Discovery (Crime, Action, Sci-Fi, Thriller, Animation, Comedy, Drama, Horror, Romance, Documentary)
                val crimeMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "80,53") }
                val crimeTvDeferred = request { TmdbClient.service.discoverTv(apiKey, withGenres = "80,9648") }
                val actionMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "28,12") }
                val sciFiMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "878") }
                val animationMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "16,10751") }
                val comedyMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "35") }
                val dramaMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "18") }
                val horrorMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "27") }
                val romanceMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "10749") }
                val docMoviesDeferred = request { TmdbClient.service.discoverMovies(apiKey, withGenres = "99") }
                val actionTvDeferred = request { TmdbClient.service.discoverTv(apiKey, withGenres = "10759") }
                val sciFiTvDeferred = request { TmdbClient.service.discoverTv(apiKey, withGenres = "10765") }

                val trendingMoviesTask = trendingMoviesDeferred.await()
                val trendingTvTask = trendingTvDeferred.await()
                val topRatedMoviesTask = topRatedMoviesDeferred.await()
                val topRatedTvTask = topRatedTvDeferred.await()
                val upcomingMoviesTask = upcomingMoviesDeferred.await()
                val popularMoviesTask = popularMoviesDeferred.await()
                val popularTvTask = popularTvDeferred.await()

                val crimeMovies = crimeMoviesDeferred.await()
                val crimeTv = crimeTvDeferred.await()
                val actionMovies = actionMoviesDeferred.await()
                val sciFiMovies = sciFiMoviesDeferred.await()
                val animationMovies = animationMoviesDeferred.await()
                val comedyMovies = comedyMoviesDeferred.await()
                val dramaMovies = dramaMoviesDeferred.await()
                val horrorMovies = horrorMoviesDeferred.await()
                val romanceMovies = romanceMoviesDeferred.await()
                val docMovies = docMoviesDeferred.await()
                val actionTv = actionTvDeferred.await()
                val sciFiTv = sciFiTvDeferred.await()

                val mappedItems = mutableListOf<MediaItem>()

                trendingMoviesTask?.results?.forEach { result ->
                    mappedItems.add(mapResultToMedia(result, MediaType.MOVIE, isTrending = true))
                }

                trendingTvTask?.results?.forEach { result ->
                    mappedItems.add(mapResultToMedia(result, MediaType.TV_SHOW, isTrending = true))
                }

                topRatedMoviesTask?.results?.take(10)?.forEachIndexed { index, result ->
                    mappedItems.add(mapResultToMedia(result, MediaType.MOVIE, topRank = index + 1))
                }

                topRatedTvTask?.results?.take(10)?.forEachIndexed { index, result ->
                    mappedItems.add(mapResultToMedia(result, MediaType.TV_SHOW, topRank = index + 1))
                }

                upcomingMoviesTask?.results?.forEach { result ->
                    mappedItems.add(mapResultToMedia(result, MediaType.MOVIE, isComingSoon = true))
                }

                popularMoviesTask?.results?.forEach { result ->
                    mappedItems.add(mapResultToMedia(result, MediaType.MOVIE))
                }

                popularTvTask?.results?.forEach { result ->
                    mappedItems.add(mapResultToMedia(result, MediaType.TV_SHOW))
                }

                // Add genre discovered media
                crimeMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                crimeTv?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.TV_SHOW)) }
                actionMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                sciFiMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                animationMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                comedyMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                dramaMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                horrorMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                romanceMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                docMovies?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.MOVIE)) }
                actionTv?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.TV_SHOW)) }
                sciFiTv?.results?.forEach { mappedItems.add(mapResultToMedia(it, MediaType.TV_SHOW)) }

                // Pure TMDB real catalog
                val deduplicated = mappedItems.distinctBy { it.id }
                if (deduplicated.isEmpty()) throw java.io.IOException("Catalog is unavailable")
                withContext(Dispatchers.IO) { catalogCache.write(deduplicated) }
                CatalogData.allMedia = deduplicated
                _catalogMedia.value = deduplicated

                // Fetch real TMDB logo titles in background for hero and trending items
                fetchLogosForItems(apiKey, deduplicated)

                // History and notifications come from actual user actions, not catalog fixtures.

            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                // If TMDB loading fails for network reasons, do offline fallbacks gracefully
                setupOfflineFallbacks()
            } finally {
                _isLoadingCatalog.value = false
            }
        }
    }

    private fun mapResultToMedia(
        result: TmdbMediaResult,
        mediaType: MediaType,
        isTrending: Boolean = false,
        isComingSoon: Boolean = false,
        topRank: Int? = null
    ): MediaItem {
        val idStr = result.id.toString()
        val title = result.title ?: result.name ?: result.originalTitle ?: result.originalName ?: "Untitled Movie"
        val year = try {
            val dateStr = result.releaseDate ?: result.firstAirDate ?: ""
            if (dateStr.length >= 4) dateStr.substring(0, 4).toInt() else 2025
        } catch (e: Exception) {
            2025
        }

        val poster = result.posterPath?.let { "https://image.tmdb.org/t/p/w342$it" }
        val backdrop = result.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
        val genreList = mapGenreIds(result.genreIds)
        val matchPct = ((result.voteAverage ?: 7.5) * 10).toInt().coerceIn(60, 100)

        val durationOrSeasons = if (mediaType == MediaType.MOVIE) "2h 05m" else "1 Season"
        val maturityRating = if (genreList.contains("Kids") || genreList.contains("Animation") || genreList.contains("Family")) "PG" else "16+"

        return MediaItem(
            id = idStr,
            title = title,
            type = mediaType,
            description = result.overview ?: "No synopsis available.",
            tagline = "",
            matchPercentage = matchPct,
            maturityRating = maturityRating,
            releaseYear = year,
            durationOrSeasons = durationOrSeasons,
            isOriginal = (result.voteAverage ?: 0.0) > 7.5,
            top10Rank = topRank,
            genres = genreList,
            cast = emptyList(),
            director = "Unknown Director",
            isTrending = isTrending,
            isComingSoon = isComingSoon,
            releaseDateBadge = if (isComingSoon) {
                val parts = (result.releaseDate ?: "").split("-")
                if (parts.size >= 3) {
                    val month = when (parts[1]) {
                        "01" -> "JAN"
                        "02" -> "FEB"
                        "03" -> "MAR"
                        "04" -> "APR"
                        "05" -> "MAY"
                        "06" -> "JUN"
                        "07" -> "JUL"
                        "08" -> "AUG"
                        "09" -> "SEP"
                        "10" -> "OCT"
                        "11" -> "NOV"
                        "12" -> "DEC"
                        else -> "SEP"
                    }
                    "$month ${parts[2]}"
                } else "SEP 15"
            } else null,
            posterUrl = poster,
            backdropUrl = backdrop,
            episodes = emptyList()
        )
    }

    // Dynamic Profile Local Persistence
    private fun getProfilePrefs(): SharedPreferences {
        return getApplication<Application>().getSharedPreferences("netflix_profiles_prefs", Context.MODE_PRIVATE)
    }

    private fun loadLocalProfiles(): List<UserProfile> {
        return try {
            if (getProfilePrefs().getString("local_account_uid", null) != syncManager.getUserId()) {
                return emptyList()
            }
            val jsonStr = getProfilePrefs().getString("profiles_json", null)
            if (!jsonStr.isNullOrBlank()) {
                val array = JSONArray(jsonStr)
                val list = mutableListOf<UserProfile>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(
                        UserProfile(
                            id = obj.optString("id", "p_$i"),
                            name = obj.optString("name", "User"),
                            avatarUrl = obj.optString("avatarUrl", null).takeIf { !it.isNullOrEmpty() },
                            avatarType = try {
                                com.example.data.model.AvatarType.valueOf(obj.optString("avatarType", "CUSTOM"))
                            } catch (e: Exception) { com.example.data.model.AvatarType.CUSTOM },
                            isKids = obj.optBoolean("isKids", false),
                            maxAge = obj.optInt("maxAge", 18),
                            pin = obj.optString("pin", null).takeIf { !it.isNullOrEmpty() },
                            language = obj.optString("language", "English"),
                            audioLanguage = obj.optString("audioLanguage", "Original"),
                            subtitleLanguage = obj.optString("subtitleLanguage", "Off"),
                            autoplayNext = obj.optBoolean("autoplayNext", true),
                            autoplayPreviews = obj.optBoolean("autoplayPreviews", true),
                            gameHandle = obj.optString("gameHandle", null).takeIf { !it.isNullOrEmpty() }
                        )
                    )
                }
                list
            } else emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun ensureLocalAccountOwnership() {
        val uid = syncManager.getUserId()
        val prefs = getProfilePrefs()
        val previousUid = prefs.getString("local_account_uid", null)
        if (previousUid == null) {
            // Preserve an older install's local rows only if Firebase has
            // restored a signed-in account. Otherwise their owner is unknown.
            if (uid.isNotBlank() && syncManager.isAuthenticated()) {
                prefs.edit().putString("local_account_uid", uid).apply()
            } else {
                repository.clearAccountData()
                prefs.edit().clear().putString("local_account_uid", "").apply()
            }
        } else if (previousUid != uid) {
            repository.clearAccountData()
            prefs.edit().clear().putString("local_account_uid", uid).apply()
            _profiles.value = emptyList()
            _activeProfile.value = UserProfile(id = "p_temp", name = "User")
            _showProfilePicker.value = false
            _userSubscription.value = com.example.data.model.UserSubscription()
            _watchHistory.value = emptyList()
        }
    }

    private var accountResetJob: Job? = null
    private val _watchHistory = MutableStateFlow<List<CloudWatchHistoryItem>>(emptyList())
    val watchHistory: StateFlow<List<CloudWatchHistoryItem>> = _watchHistory.asStateFlow()

    private fun saveProfilesLocally(list: List<UserProfile>) {
        try {
            val array = JSONArray()
            list.forEach { profile ->
                val obj = JSONObject().apply {
                    put("id", profile.id)
                    put("name", profile.name)
                    put("avatarUrl", profile.avatarUrl ?: "")
                    put("avatarType", profile.avatarType.name)
                    put("isKids", profile.isKids)
                    put("maxAge", profile.maxAge)
                    put("pin", profile.pin ?: "")
                    put("language", profile.language)
                    put("audioLanguage", profile.audioLanguage)
                    put("subtitleLanguage", profile.subtitleLanguage)
                    put("autoplayNext", profile.autoplayNext)
                    put("autoplayPreviews", profile.autoplayPreviews)
                    put("gameHandle", profile.gameHandle ?: "")
                }
                array.put(obj)
            }
            getProfilePrefs().edit().putString("profiles_json", array.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("NetflixViewModel", "Error saving profiles locally: ${e.message}")
        }
    }

    private fun createDefaultPrimaryProfile(email: String?): UserProfile {
        val displayName = if (!email.isNullOrBlank()) {
            email.substringBefore("@").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            "Main Profile"
        }
        return UserProfile(
            id = "p_primary",
            name = displayName,
            avatarType = com.example.data.model.AvatarType.CUSTOM,
            avatarUrl = com.example.data.model.AvatarUrls.urls[0]
        )
    }

    private val initialProfilesList: List<UserProfile> by lazy {
        val local = loadLocalProfiles()
        if (local.isNotEmpty()) local
        else if (syncManager.currentUserEmail.value != null) listOf(createDefaultPrimaryProfile(syncManager.currentUserEmail.value))
        else emptyList()
    }

    // Profile state
    private val _profiles = MutableStateFlow<List<UserProfile>>(initialProfilesList)
    val profiles: StateFlow<List<UserProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<UserProfile>(initialProfilesList.firstOrNull() ?: UserProfile(id = "p_temp", name = "User"))
    val activeProfile: StateFlow<UserProfile> = _activeProfile.asStateFlow()

    private val _showProfilePicker = MutableStateFlow(syncManager.currentUserEmail.value != null && initialProfilesList.isNotEmpty())
    val showProfilePicker: StateFlow<Boolean> = _showProfilePicker.asStateFlow()

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()
    private val _profileSaveError = MutableStateFlow<String?>(null)
    val profileSaveError: StateFlow<String?> = _profileSaveError.asStateFlow()
    private var profileWriteJob: Job? = null
    private val _editingProfile = MutableStateFlow<UserProfile?>(null)
    val editingProfile: StateFlow<UserProfile?> = _editingProfile.asStateFlow()

    private val _showEditProfileScreen = MutableStateFlow(false)
    val showEditProfileScreen: StateFlow<Boolean> = _showEditProfileScreen.asStateFlow()

    private val _showAvatarPicker = MutableStateFlow(false)
    val showAvatarPicker: StateFlow<Boolean> = _showAvatarPicker.asStateFlow()

    private val _transitioningProfile = MutableStateFlow<UserProfile?>(null)
    val transitioningProfile: StateFlow<UserProfile?> = _transitioningProfile.asStateFlow()

    private var subscriptionExpiryJob: Job? = null
    private val _userSubscription = MutableStateFlow<com.example.data.model.UserSubscription>(loadStoredSubscription())
    val userSubscription: StateFlow<com.example.data.model.UserSubscription> = _userSubscription.asStateFlow()

    private fun loadStoredSubscription(): com.example.data.model.UserSubscription {
        val isUserSignedIn = syncManager.currentUserEmail.value != null
        if (!isUserSignedIn) {
            return com.example.data.model.UserSubscription(
                status = "NONE",
                planId = "plan_guest",
                planName = "Guest",
                amount = 0,
                expiresAt = 0L
            )
        }
        val prefs = getProfilePrefs()
        if (prefs.getString("local_account_uid", null) != syncManager.getUserId()) {
            return com.example.data.model.UserSubscription()
        }
        val planId = prefs.getString("user_plan_id", "plan_guest") ?: "plan_guest"
        val planName = prefs.getString("user_plan_name", "Guest") ?: "Guest"
        val status = prefs.getString("user_plan_status", "NONE") ?: "NONE"
        return com.example.data.model.UserSubscription(
            status = status,
            planId = planId,
            planName = planName,
            amount = prefs.getInt("user_plan_amount", 0),
            currency = prefs.getString("user_plan_currency", "KES") ?: "KES",
            paymentReference = prefs.getString("user_plan_reference", "") ?: "",
            mpesaReceipt = prefs.getString("user_plan_receipt", "") ?: "",
            subscribedAt = prefs.getLong("user_plan_subscribed_at", 0L),
            expiresAt = prefs.getLong("user_plan_expires_at", 0L)
        )
    }

    private fun setUserSubscription(sub: com.example.data.model.UserSubscription) {
        subscriptionExpiryJob?.cancel()
        val effective = if (sub.status.equals("ACTIVE", true) && !sub.isActive) sub.copy(status = "EXPIRED") else sub
        _userSubscription.value = effective
        if (!sub.isClipsAllowed && _selectedTab.value == NavigationTab.CLIPS) {
            _selectedTab.value = NavigationTab.HOME
        }
        if (!sub.isGamesAllowed && _categoryFilter.value == CategoryFilter.GAMES) {
            _categoryFilter.value = CategoryFilter.ALL
        }
        if (!effective.isActive && _playerState.value.media != null && _playerState.value.sourceId != "Trailer") {
            closePlayer()
        }
        if (effective.isActive) {
            subscriptionExpiryJob = viewModelScope.launch {
                while (_userSubscription.value == effective && effective.isActive) {
                    delay((effective.expiresAt - System.currentTimeMillis()).coerceIn(1L, 60_000L))
                }
                if (_userSubscription.value == effective) {
                    val expired = effective.copy(status = "EXPIRED")
                    setUserSubscription(expired)
                    saveStoredSubscription(expired)
                }
            }
        }
    }

    fun updateSubscription(sub: com.example.data.model.UserSubscription) {
        val uid = syncManager.getUserId()
        if (!syncManager.isAuthenticated() || sub.mpesaReceipt.isBlank()) return
        viewModelScope.launch {
            try {
                val confirmed = syncManager.loadConfirmedSubscription(sub.mpesaReceipt, sub.paymentReference)
                if (syncManager.getUserId() != uid) return@launch
                if (confirmed != null) {
                    setUserSubscription(confirmed)
                    saveStoredSubscription(confirmed)
                } else {
                    showToast("Membership could not be confirmed. Retry your payment code; do not pay again.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                showToast("Payment was saved. Reconnect to refresh your membership; do not pay again.")
            }
        }
    }

    private fun saveStoredSubscription(sub: com.example.data.model.UserSubscription) {
        getProfilePrefs().edit()
            .putString("user_plan_id", sub.planId)
            .putString("user_plan_name", sub.planName)
            .putString("user_plan_status", sub.status)
            .putInt("user_plan_amount", sub.amount)
            .putString("user_plan_currency", sub.currency)
            .putString("user_plan_reference", sub.paymentReference)
            .putString("user_plan_receipt", sub.mpesaReceipt)
            .putLong("user_plan_subscribed_at", sub.subscribedAt)
            .putLong("user_plan_expires_at", sub.expiresAt)
            .apply()
    }

    fun isMovieLocked(media: MediaItem): Boolean {
        if (_userSubscription.value.isGuest) return true
        return _userSubscription.value.isMediaLocked(media.id, media.title)
    }

    private val _showSubscriptionSheet = MutableStateFlow(false)
    val showSubscriptionSheet: StateFlow<Boolean> = _showSubscriptionSheet.asStateFlow()

    fun openSubscriptionSheet(open: Boolean) {
        _showSubscriptionSheet.value = open
    }

    private val _showTrailerEndPrompt = MutableStateFlow(false)
    val showTrailerEndPrompt: StateFlow<Boolean> = _showTrailerEndPrompt.asStateFlow()

    fun dismissTrailerEndPrompt() {
        _showTrailerEndPrompt.value = false
    }

    /** Called by the player when trailer playback reaches the end */
    fun onTrailerPlaybackEnded() {
        closePlayer()
        if (!syncManager.isAuthenticated()) {
            _showTrailerEndPrompt.value = true
        } else {
            showToast("Upgrade your plan to stream the full movie.")
            _showSubscriptionSheet.value = true
        }
    }

    // Dynamic catalog StateFlow
    private val _catalogMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val catalogMedia: StateFlow<List<MediaItem>> = _catalogMedia.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val displayCatalogMedia: StateFlow<List<MediaItem>> = combine(_catalogMedia, _activeProfile) { catalog, profile ->
        if (profile.hasMaturityRestriction) {
            catalog.filter { it.isKidSafe(profile.contentMaxAge) }
        } else {
            catalog
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Navigation and Categories
    private val _selectedTab = MutableStateFlow(NavigationTab.HOME)
    val selectedTab: StateFlow<NavigationTab> = _selectedTab.asStateFlow()

    private val _categoryFilter = MutableStateFlow(CategoryFilter.ALL)
    val categoryFilter: StateFlow<CategoryFilter> = _categoryFilter.asStateFlow()

    private val _selectedCategoryGenre = MutableStateFlow<String?>(null)
    val selectedCategoryGenre: StateFlow<String?> = _selectedCategoryGenre.asStateFlow()

    // Media Detail Selection
    private val _selectedMedia = MutableStateFlow<MediaItem?>(null)
    val selectedMedia: StateFlow<MediaItem?> = _selectedMedia.asStateFlow()

    // Continue Watching Options Selection
    private val _continueWatchingOptions = MutableStateFlow<Pair<MediaItem, WatchProgressEntity>?>(null)
    val continueWatchingOptions: StateFlow<Pair<MediaItem, WatchProgressEntity>?> = _continueWatchingOptions.asStateFlow()

    // Player State
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var playerTickerJob: Job? = null

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedSearchGenre = MutableStateFlow<String?>(null)
    val selectedSearchGenre: StateFlow<String?> = _selectedSearchGenre.asStateFlow()

    // Downloads management via NetflixDownloadManager
    val downloadManager: NetflixDownloadManager = NetflixDownloadManager.getInstance(application)

    val downloadTasks: StateFlow<Map<String, DownloadTaskInfo>> = combine(downloadManager.downloadTasks, _activeProfile) { tasks, profile ->
        tasks.filterValues { it.profileId == profile.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val downloadingProgress: StateFlow<Map<String, Float>> = combine(downloadManager.downloadingProgress, downloadTasks) { progress, tasks ->
        progress.filterKeys { it in tasks }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val pausedDownloadKeys: StateFlow<Set<String>> = combine(downloadManager.pausedDownloadKeys, downloadTasks) { paused, tasks ->
        paused.filter { it in tasks }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Messages
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    // Flow states from Room (Scoped per active profile)
    @OptIn(ExperimentalCoroutinesApi::class)
    val watchlist: StateFlow<List<MediaItem>> = _activeProfile
        .flatMapLatest { profile ->
            combine(repository.getWatchlistEntries(profile.id), _catalogMedia) { entries, catalog ->
                val byId = catalog.associateBy { it.id }
                entries.mapNotNull { byId[it.mediaId] ?: CatalogData.getById(it.mediaId) }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<Pair<MediaItem, WatchProgressEntity>>> = _activeProfile
        .flatMapLatest { profile ->
            combine(repository.getAllProgress(profile.id), _catalogMedia) { progress, catalog ->
                val byId = catalog.associateBy { it.id }
                progress.groupBy { it.mediaId }.mapNotNull { (mediaId, entries) ->
                    val latest = entries.maxByOrNull { it.lastWatchedTimestamp } ?: return@mapNotNull null
                    if (latest.totalSeconds > 0 && latest.positionSeconds >= latest.totalSeconds * 0.95) {
                        return@mapNotNull null
                    }
                    val media = byId[mediaId] ?: CatalogData.getById(mediaId)
                    if (media == null) null else Pair(media, latest)
                }.sortedByDescending { it.second.lastWatchedTimestamp }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allWatchProgress: StateFlow<List<WatchProgressEntity>> = _activeProfile
        .flatMapLatest { profile -> repository.getAllProgress(profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val downloads: StateFlow<List<DownloadEntity>> = _activeProfile
        .flatMapLatest { profile -> repository.getDownloads(profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val reminders: StateFlow<List<ReminderEntity>> = repository.reminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val likedMedia: StateFlow<List<MediaItem>> = _activeProfile
        .flatMapLatest { profile -> repository.getLikedItems(profile.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 2026 "Trailers You've Watched" Hub
    private val _watchedTrailers = MutableStateFlow<List<TrailerItem>>(emptyList())
    val watchedTrailers: StateFlow<List<TrailerItem>> = _watchedTrailers.asStateFlow()

    // 2026 Notifications Hub
    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _showNotificationsSheet = MutableStateFlow(false)
    val showNotificationsSheet: StateFlow<Boolean> = _showNotificationsSheet.asStateFlow()

    // Settings Drawer / Sheet state
    private val _showSettingsDrawer = MutableStateFlow(false)
    val showSettingsDrawer: StateFlow<Boolean> = _showSettingsDrawer.asStateFlow()

    // Cast Dialog state
    private val _showCastDialog = MutableStateFlow(false)
    val showCastDialog: StateFlow<Boolean> = _showCastDialog.asStateFlow()

    private val _connectedCastDevice = MutableStateFlow<CastDevice?>(null)
    val connectedCastDevice: StateFlow<CastDevice?> = _connectedCastDevice.asStateFlow()

    val availableCastDevices = listOf(
        CastDevice("cast_1", "Living Room TV (4K UHD)", "Google TV • Living Room"),
        CastDevice("cast_2", "Bedroom OLED Screen", "Chromecast Ultra • Bedroom"),
        CastDevice("cast_3", "Family Den Shield TV", "Android TV • Downstairs")
    )

    private val prefs by lazy { application.getSharedPreferences("netflix_prefs", android.content.Context.MODE_PRIVATE) }

    // Preference settings
    private val _isSmartDownloadsEnabled = MutableStateFlow(prefs.getBoolean("pref_smart_downloads_enabled", true))
    val isSmartDownloadsEnabled: StateFlow<Boolean> = _isSmartDownloadsEnabled.asStateFlow()

    private val _allocatedStorageGb = MutableStateFlow(prefs.getFloat("pref_allocated_storage_gb", 3.0f))
    val allocatedStorageGb: StateFlow<Float> = _allocatedStorageGb.asStateFlow()
    private val _downloadsForYouEnabled = MutableStateFlow(prefs.getBoolean("pref_downloads_for_you", false))
    val downloadsForYouEnabled: StateFlow<Boolean> = _downloadsForYouEnabled.asStateFlow()
    private val _profileDownloadAllocations = MutableStateFlow<Map<String, Float>>(
        prefs.all.filterKeys { it.startsWith("profile_download_gb_") }.mapNotNull { (key, value) ->
            (value as? Float)?.let { key.removePrefix("profile_download_gb_") to it }
        }.toMap())
    val profileDownloadAllocations: StateFlow<Map<String, Float>> = _profileDownloadAllocations.asStateFlow()

    private val mobileSettings by lazy { MobileSettingsStore(prefs) }
    val playbackNetworkState = observePlaybackNetwork(application).stateIn(viewModelScope,
        SharingStarted.WhileSubscribed(5000), playbackNetwork(application))
    private val _isWifiOnlyEnabled = MutableStateFlow(mobileSettings.get(MobileSetting.WIFI_ONLY_DOWNLOADS))
    val isWifiOnlyEnabled: StateFlow<Boolean> = _isWifiOnlyEnabled.asStateFlow()

    private val _isHighQualityEnabled = MutableStateFlow(mobileSettings.get(MobileSetting.HIGH_QUALITY))
    val isHighQualityEnabled: StateFlow<Boolean> = _isHighQualityEnabled.asStateFlow()

    private val _isAutoPlayNextEnabled = MutableStateFlow(mobileSettings.get(MobileSetting.AUTOPLAY_NEXT))
    val isAutoPlayNextEnabled: StateFlow<Boolean> = _isAutoPlayNextEnabled.asStateFlow()

    private val _isAutoPlayPreviewsEnabled = MutableStateFlow(mobileSettings.get(MobileSetting.AUTOPLAY_PREVIEWS))
    val isAutoPlayPreviewsEnabled: StateFlow<Boolean> = _isAutoPlayPreviewsEnabled.asStateFlow()

    private val _isSpatialAudioEnabled = MutableStateFlow(mobileSettings.get(MobileSetting.SPATIAL_AUDIO))
    val isSpatialAudioEnabled: StateFlow<Boolean> = _isSpatialAudioEnabled.asStateFlow()

    private val _cellularDataOption = MutableStateFlow(mobileSettings.cellularDataMode.label)
    val cellularDataOption: StateFlow<String> = _cellularDataOption.asStateFlow()

    // Network diagnostic test state
    private val _diagnosticRunning = MutableStateFlow(false)
    val diagnosticRunning: StateFlow<Boolean> = _diagnosticRunning.asStateFlow()

    private val _diagnosticResult = MutableStateFlow<String?>(null)
    val diagnosticResult: StateFlow<String?> = _diagnosticResult.asStateFlow()

    // 2026 Dynamic Ambient Color for Bottom Nav
    private val _ambientColor = MutableStateFlow(Color.Black)
    val ambientColor: StateFlow<Color> = _ambientColor.asStateFlow()

    fun updateAmbientColor(color: Color) {
        _ambientColor.value = color
    }

    // 2026 Netflix Games Catalog state
    private val _games = MutableStateFlow<List<GameItem>>(CatalogData.gamesList)
    val games: StateFlow<List<GameItem>> = _games.asStateFlow()

    val displayGames: StateFlow<List<GameItem>> = combine(_games, _activeProfile) { list, profile ->
        if (profile.hasMaturityRestriction) {
            list.filter { it.isKidFriendly() }
        } else {
            list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogData.gamesList)

    // Filtered search results
    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var searchOwnerProfile: String? = null
    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()
    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private var startupCacheJob: Job? = null

    init {
        setUserSubscription(_userSubscription.value)
        fetchTmdbCatalog()
        startNetMirrorWarmup()

        // Load Room DB profiles on startup for immediate offline availability
        startupCacheJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                ensureLocalAccountOwnership()
                val roomProfiles = repository.getAllProfilesList()
                if (roomProfiles.isNotEmpty()) {
                    val mapped = roomProfiles.map { it.toUserProfile() }
                    withContext(Dispatchers.Main) {
                        _profiles.value = mapped
                        if (mapped.none { it.id == _activeProfile.value.id }) {
                            _activeProfile.value = mapped.first()
                        }
                        saveProfilesLocally(mapped)
                        connectCloudDataForActiveProfile()
                    }
                } else {
                    withContext(Dispatchers.Main) { connectCloudDataForActiveProfile() }
                }
            } catch (e: Exception) {
                android.util.Log.e("NetflixViewModel", "Error loading Room profiles on startup: ${e.message}")
            }
        }

        viewModelScope.launch {
            startupCacheJob?.join()
            if (!syncManager.isAuthenticated()) return@launch
            syncManager.listenToProfiles { remoteProfiles ->
                if (remoteProfiles.isNotEmpty()) {
                    _profiles.value = remoteProfiles
                    if (_profiles.value.none { it.id == _activeProfile.value.id }) {
                        _activeProfile.value = _profiles.value.first()
                    }
                    saveProfilesLocally(remoteProfiles)
                    connectCloudDataForActiveProfile()
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            repository.insertProfiles(remoteProfiles.map { it.toEntity() })
                        } catch (e: Exception) {
                            android.util.Log.e("NetflixViewModel", "Error caching Firestore profiles to Room: ${e.message}")
                        }
                    }
                }
            }
            syncManager.listenToSubscription { remoteSub ->
                setUserSubscription(remoteSub)
                saveStoredSubscription(remoteSub)
            }
        }

        viewModelScope.launch {
            combine(_catalogMedia, _activeProfile) { catalog, _ -> catalog }.collect {
                if (_searchQuery.value.isBlank() || searchOwnerProfile != _activeProfile.value.id) {
                    _searchResults.value = emptyList()
                    searchJob?.cancel()
                    performSearch()
                }
            }
        }

        viewModelScope.launch {
            downloadManager.toastEvents.collect { msg ->
                showToast(msg)
            }
        }
    }

    val currentUserEmail: StateFlow<String?> = syncManager.currentUserEmail

    private fun connectCloudDataForActiveProfile() {
        _watchHistory.value = emptyList()
        if (!syncManager.isAuthenticated()) return
        val uid = syncManager.getUserId()
        val profileId = _activeProfile.value.id
        if (uid.isBlank() || profileId == "p_guest" || profileId == "p_temp") return

        viewModelScope.launch(Dispatchers.IO) {
            val key = "my_list_migrated_${uid}_${profileId}"
            val prefs = getProfilePrefs()
            if (!prefs.getBoolean(key, false) && syncManager.getUserId() == uid) {
                val localEntries = repository.getWatchlistEntriesOnce(profileId)
                if (syncManager.seedMyListIfCloudEmpty(profileId, localEntries) &&
                    syncManager.getUserId() == uid) {
                    prefs.edit().putBoolean(key, true).apply()
                }
            }
        }

        syncManager.listenToMyList(profileId) { changes ->
            viewModelScope.launch(Dispatchers.IO) {
                if (syncManager.getUserId() != uid || _activeProfile.value.id != profileId) return@launch
                changes.forEach { change ->
                    repository.applyRemoteMyListChange(
                        profileId, change.mediaId, change.isAdded, change.addedAt
                    )
                }
            }
        }
        syncManager.listenToContinueWatching(profileId) { updates, removedIds ->
            viewModelScope.launch(Dispatchers.IO) {
                if (syncManager.getUserId() != uid || _activeProfile.value.id != profileId) return@launch
                removedIds.forEach { repository.removeProgress(profileId, it) }
                updates.forEach { repository.upsertRemoteProgressIfNewer(it) }
            }
        }
        syncManager.listenToWatchHistory(profileId) { items ->
            if (syncManager.getUserId() == uid && _activeProfile.value.id == profileId) {
                _watchHistory.value = items
            }
        }
    }

    /** Returns the Firebase UID of the signed-in user, or a stable fallback for guest users. */
    fun getCurrentUserId(): String = syncManager.getUserId()

    private val _showAuthScreen = MutableStateFlow(syncManager.currentUserEmail.value == null)
    val showAuthScreen: StateFlow<Boolean> = _showAuthScreen.asStateFlow()

    fun openAuthScreen(open: Boolean) {
        _showAuthScreen.value = open
        if (!open && syncManager.currentUserEmail.value == null) {
            setGuestMode()
        }
    }

    fun browseAsGuest() {
        _showAuthScreen.value = false
        setGuestMode()
    }

    fun setGuestMode() {
        syncManager.signOutUser()
        accountResetJob = viewModelScope.launch(Dispatchers.IO) {
            repository.clearAccountData()
        }
        getProfilePrefs().edit().clear().putString("local_account_uid", "").apply()
        _watchHistory.value = emptyList()
        val guestSub = com.example.data.model.UserSubscription(
            status = "NONE",
            planId = "plan_guest",
            planName = "Guest",
            amount = 0,
            expiresAt = 0L
        )
        setUserSubscription(guestSub)
        saveStoredSubscription(guestSub)
        val guestProfile = UserProfile(
            id = "p_guest",
            name = "Guest",
            avatarType = com.example.data.model.AvatarType.CUSTOM,
            avatarUrl = com.example.data.model.AvatarUrls.urls[0]
        )
        _activeProfile.value = guestProfile
        _profiles.value = listOf(guestProfile)
        saveProfilesLocally(_profiles.value)
    }

    fun signInUser(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            startupCacheJob?.join()
            accountResetJob?.join()
            syncManager.signInWithEmail(
                email = email,
                pass = pass,
                onSuccess = { userEmail ->
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) { ensureLocalAccountOwnership() }
                    showToast("Signed in as $userEmail")
                    syncManager.listenToSubscription { remoteSub ->
                        setUserSubscription(remoteSub)
                        saveStoredSubscription(remoteSub)
                    }
                    syncManager.listenToProfiles { remoteProfiles ->
                        if (remoteProfiles.isNotEmpty()) {
                            _profiles.value = remoteProfiles
                            if (_profiles.value.none { it.id == _activeProfile.value.id }) {
                                _activeProfile.value = _profiles.value.first()
                            }
                            saveProfilesLocally(remoteProfiles)
                            connectCloudDataForActiveProfile()
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    repository.insertProfiles(remoteProfiles.map { it.toEntity() })
                                } catch (e: Exception) {
                                    android.util.Log.e("NetflixViewModel", "Error saving profiles to Room: ${e.message}")
                                }
                            }
                        }
                    }
                    _showAuthScreen.value = false
                    _showProfilePicker.value = true
                    onSuccess()
                    }
                },
                onError = { err ->
                    showToast(err)
                    onError(err)
                }
            )
        }
    }

    fun signUpUser(
        email: String,
        pass: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            startupCacheJob?.join()
            accountResetJob?.join()
            syncManager.signUpWithEmail(
                email = email,
                pass = pass,
                onSuccess = { userEmail ->
                    viewModelScope.launch {
                        withContext(Dispatchers.IO) { ensureLocalAccountOwnership() }
                    showToast("Welcome to NetflixPro! Account created.")
                    syncManager.listenToSubscription { remoteSub ->
                        setUserSubscription(remoteSub)
                        saveStoredSubscription(remoteSub)
                    }
                    val primary = createDefaultPrimaryProfile(userEmail)
                    _profiles.value = listOf(primary)
                    _activeProfile.value = primary
                    saveProfilesLocally(_profiles.value)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            repository.insertProfiles(_profiles.value.map { it.toEntity() })
                        } catch (e: Exception) {
                            android.util.Log.e("NetflixViewModel", "Error saving profiles to Room: ${e.message}")
                        }
                    }
                    syncManager.syncProfilesToCloud(_profiles.value, primary.id)
                    connectCloudDataForActiveProfile()
                    syncManager.listenToProfiles { remoteProfiles ->
                        if (remoteProfiles.isNotEmpty()) {
                            _profiles.value = remoteProfiles
                            if (_profiles.value.none { it.id == _activeProfile.value.id }) {
                                _activeProfile.value = _profiles.value.first()
                            }
                            saveProfilesLocally(remoteProfiles)
                            connectCloudDataForActiveProfile()
                            viewModelScope.launch(Dispatchers.IO) {
                                try {
                                    repository.insertProfiles(remoteProfiles.map { it.toEntity() })
                                } catch (e: Exception) {
                                    android.util.Log.e("NetflixViewModel", "Error saving profiles to Room: ${e.message}")
                                }
                            }
                        }
                    }
                    _showAuthScreen.value = false
                    _showProfilePicker.value = true
                    onSuccess()
                    }
                },
                onError = { err ->
                    showToast(err)
                    onError(err)
                }
            )
        }
    }

    fun signOutUser() {
        pendingSmartReplacement = null
        profileWriteJob?.cancel()
        smartCuratorJob?.cancel()
        syncManager.signOutUser()
        _watchHistory.value = emptyList()
        accountResetJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                downloadManager.cancelAndJoinTransfers()
                profileWriteJob?.join()
                repository.clearAccountData()
            } catch (e: Exception) {
                android.util.Log.e("NetflixViewModel", "Account cache cleanup failed", e)
            }
        }
        getProfilePrefs().edit().clear().putString("local_account_uid", "").apply()
        getProfilePrefs().edit()
            .putString("user_plan_id", "plan_guest")
            .putString("user_plan_name", "Guest")
            .putString("user_plan_status", "NONE")
            .putLong("user_plan_expires_at", 0L)
            .apply()
        setUserSubscription(com.example.data.model.UserSubscription(
            status = "NONE",
            planId = "plan_guest",
            planName = "Guest",
            expiresAt = 0L
        ))
        _profiles.value = emptyList()
        _activeProfile.value = UserProfile(id = "p_guest", name = "Guest", avatarType = com.example.data.model.AvatarType.CUSTOM, avatarUrl = com.example.data.model.AvatarUrls.urls[0])
        _showAuthScreen.value = true
        _showProfilePicker.value = false
        showToast("Signed out")
    }

    fun pairTvWithCode(
        code: String,
        onSuccess: (TvSessionData) -> Unit,
        onError: (String) -> Unit
    ) {
        val message = "Sign in with your account email and password on the TV to connect it."
        showToast(message)
        onError(message)
    }

    /** Legacy entry point for old QR links; direct TV sign-in is required. */
    fun pairTvSession(
        rawCode: String,
        onSuccess: (TvSessionData) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        pairTvWithCode(rawCode, onSuccess, onError)
    }

    fun unpairTv(sessionCode: String) {
        viewModelScope.launch {
            syncManager.unpairTv(sessionCode)
            showToast("TV removed from this phone. Sign out on the TV to disconnect it.")
        }
    }

    fun sendTvRemoteCommand(sessionCode: String, command: String) {
        showToast("TV remote control is unavailable. Use the TV remote instead.")
    }

    fun selectTab(tab: NavigationTab) {
        if (tab == NavigationTab.CLIPS && !_userSubscription.value.isClipsAllowed) {
            if (_userSubscription.value.isGuest) {
                showToast("Sign in and upgrade to Premium to access Fast Laughs & Clips.")
                _showAuthScreen.value = true
            } else {
                showToast("Clips is exclusive to Premium plan. Upgrade to unlock.")
                _showSubscriptionSheet.value = true
            }
            return
        }
        _selectedTab.value = tab
    }

    fun setCategoryFilter(filter: CategoryFilter, genre: String? = null) {
        if (filter == CategoryFilter.GAMES && !_userSubscription.value.isGamesAllowed) {
            if (_userSubscription.value.isGuest) {
                showToast("Sign in and subscribe to Standard or Premium to play NetflixPro Games.")
                _showAuthScreen.value = true
            } else {
                showToast("Games are included with Standard and Premium plans. Upgrade to play.")
                _showSubscriptionSheet.value = true
            }
            return
        }
        _categoryFilter.value = filter
        _selectedCategoryGenre.value = genre
    }

    fun openProfilePicker(show: Boolean) {
        if (show && _userSubscription.value.isGuest) {
            showToast("Sign in to create and manage profiles.")
            _showAuthScreen.value = true
            return
        }
        _showProfilePicker.value = show
    }

    fun selectProfile(profile: UserProfile) {
        _activeProfile.value = profile
        _allocatedStorageGb.value = _profileDownloadAllocations.value[profile.id] ?: 0f
        _showProfilePicker.value = false
        _transitioningProfile.value = profile
        syncManager.syncProfilesToCloud(_profiles.value, profile.id)
        connectCloudDataForActiveProfile()
        showToast("Switched to ${profile.name}'s profile")
        viewModelScope.launch {
            performSearch()
        }
    }

    fun clearTransitioningProfile() {
        _transitioningProfile.value = null
    }

    fun openEditProfile(profile: UserProfile? = null) {
        _profileSaveError.value = null
        if (_userSubscription.value.isGuest) {
            showToast("Sign in to create or edit profiles.")
            _showAuthScreen.value = true
            return
        }
        if (profile == null) {
            if (_profiles.value.size >= _userSubscription.value.maxProfiles) {
                showToast("Profile limit reached (${_userSubscription.value.maxProfiles} profile(s) on ${_userSubscription.value.planName} Plan). Upgrade plan to add more.")
                _showSubscriptionSheet.value = true
                return
            }
            val newProfile = UserProfile(
                id = "p_${System.currentTimeMillis()}",
                name = "Profile ${_profiles.value.size + 1}",
                avatarType = com.example.data.model.AvatarType.CUSTOM,
                avatarUrl = com.example.data.model.AvatarUrls.urls.random()
            )
            _editingProfile.value = newProfile
        } else {
            _editingProfile.value = profile
        }
        _showEditProfileScreen.value = true
    }

    fun closeEditProfile() {
        _showEditProfileScreen.value = false
        _editingProfile.value = null
    }

    fun openAvatarPicker() {
        _showAvatarPicker.value = true
    }

    fun closeAvatarPicker() {
        _showAvatarPicker.value = false
    }

    fun selectAvatarUrl(url: String) {
        _editingProfile.update { current ->
            current?.copy(avatarUrl = url, avatarType = com.example.data.model.AvatarType.CUSTOM)
        }
        _showAvatarPicker.value = false
    }

    fun saveProfile(updatedProfile: UserProfile) {
        if (_isSavingProfile.value || !syncManager.isAuthenticated()) return
        val name = updatedProfile.name.trim().filterNot { it.isISOControl() }.take(25)
        if (name.isBlank()) { _profileSaveError.value = "Enter a profile name."; return }
        val handle = updatedProfile.gameHandle?.trim()?.takeIf { it.isNotBlank() }
        if (handle != null && !handle.matches(Regex("[a-zA-Z0-9_]{3,16}"))) {
            _profileSaveError.value = "Game handles need 3–16 letters, numbers or underscores."; return
        }
        val safeProfile = updatedProfile.copy(name = name, gameHandle = handle,
            maxAge = updatedProfile.maxAge.coerceIn(7, if (updatedProfile.isKids) 12 else 18),
            pin = ProfilePin.hash(updatedProfile.pin))
        val isNew = _profiles.value.none { it.id == safeProfile.id }
        if (isNew && _profiles.value.size >= _userSubscription.value.maxProfiles) {
            _profileSaveError.value = "Your plan's profile limit has been reached."; return
        }
        val owner = syncManager.getUserId()
        _isSavingProfile.value = true; _profileSaveError.value = null
        profileWriteJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.insertProfile(safeProfile.toEntity()) }
                ensureActive()
                if (owner != syncManager.getUserId()) return@launch
                _profiles.update { list -> if (isNew) list + safeProfile else list.map { if (it.id == safeProfile.id) safeProfile else it } }
                if (_activeProfile.value.id == safeProfile.id || _profiles.value.size == 1) _activeProfile.value = safeProfile
                saveProfilesLocally(_profiles.value)
                syncManager.syncProfilesToCloud(_profiles.value, _activeProfile.value.id)
                _showEditProfileScreen.value = false; _editingProfile.value = null
                showToast("Saved ${safeProfile.name}'s profile")
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { _profileSaveError.value = "Couldn't save your profile. Try again." }
            finally { _isSavingProfile.value = false }
        }
    }

    fun deleteProfile(profileId: String) {
        if (_profiles.value.size <= 1) {
            showToast("Cannot delete the only profile")
            return
        }
        val target = _profiles.value.find { it.id == profileId } ?: return
        downloadManager.clearAllDownloads(profileId)
        _profiles.update { list -> list.filter { it.id != profileId } }
        if (_activeProfile.value.id == profileId) {
            _activeProfile.value = _profiles.value.first()
        }
        _showEditProfileScreen.value = false
        _editingProfile.value = null
        saveProfilesLocally(_profiles.value)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteProfile(profileId)
            } catch (e: Exception) {
                android.util.Log.e("NetflixViewModel", "Error deleting profile from Room: ${e.message}")
            }
        }
        syncManager.deleteProfileFromCloud(profileId)
        syncManager.syncProfilesToCloud(_profiles.value, _activeProfile.value.id)
        showToast("Deleted ${target?.name ?: "profile"}")
    }

    private var detailJob: Job? = null
    private var seasonJob: Job? = null
    private var detailGeneration = 0L
    private var seasonGeneration = 0L
    private val seasonCache = object : java.util.LinkedHashMap<String, List<Episode>>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Episode>>?) = size > 12
    }
    private val _detailLoadState = MutableStateFlow(DetailLoadState())
    val detailLoadState = _detailLoadState.asStateFlow()

    fun openDetail(media: MediaItem) {
        if (!titleAllowed(media)) return
        detailJob?.cancel()
        seasonJob?.cancel()
        detailGeneration++
        seasonGeneration++
        val latest = allWatchProgress.value.filter { it.mediaId == media.id }.maxByOrNull { it.lastWatchedTimestamp }
        val season = latest?.takeIf { it.canResume() }?.season ?: 1
        _detailLoadState.value = DetailLoadState(loading = true, season = season)
        _selectedMedia.value = media
        fetchMediaDetails(media)
        if (media.type == MediaType.TV_SHOW) fetchTvSeasonEpisodes(media, season)
    }

    fun retryMediaDetails() { _selectedMedia.value?.let(::fetchMediaDetails) }


    fun openHistoryDetail(item: CloudWatchHistoryItem) {
        val candidates = (_catalogMedia.value + CatalogData.allMedia)
            .filter { it.id == item.mediaId }
        val expectedType = when {
            item.type.equals("Series", ignoreCase = true) ||
                item.type.equals("TV_SHOW", ignoreCase = true) -> MediaType.TV_SHOW
            item.type.equals("Movie", ignoreCase = true) -> MediaType.MOVIE
            else -> null
        }
        val media = candidates.firstOrNull { expectedType == null || it.type == expectedType }
            ?: candidates.firstOrNull()
        if (media != null) openDetail(media)
        else showToast("This title is no longer available in the catalog.")
    }

    private fun resolveTmdbId(id: String): Int? {
        return id.toIntOrNull() ?: when (id) {
            "squid_game", "squid_game_s3" -> 93405
            "stranger_things", "stranger_things_5" -> 66732
            "cyberpunk_edgerunners" -> 105248
            "wednesday_s2" -> 119051
            "umthetho" -> 1182392
            "top_show_4" -> 91363
            "top_show_5" -> 202250
            "top_movie_2" -> 280180
            "top_movie_3" -> 1022789
            "knives_out_3" -> 1051891
            "frankenstein_del_toro" -> 1001835
            else -> null
        }
    }

    private suspend fun loadSeasonEpisodes(media: MediaItem, season: Int): List<Episode> {
        val key = "${media.type}:${media.id}:$season"
        seasonCache[key]?.let { return it }
        val id = resolveTmdbId(media.id) ?: error("Title metadata is unavailable")
        val details = kotlinx.coroutines.withTimeoutOrNull(12_000L) {
            TmdbClient.service.getTvSeasonDetails(id, season, TmdbClient.apiKey)
        } ?: throw java.io.IOException("Season lookup timed out")
        val episodes = details.episodes.map { ep ->
            Episode("ep_${media.id}_S${season}_${ep.episodeNumber}", ep.episodeNumber,
                ep.name.ifEmpty { "Episode ${ep.episodeNumber}" }, ep.runtime ?: 45,
                ep.overview.orEmpty(), ep.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" })
        }.sortedBy { it.episodeNumber }
        if (episodes.isNotEmpty()) seasonCache[key] = episodes
        return episodes
    }

    fun fetchTvSeasonEpisodes(media: MediaItem, seasonNumber: Int) {
        if (_selectedMedia.value?.let { it.id == media.id && it.type == media.type } != true) return
        if (_detailLoadState.value.season == seasonNumber && _detailLoadState.value.seasonLoading) return
        seasonJob?.cancel()
        val generation = ++seasonGeneration
        _detailLoadState.update { it.copy(season = seasonNumber, seasonLoading = true, seasonError = null) }
        // Hide outgoing-season episodes immediately; a late response cannot replace the requested season.
        _selectedMedia.update { it?.copy(episodes = emptyList()) }
        seasonJob = viewModelScope.launch {
            try {
                val episodes = loadSeasonEpisodes(media, seasonNumber)
                ensureActive()
                if (generation != seasonGeneration) return@launch
                _selectedMedia.update { current ->
                    if (current?.id == media.id && current.type == media.type) current.copy(episodes = episodes) else current
                }
                if (episodes.isEmpty()) _detailLoadState.update { it.copy(seasonError = "No episodes are available for this season yet.") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == seasonGeneration) _detailLoadState.update { it.copy(seasonError = "Episodes couldn't load. Check your connection and retry.") }
            } finally {
                if (generation == seasonGeneration) _detailLoadState.update { it.copy(seasonLoading = false) }
            }
        }
    }

    private fun fetchMediaDetails(media: MediaItem) {
        detailJob?.cancel()
        val generation = ++detailGeneration
        _detailLoadState.update { it.copy(loading = true, error = null) }
        detailJob = viewModelScope.launch {
            try {
                val apiKey = TmdbClient.apiKey
                val mediaIdInt = resolveTmdbId(media.id) ?: return@launch

                if (media.type == MediaType.MOVIE) {
                    val details = TmdbClient.service.getMovieDetails(mediaIdInt, apiKey)

                    val castNames = details.credits?.cast?.take(6)?.map { it.name } ?: emptyList()
                    val directorName = details.credits?.crew?.find { it.job == "Director" }?.name
                        ?: details.credits?.crew?.firstOrNull()?.name
                        ?: "Unknown Director"
                    val durationStr = details.runtime?.let {
                        val hours = it / 60
                        val mins = it % 60
                        if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                    } ?: media.durationOrSeasons

                    val logoPath = details.images?.logos?.firstOrNull { it.language == "en" }?.filePath
                        ?: details.images?.logos?.firstOrNull()?.filePath
                    val logoUrl = logoPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: media.logoUrl
                    val posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: media.posterUrl
                    val backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: media.backdropUrl

                    val similarItems = details.similar?.results?.take(6)?.map { res ->
                        mapResultToMedia(res, MediaType.MOVIE)
                    } ?: emptyList()

                    _selectedMedia.update { current ->
                        if (generation == detailGeneration && current?.id == media.id && current.type == media.type) {
                            current.copy(
                                tagline = details.tagline?.ifBlank { null } ?: current.tagline,
                                cast = if (castNames.isNotEmpty()) castNames else current.cast,
                                director = directorName,
                                durationOrSeasons = durationStr,
                                description = details.overview?.ifBlank { null } ?: current.description,
                                logoUrl = logoUrl,
                                posterUrl = posterUrl,
                                backdropUrl = backdropUrl,
                                similarMedia = similarItems
                            )
                        } else current
                    }
                } else {
                    val details = TmdbClient.service.getTvShowDetails(mediaIdInt, apiKey)

                    val castNames = details.credits?.cast?.take(6)?.map { it.name } ?: emptyList()
                    val directorName = details.credits?.crew?.find { it.job == "Executive Producer" || it.job == "Director" }?.name
                        ?: details.credits?.crew?.firstOrNull()?.name
                        ?: "Unknown Creator"

                    val numSeasons = details.numberOfSeasons ?: 1
                    val numEpisodes = details.numberOfEpisodes ?: 0
                    val seasonsStr = "$numSeasons ${if (numSeasons == 1) "Season" else "Seasons"}" +
                        if (numEpisodes > 0) " ($numEpisodes Ep)" else ""

                    val logoPath = details.images?.logos?.firstOrNull { it.language == "en" }?.filePath
                        ?: details.images?.logos?.firstOrNull()?.filePath
                    val logoUrl = logoPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: media.logoUrl
                    val posterUrl = details.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: media.posterUrl
                    val backdropUrl = details.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: media.backdropUrl

                    val similarItems = details.similar?.results?.take(6)?.map { res ->
                        mapResultToMedia(res, MediaType.TV_SHOW)
                    } ?: emptyList()

                    _selectedMedia.update { current ->
                        if (generation == detailGeneration && current?.id == media.id && current.type == media.type) {
                            current.copy(
                                tagline = details.tagline?.ifBlank { null } ?: current.tagline,
                                cast = if (castNames.isNotEmpty()) castNames else current.cast,
                                director = directorName,
                                durationOrSeasons = seasonsStr,
                                totalSeasons = numSeasons,
                                totalEpisodes = numEpisodes,
                                description = details.overview?.ifBlank { null } ?: current.description,
                                logoUrl = logoUrl,
                                posterUrl = posterUrl,
                                backdropUrl = backdropUrl,
                                similarMedia = similarItems
                            )
                        } else current
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == detailGeneration) _detailLoadState.update { it.copy(error = "Some title information couldn't load. You can retry below.") }
            } finally {
                if (generation == detailGeneration) _detailLoadState.update { it.copy(loading = false) }
            }
        }
    }

    private fun fetchLogosForItems(apiKey: String, items: List<MediaItem>) {
        viewModelScope.launch {
            try {
                val targets = items.take(15).filter { it.id.toIntOrNull() != null }
                val updatedItems = _catalogMedia.value.toMutableList()
                var anyUpdated = false

                for (item in targets) {
                    val tmdbId = item.id.toIntOrNull() ?: continue
                    try {
                        val imagesResponse = if (item.type == MediaType.MOVIE) {
                            TmdbClient.service.getMovieImages(tmdbId, apiKey)
                        } else {
                            TmdbClient.service.getTvShowImages(tmdbId, apiKey)
                        }
                        val logoPath = imagesResponse.logos.firstOrNull { it.language == "en" }?.filePath
                            ?: imagesResponse.logos.firstOrNull()?.filePath
                        if (logoPath != null) {
                            val logoUrl = "https://image.tmdb.org/t/p/w500$logoPath"
                            val index = updatedItems.indexOfFirst { it.id == item.id }
                            if (index >= 0) {
                                updatedItems[index] = updatedItems[index].copy(logoUrl = logoUrl)
                                anyUpdated = true
                            }
                        }
                    } catch (_: Exception) {}
                }

                if (anyUpdated) {
                    CatalogData.allMedia = updatedItems
                    _catalogMedia.value = updatedItems
                    withContext(Dispatchers.IO) { catalogCache.write(updatedItems) }
                }
            } catch (_: Exception) {}
        }
    }

    fun closeDetail() {
        detailGeneration++
        seasonGeneration++
        detailJob?.cancel()
        seasonJob?.cancel()
        _selectedMedia.value = null
    }

    fun openContinueWatchingOptions(media: MediaItem, progress: WatchProgressEntity) {
        _continueWatchingOptions.value = Pair(media, progress)
    }

    fun closeContinueWatchingOptions() {
        _continueWatchingOptions.value = null
    }

    fun removeFromContinueWatching(mediaId: String) {
        viewModelScope.launch {
            val profileId = _activeProfile.value.id
            repository.removeProgress(profileId, mediaId)
            val synced = if (syncManager.isAuthenticated()) {
                syncManager.removeContinueWatchingFromCloud(profileId, mediaId)
            } else true
            closeContinueWatchingOptions()
            showToast(if (synced) "Removed from Continue Watching" else "Removed locally; cloud sync failed")
        }
    }

    fun isMediaInWatchlist(mediaId: String): Boolean {
        return watchlist.value.any { it.id == mediaId }
    }

    fun toggleWatchlist(media: MediaItem) {
        if (!syncManager.isAuthenticated()) {
            showToast("Sign in to add titles to My List.")
            _showAuthScreen.value = true
            return
        }
        viewModelScope.launch {
            val profileId = _activeProfile.value.id
            val inList = repository.isInWatchlistOnce(profileId, media.id)
            repository.toggleWatchlist(profileId, media.id, inList)
            syncManager.syncMyListItem(profileId, media.id, !inList, System.currentTimeMillis())
            showToast(if (inList) "Removed from My List" else "Added to My List")
        }
    }

    fun toggleReminder(media: MediaItem) {
        if (_userSubscription.value.isGuest) {
            showToast("Sign in to set reminders for upcoming releases.")
            _showAuthScreen.value = true
            return
        }
        viewModelScope.launch {
            val hasReminder = reminders.value.any { it.mediaId == media.id }
            repository.toggleReminder(media.id, hasReminder)
            showToast(if (hasReminder) "Reminder removed" else "We'll remind you when ${media.title} is released!")
        }
    }

    fun setRating(mediaId: String, rating: String) {
        if (_userSubscription.value.isGuest) {
            showToast("Sign in to rate titles and get personalized recommendations.")
            _showAuthScreen.value = true
            return
        }
        viewModelScope.launch {
            repository.setRating(_activeProfile.value.id, mediaId, rating)
            showToast(
                when (rating) {
                    "DOUBLE_LIKE" -> "Loved it! We'll recommend more like this."
                    "LIKE" -> "Liked! Feedback saved."
                    else -> "Not for you. We'll adjust your suggestions."
                }
            )
        }
    }

    // Playback Controls
    private var resolveJob: Job? = null
    private var playbackRequestGeneration = 0L
    private val progressTracker = PlaybackProgressTracker()
    private var pendingSmartReplacement: (() -> Unit)? = null

    private fun titleAllowed(media: MediaItem): Boolean {
        val profile = _activeProfile.value
        if (profile.hasMaturityRestriction && !media.isKidSafe(profile.contentMaxAge)) {
            showToast("This title is not available for this profile.")
            return false
        }
        return true
    }

    private fun finishSmartReplacement() {
        val replacement = pendingSmartReplacement
        pendingSmartReplacement = null
        replacement?.invoke()
    }

    fun playMedia(media: MediaItem, episode: Episode? = null, offlineOnly: Boolean = false) {
        if (!titleAllowed(media)) return
        smartCuratorJob?.cancel() // A background availability probe must yield to an explicit Play tap.
        finishSmartReplacement()
        persistPlayerProgress(_playerState.value)
        progressTracker.reset()
        val requestGeneration = ++playbackRequestGeneration
        if (isMovieLocked(media)) {
            if (_userSubscription.value.isGuest) {
                showToast("Sign in & subscribe to stream full movies. Playing trailer preview...")
            } else {
                showToast("Playing Trailer Preview. Upgrade plan to stream full movie.")
                _showSubscriptionSheet.value = true
            }
            playTrailer(media, "Trailer: ${media.title}")
            return
        }

        nextEpisodeJob?.cancel()
        val latestProgress = allWatchProgress.value.filter { it.mediaId == media.id }.maxByOrNull { it.lastWatchedTimestamp }
        val targetEpisode = initialPlaybackEpisode(media, episode, latestProgress)
        val totalSec = targetEpisode?.durationMinutes?.times(60) ?: 7200

        _playerState.value = PlayerState(
            media = media,
            episode = targetEpisode,
            nextEpisode = targetEpisode?.let { nextLoadedEpisode(it, media.episodes) },
            nextEpisodeChecked = targetEpisode?.let { nextLoadedEpisode(it, media.episodes) != null } == true,
            audioLanguage = _activeProfile.value.audioLanguage,
            subtitleLanguage = _activeProfile.value.subtitleLanguage,
            audioTrack = _activeProfile.value.audioLanguage,
            subtitleTrack = _activeProfile.value.subtitleLanguage,
            nextEpisodeMedia = media,
            isPlaying = false,
            isResolving = true,
            currentPositionSec = 0,
            durationSec = totalSec,
            playbackSpeed = 1.0f,
            showControls = true,
            isLocked = false,
            showSkipIntro = false
        )

        val devId = "phone_${syncManager.getUserId()}"
        val maxScreens = when (_userSubscription.value.planId) {
            "plan_premium" -> 4
            "plan_standard" -> 2
            else -> 1
        }
        syncManager.startActiveStreamHeartbeat(
            deviceId = devId,
            deviceName = "Android Phone",
            mediaTitle = media.title,
            maxAllowedScreens = maxScreens,
            onLimitExceeded = { active, max ->
                viewModelScope.launch(Dispatchers.Main) {
                    if (_playerState.value.sourceId != "Trailer") closePlayer()
                    showToast("Screen limit reached ($active/$max active streams). Upgrade your plan to watch simultaneously.")
                }
            }
        )

        val downloadKey = if (episode != null) "${media.id}_${episode.id}" else if (media.type == MediaType.TV_SHOW && targetEpisode != null) "${media.id}_${targetEpisode.id}" else media.id
        val existingDownload = downloads.value.find { it.downloadKey == downloadKey && it.isComplete }

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            val progressEntity = repository.getProgress(_activeProfile.value.id, media.id).firstOrNull()
            if (requestGeneration != playbackRequestGeneration) return@launch
            if (progressEntity != null) {
                if (targetEpisode == null || episodeCoordinates(targetEpisode.id) ==
                    episodeCoordinates(progressEntity.episodeId, progressEntity.season, progressEntity.episode)) {
                    if (progressEntity.canResume() && progressEntity.positionSeconds > 10) {
                        _playerState.update { it.copy(currentPositionSec = progressEntity.positionSeconds) }
                    }
                }
            }

            // Real Offline Download Playback Check
            if (existingDownload != null) {
                val offlineCaptions = deserializeCaptions(existingDownload.captionsJson)
                if (!existingDownload.localFilePath.isNullOrEmpty()) {
                    val file = java.io.File(existingDownload.localFilePath)
                    if (file.exists() && file.length() > 0) {
                        showToast("Playing downloaded media offline...")
                        _playerState.update {
                            it.copy(
                                isResolving = false,
                                resolvedUrl = file.absolutePath,
                                resolveHeaders = emptyMap(),
                                sourceId = "Offline Download (${file.length() / (1024 * 1024)} MB)",
                                captions = offlineCaptions,
                                isPlaying = true
                            )
                        }
                        return@launch
                    }
                }

            }

            if (offlineOnly) {
                _playerState.update { it.copy(isResolving = false, isPlaying = false,
                    resolveError = "This download is missing from your device. Delete it in Downloads and download it again.") }
                return@launch
            }
            if (!MobilePlaybackPolicy.permitsStreaming(mobileSettings.cellularDataMode,
                    playbackNetwork(getApplication()).wifiOrEthernet)) {
                _playerState.update { it.copy(isResolving = false, isPlaying = false,
                    resolveError = "Connect to Wi-Fi or change Cellular Data in App Settings to play this title.") }
                return@launch
            }
            try {
                val tmdbId = media.id
                val type = if (media.type == MediaType.MOVIE) "movie" else "tv"
                val season = if (targetEpisode != null) {
                    val idParts = targetEpisode.id.split("_S")
                    if (idParts.size > 1) {
                        idParts[1].split("_").firstOrNull()?.toIntOrNull() ?: 1
                    } else 1
                } else if (media.type == MediaType.TV_SHOW) 1 else 0
                val epNum = if (targetEpisode != null) targetEpisode.episodeNumber else if (media.type == MediaType.TV_SHOW) 1 else 0

                showToast("Loading video...")
                // A rapid tap sequence settles before contacting the playback provider.
                kotlinx.coroutines.delay(250L)
                val streamResult = kotlinx.coroutines.withTimeoutOrNull(58_000L) {
                    netMirrorResolver.resolveNet52(tmdbId, type, season, epNum)
                } ?: throw java.io.IOException("Playback took too long. Please try again.")
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                if (requestGeneration != playbackRequestGeneration) return@launch

                _playerState.update {
                    it.copy(
                        isResolving = false,
                        resolvedUrl = streamResult.url,
                        resolveHeaders = streamResult.headers,
                        sourceId = streamResult.sourceId,
                        captions = streamResult.captions,
                        isPlaying = true
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                if (requestGeneration != playbackRequestGeneration) return@launch
                _playerState.update {
                    it.copy(
                        isResolving = false,
                        resolveError = if (e is com.example.data.PlaybackRateLimitedException) {
                            val seconds = ((e.retryAfterMs ?: 60_000L) + 999L) / 1_000L
                            "Playback is busy. Please wait $seconds seconds before trying again."
                        } else "This title cannot be played. Try again later.",
                        resolvedUrl = null,
                        isPlaying = false
                    )
                }
            }
        }
    }

    fun evictFailedPlayback() {
        val state = _playerState.value
        val media = state.media ?: return
        if (state.sourceId?.startsWith("Offline") == true || state.sourceId == "Trailer") return
        val season = state.episode?.id?.substringAfter("_S", "")?.substringBefore('_')?.toIntOrNull()
            ?: if (media.type == MediaType.TV_SHOW) 1 else 0
        netMirrorResolver.evictCachedStream(media.id, if (media.type == MediaType.MOVIE) "movie" else "tv",
            season, state.episode?.episodeNumber ?: if (media.type == MediaType.TV_SHOW) 1 else 0)
    }

    fun updatePlayerProgress(currentSec: Int, totalDurationSec: Int) {
        val currentState = _playerState.value
        val showSkip = currentState.sourceId != "Trailer" && currentState.media?.type == MediaType.TV_SHOW &&
            currentState.introWindow?.let { currentSec in it.startSec until it.endSec } == true
        val dur = if (totalDurationSec > 0) totalDurationSec else _playerState.value.durationSec
        if (currentState.media?.type == MediaType.TV_SHOW && !isMovieLocked(currentState.media) &&
            currentState.sourceId != "Trailer" && dur > 60 && currentSec >= dur - 120) prepareNextEpisode()
        _playerState.update { it.copy(currentPositionSec = currentSec, durationSec = dur, showSkipIntro = showSkip) }

        val media = _playerState.value.media ?: return
        val ep = _playerState.value.episode
        val coordinates = episodeCoordinates(ep?.id)
        val isTrailer = _playerState.value.sourceId == "Trailer"
        val ownerUid = syncManager.getUserId()
        val ownerProfile = _activeProfile.value.id

        if (!isTrailer && syncManager.isAuthenticated() && progressTracker.shouldSave(currentSec)) {
            viewModelScope.launch {
                if (syncManager.getUserId() != ownerUid || _activeProfile.value.id != ownerProfile) return@launch
                repository.saveProgress(
                    ownerProfile,
                    media.id,
                    currentSec,
                    dur,
                    ep?.id,
                    ep?.title,
                    season = coordinates.first,
                    episode = coordinates.second
                )
                val progressEntity = WatchProgressEntity(
                    profileId = ownerProfile,
                    mediaId = media.id,
                    positionSeconds = currentSec,
                    totalSeconds = dur,
                    episodeId = ep?.id ?: "",
                    episodeTitle = ep?.title,
                    season = coordinates.first,
                    episode = coordinates.second,
                    lastWatchedTimestamp = System.currentTimeMillis()
                )
                if (syncManager.getUserId() != ownerUid) return@launch
                syncManager.syncWatchProgressToCloud(progressEntity, media)
            }
        }

        maybeStartSmartDownload(_playerState.value)
    }

    private fun maybeStartSmartDownload(state: PlayerState) {
        val media = state.media ?: return
        val episode = state.episode
        if (state.sourceId == "Trailer" || !_userSubscription.value.isActive || _userSubscription.value.maxDownloads <= 0 ||
            !smartDownloadCompletionReady(state)) return
        if (media.type == MediaType.MOVIE) {
            if (_downloadsForYouEnabled.value && progressTracker.claimSmartDownload()) runSmartDownloadCurator()
            return
        }
        if (episode == null || !state.nextEpisodeChecked || state.nextEpisodeLoading || state.nextEpisodeError != null) return
        if (state.nextEpisode == null) {
            if (_downloadsForYouEnabled.value && progressTracker.claimSmartDownload()) runSmartDownloadCurator()
            return
        }
        if (!_isSmartDownloadsEnabled.value || !progressTracker.claimSmartDownload()) return
        // Cross-season metadata is ready before replacing the watched download.
        val nextMedia = (state.nextEpisodeMedia ?: media).copy(episodes = listOf(episode) + listOfNotNull(state.nextEpisode))
        val ownerUid = syncManager.getUserId()
        val ownerProfile = _activeProfile.value.id
        val replacement = {
            if (syncManager.getUserId() == ownerUid && _activeProfile.value.id == ownerProfile) {
                downloadManager.handleEpisodeWatched(ownerProfile, nextMedia, episode, true,
                    true, _isHighQualityEnabled.value, _allocatedStorageGb.value,
                    emptyList(), _userSubscription.value.maxDownloads)
            }
        }
        // Keep the current local file available for Watch again until the viewer leaves it.
        if (state.sourceId?.startsWith("Offline Download") == true) pendingSmartReplacement = replacement
        else replacement()
    }

    fun togglePlayPause() {
        val nextPlaying = !_playerState.value.isPlaying
        _playerState.update { it.copy(isPlaying = nextPlaying) }
    }

    fun seekTo(seconds: Int) {
        val state = _playerState.value
        val clamped = seconds.coerceIn(0, state.durationSec)
        val showSkip = state.sourceId != "Trailer" && state.media?.type == MediaType.TV_SHOW &&
            state.introWindow?.let { clamped in it.startSec until it.endSec } == true
        _playerState.update { it.copy(currentPositionSec = clamped, showSkipIntro = showSkip, hasEnded = false) }
    }

    fun skipForward10() {
        val state = _playerState.value
        seekTo(state.currentPositionSec + 10)
    }

    fun skipBackward10() {
        val state = _playerState.value
        seekTo(state.currentPositionSec - 10)
    }

    fun skipIntro() {
        val intro = _playerState.value.introWindow ?: return
        seekTo(intro.endSec)
        _playerState.update { it.copy(showSkipIntro = false) }
        showToast("Skipped Intro")
    }

    fun updateIntroWindow(window: IntroWindow?) {
        _playerState.update { it.copy(introWindow = window) }
    }

    fun setPlaybackSpeed(speed: Float) {
        _playerState.update { it.copy(playbackSpeed = speed) }
        showToast("Speed set to ${speed}x")
    }

    fun setAudioTrack(track: String) {
        _playerState.update { it.copy(audioTrack = track, audioLanguage = track, showAudioSubtitleDialog = false) }
        showToast("Audio: $track")
    }

    fun setSubtitleTrack(track: String) {
        _playerState.update { it.copy(subtitleTrack = track, subtitleLanguage = track, showAudioSubtitleDialog = false) }
        showToast("Subtitles: $track")
    }

    fun toggleLock() {
        val isLocked = !_playerState.value.isLocked
        _playerState.update { it.copy(isLocked = isLocked, showControls = true) }
        showToast(if (isLocked) "Screen locked" else "Screen unlocked")
    }

    fun toggleControlsVisibility() {
        _playerState.update { it.copy(showControls = !it.showControls) }
    }

    fun showAudioSubtitleDialog(show: Boolean) {
        _playerState.update { it.copy(showAudioSubtitleDialog = show) }
    }

    fun showEpisodeDrawer(show: Boolean) {
        _playerState.update { it.copy(showEpisodeDrawer = show) }
    }

    private var nextEpisodeJob: Job? = null

    /** Metadata warming happens near the end, never on Home or before stream resolution. */
    fun prepareNextEpisode(retry: Boolean = false) {
        val state = _playerState.value
        val media = state.media ?: return
        val episode = state.episode ?: return
        if (state.sourceId == "Trailer" || state.isResolving || state.nextEpisode != null || nextEpisodeJob?.isActive == true) return
        if (!retry && (state.nextEpisodeError != null || state.nextEpisodeChecked)) return
        val generation = playbackRequestGeneration
        _playerState.update { it.copy(nextEpisodeLoading = true, nextEpisodeError = null) }
        nextEpisodeJob = viewModelScope.launch {
            try {
                val season = episodeCoordinates(episode.id).first
                val loaded = media.episodes.takeIf { list -> list.any { it.id == episode.id } } ?: loadSeasonEpisodes(media, season)
                var targetMedia = media.copy(episodes = loaded)
                var next = nextLoadedEpisode(episode, loaded)
                if (next == null) {
                    if (loaded.any { episodeCoordinates(it.id).first == season && it.episodeNumber > episode.episodeNumber }) {
                        throw java.io.IOException("Episode metadata is incomplete")
                    }
                    val id = resolveTmdbId(media.id) ?: error("Title metadata is unavailable")
                    val details = kotlinx.coroutines.withTimeoutOrNull(12_000L) { TmdbClient.service.getTvShowDetails(id, TmdbClient.apiKey) } ?: throw java.io.IOException("Series lookup timed out")
                    val seasons = details.numberOfSeasons ?: media.totalSeasons
                    targetMedia = targetMedia.copy(totalSeasons = seasons)
                    if (season < seasons) {
                        val nextSeason = loadSeasonEpisodes(media, season + 1)
                        next = nextSeason.firstOrNull()
                        targetMedia = targetMedia.copy(episodes = nextSeason)
                    }
                }
                ensureActive()
                if (generation != playbackRequestGeneration) return@launch
                _playerState.update { it.copy(nextEpisode = next, nextEpisodeMedia = targetMedia.takeIf { next != null }, nextEpisodeLoading = false, nextEpisodeChecked = true) }
                maybeStartSmartDownload(_playerState.value)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (generation == playbackRequestGeneration) _playerState.update { it.copy(nextEpisodeLoading = false, nextEpisodeError = "Couldn't check the next episode. Retry or choose one from Episodes.") }
            }
        }
    }

    fun onContentPlaybackEnded() {
        val state = _playerState.value
        if (state.sourceId == "Trailer") return
        _playerState.update { it.copy(isPlaying = false, hasEnded = true, showControls = false) }
        persistPlayerProgress(_playerState.value)
        if (state.media?.type == MediaType.TV_SHOW) prepareNextEpisode()
        maybeStartSmartDownload(_playerState.value)
    }

    fun replayCurrent() {
        pendingSmartReplacement = null
        progressTracker.reset()
        _playerState.update { it.copy(currentPositionSec = 0, hasEnded = false, isPlaying = true, showControls = true) }
    }

    fun playNextEpisode() {
        val state = _playerState.value
        val next = state.nextEpisode
        val media = state.nextEpisodeMedia ?: state.media ?: return
        if (next != null) {
            val speed = state.playbackSpeed
            playMedia(media, next)
            _playerState.update { it.copy(playbackSpeed = speed, audioTrack = state.audioTrack, subtitleTrack = state.subtitleTrack, audioLanguage = state.audioLanguage, subtitleLanguage = state.subtitleLanguage) }
        } else prepareNextEpisode(retry = true)
    }

    fun closePlayer() {
        finishSmartReplacement()
        nextEpisodeJob?.cancel()
        playbackRequestGeneration++
        resolveJob?.cancel()
        val current = _playerState.value
        persistPlayerProgress(current)
        val devId = "phone_${syncManager.getUserId()}"
        syncManager.stopActiveStreamHeartbeat(devId)
        _playerState.value = PlayerState(media = null)
    }

    private fun persistPlayerProgress(current: PlayerState) {
        val coordinates = episodeCoordinates(current.episode?.id)
        val ownerUid = syncManager.getUserId()
        val ownerProfile = _activeProfile.value.id
        if (current.media != null && current.sourceId != "Trailer" && current.currentPositionSec > 0 && syncManager.isAuthenticated()) {
            viewModelScope.launch {
                if (syncManager.getUserId() != ownerUid || _activeProfile.value.id != ownerProfile) return@launch
                repository.saveProgress(
                    ownerProfile,
                    current.media.id,
                    current.currentPositionSec,
                    current.durationSec,
                    current.episode?.id,
                    current.episode?.title,
                    season = coordinates.first,
                    episode = coordinates.second
                )
                val progressEntity = WatchProgressEntity(
                    profileId = ownerProfile,
                    mediaId = current.media.id,
                    positionSeconds = current.currentPositionSec,
                    totalSeconds = current.durationSec,
                    episodeId = current.episode?.id ?: "",
                    episodeTitle = current.episode?.title,
                    season = coordinates.first,
                    episode = coordinates.second,
                    lastWatchedTimestamp = System.currentTimeMillis()
                )
                if (syncManager.getUserId() != ownerUid) return@launch
                syncManager.syncWatchProgressToCloud(progressEntity, current.media)
            }
        }
    }

    fun savePlayerProgress() = persistPlayerProgress(_playerState.value)

    override fun onCleared() {
        detailJob?.cancel()
        seasonJob?.cancel()
        nextEpisodeJob?.cancel()
        resolveJob?.cancel()
        syncManager.cleanup()
        super.onCleared()
    }

    // Search Actions
    fun setSearchQuery(query: String) {
        _searchQuery.value = query.take(150)
        _isSearching.value = query.isNotBlank()
        _searchError.value = null
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (_searchQuery.value.isNotBlank()) delay(300)
            performSearch()
        }
    }

    fun setSearchGenre(genre: String?) {
        _selectedSearchGenre.value = if (_selectedSearchGenre.value == genre) null else genre
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch()
        }
    }

    private suspend fun performSearch() {
        val generation = ++searchGeneration
        val query = _searchQuery.value.trim()
        val genre = _selectedSearchGenre.value
        val profile = _activeProfile.value
        searchOwnerProfile = profile.id
        fun ownsRequest() = generation == searchGeneration && profile.id == _activeProfile.value.id &&
            query == _searchQuery.value.trim() && genre == _selectedSearchGenre.value
        _isSearching.value = query.isNotBlank(); _searchError.value = null
        try {
            if (query.isBlank()) {
                val list = _catalogMedia.value.filter { !profile.hasMaturityRestriction || it.isKidSafe(profile.contentMaxAge) }
                    .filter { genre == null || it.matchesGenre(genre) }
                if (ownsRequest()) _searchResults.value = list
                return
            }
            val apiKey = TmdbClient.apiKey
            if (!hasCatalogNetwork() || apiKey.isEmpty() || apiKey == "PLACEHOLDER") {
                if (ownsRequest()) { fallbackLocalSearch(query, genre); _searchError.value = "Showing saved titles. Connect for more results." }
                return
            }
            val response = kotlinx.coroutines.withTimeoutOrNull(12_000) { TmdbClient.service.searchMulti(apiKey, query) }
                ?: throw java.io.IOException("Search timed out")
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val list = response.results.mapNotNull { result ->
                when (result.mediaType) {
                    "movie" -> mapResultToMedia(result, MediaType.MOVIE)
                    "tv" -> mapResultToMedia(result, MediaType.TV_SHOW)
                    else -> null
                }
            }.filter { !profile.hasMaturityRestriction || it.isKidSafe(profile.contentMaxAge) }
                .filter { genre == null || it.matchesGenre(genre) }.distinctBy { it.type to it.id }
            if (ownsRequest()) _searchResults.value = list
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            if (ownsRequest()) { fallbackLocalSearch(query, genre); _searchError.value = "Search couldn't refresh. Showing saved matches." }
        } finally { if (ownsRequest()) _isSearching.value = false }
    }

    private fun fallbackLocalSearch(query: String, genre: String?) {
        val profile = _activeProfile.value
        var list = if (profile.hasMaturityRestriction) {
            _catalogMedia.value.filter { it.isKidSafe(profile.contentMaxAge) }
        } else {
            _catalogMedia.value
        }
        val q = query.lowercase()
        list = list.filter {
            it.title.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.genres.any { g -> g.lowercase().contains(q) } ||
            it.cast.any { c -> c.lowercase().contains(q) } ||
            it.director.lowercase().contains(q)
        }
        if (genre != null) {
            list = list.filter { it.matchesGenre(genre) }
        }
        _searchResults.value = list
    }

    // Downloads with real stream resolution and local file storage
    fun startDownload(media: MediaItem, episode: Episode? = null) {
        if (_userSubscription.value.isGuest) {
            showToast("Sign in to download movies and episodes for offline viewing.")
            _showAuthScreen.value = true
            return
        }
        if (!_userSubscription.value.isActive || _userSubscription.value.maxDownloads <= 0) {
            showToast("Offline downloads not supported on Mobile Plan. Upgrade to Basic or above.")
            _showSubscriptionSheet.value = true
            return
        }
        val key = if (episode == null) media.id else "${media.id}_${episode.id}"
        if (downloads.value.any { it.downloadKey == key && it.isComplete }) {
            showToast("Already downloaded. Open Downloads to watch or delete this title.")
            return
        }
        if (key !in downloadTasks.value && downloads.value.size + downloadTasks.value.size >= _userSubscription.value.maxDownloads) {
            showToast("Download limit reached (${_userSubscription.value.maxDownloads} titles on ${_userSubscription.value.planName}). Upgrade plan to download more.")
            _showSubscriptionSheet.value = true
            return
        }

        val epTitle = episode?.let(::episodeDownloadLabel)
        val itemTitle = if (epTitle != null) "${media.title} ($epTitle)" else media.title

        addNotification(
            NotificationItem(
                id = "notif_dl_start_${System.currentTimeMillis()}",
                title = "Download Started",
                message = "$itemTitle is downloading for offline viewing.",
                timestamp = "Just now",
                mediaId = media.id,
                isRead = false,
                iconType = NotificationIconType.DOWNLOAD
            )
        )

        downloadManager.startOrResumeDownload(
            profileId = _activeProfile.value.id,
            media = media,
            episode = episode,
            isWifiOnly = _isWifiOnlyEnabled.value,
            isHighQuality = _isHighQualityEnabled.value
        )
    }

    fun pauseDownload(key: String) {
        downloadManager.pauseDownload(key)
    }

    fun resumeDownload(key: String, media: MediaItem? = null, episode: Episode? = null) {
        if (!_userSubscription.value.isActive || _userSubscription.value.maxDownloads <= 0) {
            showToast("An active download plan is required to continue.")
            if (_userSubscription.value.isGuest) _showAuthScreen.value = true else _showSubscriptionSheet.value = true
            return
        }
        if (downloadManager.resumeSavedDownload(key, _activeProfile.value.id)) return
        val task = downloadTasks.value[key]
        val matchedMedia = media ?: task?.mediaId?.let { CatalogData.getById(it) }
            ?: CatalogData.allMedia.sortedByDescending { it.id.length }.firstOrNull { key == it.id || key.startsWith("${it.id}_") }
        if (matchedMedia == null) { showToast("This title's metadata is unavailable. Open its details to retry."); return }
        val episodeId = task?.episodeId ?: downloadEpisodeId(matchedMedia.id, key)
        val matchedEpisode = episode ?: episodeId?.let { id ->
            matchedMedia.episodes.find { it.id == id } ?: Episode(id, episodeCoordinates(id).second,
                task?.episodeTitle ?: "Episode ${episodeCoordinates(id).second}", 45, "")
        }
        startDownload(matchedMedia, matchedEpisode)
    }

    fun cancelDownload(key: String) {
        downloadManager.cancelDownload(key)
    }

    fun removeDownload(downloadKey: String) {
        downloadManager.deleteCompletedDownload(_activeProfile.value.id, downloadKey)
    }

    fun clearAllDownloads() {
        downloadManager.clearAllDownloads(_activeProfile.value.id)
    }

    fun updateAllocatedStorage(allocatedGb: Float) {
        _allocatedStorageGb.value = allocatedGb
        prefs.edit().putFloat("pref_allocated_storage_gb", allocatedGb).apply()
        showToast("Storage allocated for downloads: ${String.format(java.util.Locale.US, "%.1f", allocatedGb)} GB")
        setProfileDownloadAllocation(_activeProfile.value.id, allocatedGb)
        if (_downloadsForYouEnabled.value) runSmartDownloadCurator()
    }

    fun setupDownloadsForYouWithAllocation(allocatedGb: Float) {
        _allocatedStorageGb.value = allocatedGb
        _downloadsForYouEnabled.value = true
        setProfileDownloadAllocation(_activeProfile.value.id, allocatedGb)
        prefs.edit()
            .putFloat("pref_allocated_storage_gb", allocatedGb)
            .putBoolean("pref_downloads_for_you", true)
            .apply()
        showToast("Downloads for You enabled (${String.format(java.util.Locale.US, "%.1f", allocatedGb)} GB allocated)")
        runSmartDownloadCurator()
    }

    fun setupDownloadsForYou() {
        setupDownloadsForYouWithAllocation(_allocatedStorageGb.value)
    }

    fun setProfileDownloadAllocation(profileId: String, gb: Float) {
        if (!gb.isFinite() || _profiles.value.none { it.id == profileId }) return
        val amount = gb.coerceIn(0f, 10f)
        _profileDownloadAllocations.update { it + (profileId to amount) }
        prefs.edit().putFloat("profile_download_gb_$profileId", amount).apply()
        if (profileId == _activeProfile.value.id) _allocatedStorageGb.value = amount
    }

    fun toggleDownloadsForYou(enabled: Boolean) {
        _downloadsForYouEnabled.value = enabled
        prefs.edit().putBoolean("pref_downloads_for_you", enabled).apply()
        if (enabled) runSmartDownloadCurator() else smartCuratorJob?.cancel()
    }

    private var smartCuratorJob: Job? = null
    fun runSmartDownloadCurator() {
        smartCuratorJob?.cancel()
        val subscription = _userSubscription.value
        if (!_downloadsForYouEnabled.value || !subscription.isActive || subscription.maxDownloads <= 0) return
        smartCuratorJob = viewModelScope.launch {
            val profile = _activeProfile.value
            val pool = if (profile.hasMaturityRestriction) {
                _catalogMedia.value.ifEmpty { CatalogData.allMedia }.filter { it.isKidSafe(profile.contentMaxAge) }
            } else {
                _catalogMedia.value.ifEmpty { CatalogData.allMedia }
            }

            val likedIds = likedMedia.value.map { it.id }.toSet()
            val watchlistIds = watchlist.value.map { it.id }.toSet()

            downloadManager.curateSmartDownloads(
                profileId = profile.id,
                allocatedGb = _profileDownloadAllocations.value[profile.id] ?: _allocatedStorageGb.value,
                isWifiOnly = true,
                isHighQuality = _isHighQualityEnabled.value,
                candidatePool = pool,
                userLikedIds = likedIds,
                watchlistIds = watchlistIds,
                maxDownloads = subscription.maxDownloads
            )
        }
    }

    // Downloads screen navigation state
    private val _showDownloadsScreen = MutableStateFlow(false)
    val showDownloadsScreen: StateFlow<Boolean> = _showDownloadsScreen.asStateFlow()
    private val _openSmartDownloadSettings = MutableStateFlow(false)
    val openSmartDownloadSettings: StateFlow<Boolean> = _openSmartDownloadSettings.asStateFlow()

    fun openDownloadsScreen(show: Boolean, smartSettings: Boolean = false) {
        _openSmartDownloadSettings.value = smartSettings
        _showDownloadsScreen.value = show
    }

    // TV Pairing Screen navigation state
    private val _showTvPairScreen = MutableStateFlow(false)
    val showTvPairScreen: StateFlow<Boolean> = _showTvPairScreen.asStateFlow()

    fun openTvPairScreen(show: Boolean) {
        if (show && _userSubscription.value.isGuest) {
            showToast("Please sign in or create an account to pair with Android TV.")
            _showAuthScreen.value = true
            return
        }
        _showTvPairScreen.value = show
    }

    // 2026 My Netflix Actions
    fun openNotificationsSheet(show: Boolean) {
        _showNotificationsSheet.value = show
    }

    fun markNotificationAsRead(id: String) {
        _notifications.update { list ->
            list.map { if (it.id == id) it.copy(isRead = true) else it }
        }
    }

    fun markAllNotificationsAsRead() {
        _notifications.update { list ->
            list.map { it.copy(isRead = true) }
        }
        showToast("All notifications marked as read")
    }

    fun addNotification(item: NotificationItem) {
        _notifications.update { list ->
            listOf(item) + list
        }
    }

    private fun sendSystemDownloadNotification(
        title: String,
        message: String,
        notificationId: Int,
        isComplete: Boolean
    ) {
        try {
            val app = getApplication<Application>()
            val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channelId = "netflix_downloads_channel"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Downloads",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "NetflixPro Download Notifications"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(app, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("open_downloads", true)
            }

            val pendingIntent = android.app.PendingIntent.getActivity(
                app,
                notificationId,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notificationBuilder = androidx.core.app.NotificationCompat.Builder(app, channelId)
                .setSmallIcon(R.drawable.ic_netflix_download_custom)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)

            if (!isComplete) {
                notificationBuilder.setProgress(100, 0, true)
            }

            notificationManager.notify(notificationId, notificationBuilder.build())
        } catch (e: Exception) {
            android.util.Log.e("NetflixVM", "Failed to send system notification: ${e.message}")
        }
    }

    fun removeNotification(id: String) {
        _notifications.update { list ->
            list.filter { it.id != id }
        }
        showToast("Notification removed")
    }

    fun openSettingsDrawer(show: Boolean) {
        _showSettingsDrawer.value = show
    }

    fun openCastDialog(show: Boolean) {
        if (show && _userSubscription.value.isGuest) {
            showToast("Sign in & subscribe to Standard or Premium to cast to TV.")
            _showAuthScreen.value = true
            return
        }
        if (show && !_userSubscription.value.isTvAllowed) {
            showToast("Casting to TV requires Basic plan or above. Upgrade to cast.")
            _showSubscriptionSheet.value = true
            return
        }
        _showCastDialog.value = show
    }

    fun connectCastDevice(device: CastDevice) {
        viewModelScope.launch {
            _showCastDialog.value = false
            showToast("Connecting to ${device.name}...")
            delay(800)
            _connectedCastDevice.value = device
            showToast("Connected to ${device.name}")
        }
    }

    fun disconnectCastDevice() {
        _connectedCastDevice.value = null
        showToast("Disconnected from TV")
    }

    fun toggleSmartDownloads(enabled: Boolean) {
        _isSmartDownloadsEnabled.value = enabled
        prefs.edit().putBoolean("pref_smart_downloads_enabled", enabled).apply()
        showToast("Smart Downloads ${if (enabled) "turned ON" else "turned OFF"}")
        // Next-episode replacement is triggered by completed playback, not by this toggle.
    }

    fun toggleWifiOnly(enabled: Boolean) {
        _isWifiOnlyEnabled.value = enabled
        mobileSettings.set(MobileSetting.WIFI_ONLY_DOWNLOADS, enabled)
        downloadManager.updateWifiOnlyPolicy(enabled)
        showToast("Wi-Fi Only ${if (enabled) "enabled" else "disabled"}")
    }

    fun toggleHighQuality(enabled: Boolean) {
        _isHighQualityEnabled.value = enabled
        mobileSettings.set(MobileSetting.HIGH_QUALITY, enabled)
        showToast("Video Quality set to ${if (enabled) "High" else "Standard"}")
    }

    fun toggleAutoPlayNext(enabled: Boolean) {
        _isAutoPlayNextEnabled.value = enabled
        mobileSettings.set(MobileSetting.AUTOPLAY_NEXT, enabled)
        updateActivePlaybackPreferences(next = enabled)
        showToast("Auto-play Next Episode ${if (enabled) "ON" else "OFF"}")
    }

    fun toggleAutoPlayPreviews(enabled: Boolean) {
        _isAutoPlayPreviewsEnabled.value = enabled
        mobileSettings.set(MobileSetting.AUTOPLAY_PREVIEWS, enabled)
        updateActivePlaybackPreferences(previews = enabled)
        showToast("Auto-play Previews ${if (enabled) "ON" else "OFF"}")
    }

    fun toggleSpatialAudio(enabled: Boolean) {
        _isSpatialAudioEnabled.value = enabled
        mobileSettings.set(MobileSetting.SPATIAL_AUDIO, enabled)
        showToast("Spatial Audio ${if (enabled) "Enabled" else "Disabled"}")
    }

    fun setCellularDataOption(option: String) {
        val mode = CellularDataMode.fromLabel(option)
        mobileSettings.cellularDataMode = mode
        _cellularDataOption.value = mode.label
        showToast("Cellular Data: ${mode.label}")
    }

    private var passwordResetPending = false
    fun requestPasswordReset() {
        if (passwordResetPending) return
        passwordResetPending = true
        syncManager.requestPasswordReset { success ->
            passwordResetPending = false
            showToast(if (success) "Password reset email sent to your account address."
                else "Couldn't send the reset email. Check your connection and try again.")
        }
    }

    private val playbackPreferenceWrites = kotlinx.coroutines.sync.Mutex()
    private fun updateActivePlaybackPreferences(next: Boolean? = null, previews: Boolean? = null) {
        val owner = syncManager.getUserId()
        val profile = _activeProfile.value.let { it.copy(autoplayNext = next ?: it.autoplayNext,
            autoplayPreviews = previews ?: it.autoplayPreviews) }
        _activeProfile.value = profile
        _profiles.update { list -> list.map { if (it.id == profile.id) profile else it } }
        saveProfilesLocally(_profiles.value)
        viewModelScope.launch {
            playbackPreferenceWrites.lock()
            try {
                if (owner != syncManager.getUserId()) return@launch
                val latest = _profiles.value.firstOrNull { it.id == profile.id } ?: return@launch
                kotlinx.coroutines.withContext(Dispatchers.IO) { repository.insertProfile(latest.toEntity()) }
                if (owner == syncManager.getUserId()) syncManager.syncProfilesToCloud(_profiles.value, _activeProfile.value.id)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (_: Exception) { showToast("Playback preference saved locally. Profile sync couldn't finish.") }
            finally { playbackPreferenceWrites.unlock() }
        }
    }

    fun runDiagnosticTest() {
        if (_diagnosticRunning.value) return
        _diagnosticRunning.value = true
        viewModelScope.launch {
            _diagnosticResult.value = null
            try {
                _diagnosticResult.value = NetworkDiagnostic().run(playbackNetwork(getApplication()).online)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            finally { _diagnosticRunning.value = false }
        }
    }

    fun playTrailer(trailer: TrailerItem) {
        playTrailer(trailer.media, trailer.trailerTitle)
    }

    fun playTrailer(media: MediaItem, title: String) {
        if (!titleAllowed(media)) return
        if (!MobilePlaybackPolicy.permitsStreaming(mobileSettings.cellularDataMode,
                playbackNetwork(getApplication()).wifiOrEthernet)) {
            showToast("Connect to Wi-Fi or change Cellular Data in App Settings to play trailers.")
            return
        }
        finishSmartReplacement()
        val durationSec = if (title.contains("teaser", ignoreCase = true)) 105 else 192
        _playerState.value = PlayerState(
            media = media.copy(title = title),
            episode = null,
            isPlaying = false,
            isResolving = true,
            sourceId = "Trailer",
            currentPositionSec = 0,
            durationSec = durationSec,
            playbackSpeed = 1.0f,
            showControls = true,
            isLocked = false,
            showSkipIntro = false
        )

        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            try {
                val tmdbId = resolveTmdbId(media.id)?.toString() ?: media.id
                val type = if (media.type == MediaType.MOVIE) "movie" else "tv"
                showToast("Loading trailer...")

                val trailerStream = kotlinx.coroutines.suspendCancellableCoroutine<TrailerStream?> { continuation ->
                    val resolver = TrailerResolver(
                        context = getApplication(),
                        tmdbId = tmdbId,
                        mediaType = type,
                        callback = object : TrailerResolverCallback {
                            override fun onResolved(stream: TrailerStream) {
                                if (continuation.isActive) continuation.resume(stream)
                            }

                            override fun onError(error: String) {
                                if (continuation.isActive) continuation.resume(null)
                            }
                            override fun onExternalTrailer(url: String) {
                                if (continuation.isActive) continuation.resume(TrailerStream(url, "youtube"))
                            }
                        }
                    )
                    continuation.invokeOnCancellation { resolver.cancel() }
                    resolver.start()
                }
                if (trailerStream?.type == "youtube") {
                    val app = getApplication<Application>()
                    try {
                        app.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(trailerStream.url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        showToast("Install or enable YouTube to open this official trailer.")
                    }
                    closePlayer()
                } else if (trailerStream != null) {
                    _playerState.update { it.copy(isResolving = false, resolvedUrl = trailerStream.url,
                        resolveHeaders = trailerStream.headers, sourceId = "Trailer", isPlaying = true) }
                } else {
                    _playerState.update { it.copy(isResolving = false, resolveError = "Trailer not available for this title.", isPlaying = false) }
                    showToast("Trailer not available for this title.")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _playerState.update {
                    it.copy(
                        isResolving = false,
                        resolveError = e.message,
                        isPlaying = false
                    )
                }
                showToast("Unable to play trailer")
            }
        }
    }

    fun installGame(gameId: String) {
        if (_userSubscription.value.isGuest) {
            showToast("Sign in and subscribe to Standard or Premium to play NetflixPro Games.")
            _showAuthScreen.value = true
            return
        }
        if (!_userSubscription.value.isGamesAllowed) {
            showToast("Games are included with Standard and Premium plans. Upgrade to play.")
            _showSubscriptionSheet.value = true
            return
        }
        viewModelScope.launch {
            val game = _games.value.find { it.id == gameId } ?: return@launch
            showToast("Downloading ${game.title}...")

            for (step in 1..10) {
                delay(180)
                _games.update { list ->
                    list.map {
                        if (it.id == gameId) it.copy(downloadProgress = step / 10f) else it
                    }
                }
            }

            _games.update { list ->
                list.map {
                    if (it.id == gameId) it.copy(isInstalled = true, downloadProgress = null) else it
                }
            }
            showToast("${game.title} is installed and ready to play!")
        }
    }

    fun launchGame(game: GameItem) {
        if (_userSubscription.value.isGuest) {
            showToast("Sign in and subscribe to Standard or Premium to play NetflixPro Games.")
            _showAuthScreen.value = true
            return
        }
        if (!_userSubscription.value.isGamesAllowed) {
            showToast("Games are included with Standard and Premium plans. Upgrade to play.")
            _showSubscriptionSheet.value = true
            return
        }
        showToast("Launching ${game.title} • NetflixPro Games Engine")
    }

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastEvent.emit(message)
        }
    }

    private fun serializeCaptions(captions: List<com.example.data.Caption>): String {
        val arr = org.json.JSONArray()
        for (caption in captions) {
            val obj = org.json.JSONObject()
            obj.put("url", caption.url)
            obj.put("language", caption.language)
            obj.put("type", caption.type)
            obj.put("languageCode", caption.languageCode)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun deserializeCaptions(jsonStr: String?): List<com.example.data.Caption> {
        if (jsonStr.isNullOrEmpty()) return emptyList()
        val list = mutableListOf<com.example.data.Caption>()
        try {
            val arr = org.json.JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    com.example.data.Caption(
                        url = obj.optString("url"),
                        language = obj.optString("language"),
                        type = obj.optString("type"),
                        languageCode = obj.optString("languageCode", "en")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }
}
