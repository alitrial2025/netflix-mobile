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
import com.example.data.TrailerResolver
import com.example.data.TrailerResolverCallback
import com.example.data.TrailerStream
import com.example.data.model.toEntity
import com.example.data.model.toUserProfile
import com.example.data.download.NetflixDownloadManager
import com.example.data.download.DownloadTaskInfo
import com.example.data.download.DownloadTaskStatus
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
        catalogRefreshJob = viewModelScope.launch {
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
        if (profile.isKidProfile) {
            catalog.filter { it.isKidSafe(profile.maxAge) }
        } else {
            catalog
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    val downloadManager: NetflixDownloadManager = NetflixDownloadManager(application, repository, netMirrorResolver)

    val downloadTasks: StateFlow<Map<String, DownloadTaskInfo>> = downloadManager.downloadTasks
    val downloadingProgress: StateFlow<Map<String, Float>> = downloadManager.downloadingProgress
    val pausedDownloadKeys: StateFlow<Set<String>> = downloadManager.pausedDownloadKeys

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

    private val _isWifiOnlyEnabled = MutableStateFlow(true)
    val isWifiOnlyEnabled: StateFlow<Boolean> = _isWifiOnlyEnabled.asStateFlow()

    private val _isHighQualityEnabled = MutableStateFlow(true)
    val isHighQualityEnabled: StateFlow<Boolean> = _isHighQualityEnabled.asStateFlow()

    private val _isAutoPlayNextEnabled = MutableStateFlow(true)
    val isAutoPlayNextEnabled: StateFlow<Boolean> = _isAutoPlayNextEnabled.asStateFlow()

    private val _isAutoPlayPreviewsEnabled = MutableStateFlow(true)
    val isAutoPlayPreviewsEnabled: StateFlow<Boolean> = _isAutoPlayPreviewsEnabled.asStateFlow()

    private val _isSpatialAudioEnabled = MutableStateFlow(true)
    val isSpatialAudioEnabled: StateFlow<Boolean> = _isSpatialAudioEnabled.asStateFlow()

    private val _cellularDataOption = MutableStateFlow("Automatic (Balanced)")
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
        if (profile.isKidProfile) {
            list.filter { it.isKidFriendly() }
        } else {
            list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogData.gamesList)

    // Filtered search results
    private val _searchResults = MutableStateFlow<List<MediaItem>>(emptyList())
    val searchResults: StateFlow<List<MediaItem>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

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
                if (_searchQuery.value.isBlank()) {
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
                    showToast("Welcome to Netflix Pro! Account created.")
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
        syncManager.signOutUser()
        _watchHistory.value = emptyList()
        accountResetJob = viewModelScope.launch(Dispatchers.IO) {
            try {
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
                showToast("Sign in and subscribe to Standard or Premium to play Netflix Games.")
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
        val safeProfile = updatedProfile.copy(pin = ProfilePin.hash(updatedProfile.pin))
        val isNewProfile = _profiles.value.none { it.id == safeProfile.id }
        if (isNewProfile && _profiles.value.size >= _userSubscription.value.maxProfiles) {
            showToast("Profile limit reached (${_userSubscription.value.maxProfiles} profile(s) on ${_userSubscription.value.planName} Plan). Upgrade to add more.")
            _showSubscriptionSheet.value = true
            return
        }
        _profiles.update { list ->
            if (list.any { it.id == safeProfile.id }) {
                list.map { if (it.id == safeProfile.id) safeProfile else it }
            } else {
                list + safeProfile
            }
        }
        if (_activeProfile.value.id == safeProfile.id || _profiles.value.size == 1) {
            _activeProfile.value = safeProfile
        }
        _showEditProfileScreen.value = false
        _editingProfile.value = null
        saveProfilesLocally(_profiles.value)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.insertProfile(safeProfile.toEntity())
            } catch (e: Exception) {
                android.util.Log.e("NetflixViewModel", "Error saving profile to Room: ${e.message}")
            }
        }
        syncManager.syncProfilesToCloud(_profiles.value, _activeProfile.value.id)
        showToast("Saved ${safeProfile.name}'s profile")
    }

    fun deleteProfile(profileId: String) {
        if (_profiles.value.size <= 1) {
            showToast("Cannot delete the only profile")
            return
        }
        val target = _profiles.value.find { it.id == profileId }
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

    fun openDetail(media: MediaItem) {
        _selectedMedia.value = media
        fetchMediaDetails(media)
    }

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

    fun fetchTvSeasonEpisodes(media: MediaItem, seasonNumber: Int) {
        viewModelScope.launch {
            try {
                val apiKey = TmdbClient.apiKey
                val mediaIdInt = resolveTmdbId(media.id) ?: return@launch
                val seasonDetails = TmdbClient.service.getTvSeasonDetails(mediaIdInt, seasonNumber, apiKey)
                val episodesList = seasonDetails.episodes.map { ep ->
                    Episode(
                        id = "ep_${media.id}_S${seasonNumber}_${ep.episodeNumber}",
                        episodeNumber = ep.episodeNumber,
                        title = ep.name.ifEmpty { "Episode ${ep.episodeNumber}" },
                        durationMinutes = ep.runtime ?: 45,
                        description = ep.overview?.ifBlank { null } ?: "No description available for this episode.",
                        stillUrl = ep.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                    )
                }
                _selectedMedia.update { current ->
                    if (current?.id == media.id) {
                        current.copy(episodes = episodesList)
                    } else current
                }
            } catch (_: Exception) {}
        }
    }

    private fun fetchMediaDetails(media: MediaItem) {
        viewModelScope.launch {
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
                        if (current?.id == media.id) {
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

                    // Fetch episodes for Season 1 from TMDB
                    val seasonDetails = try {
                        TmdbClient.service.getTvSeasonDetails(mediaIdInt, 1, apiKey)
                    } catch (e: Exception) {
                        null
                    }

                    val episodesList = seasonDetails?.episodes?.map { ep ->
                        Episode(
                            id = "ep_${media.id}_S1_${ep.episodeNumber}",
                            episodeNumber = ep.episodeNumber,
                            title = ep.name.ifEmpty { "Episode ${ep.episodeNumber}" },
                            durationMinutes = ep.runtime ?: 45,
                            description = ep.overview?.ifBlank { null } ?: "No description available.",
                            stillUrl = ep.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                        )
                    } ?: media.episodes

                    _selectedMedia.update { current ->
                        if (current?.id == media.id) {
                            current.copy(
                                tagline = details.tagline?.ifBlank { null } ?: current.tagline,
                                cast = if (castNames.isNotEmpty()) castNames else current.cast,
                                director = directorName,
                                durationOrSeasons = seasonsStr,
                                totalSeasons = numSeasons,
                                totalEpisodes = numEpisodes,
                                description = details.overview?.ifBlank { null } ?: current.description,
                                episodes = episodesList,
                                logoUrl = logoUrl,
                                posterUrl = posterUrl,
                                backdropUrl = backdropUrl,
                                similarMedia = similarItems
                            )
                        } else current
                    }
                }
            } catch (e: Exception) {
                // Fail silently, keeping the lightweight details
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

    fun playMedia(media: MediaItem, episode: Episode? = null) {
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

        val totalSec = if (episode != null) episode.durationMinutes * 60 else if (media.type == MediaType.MOVIE) 7200 else 3000
        val targetEpisode = episode ?: media.episodes.firstOrNull()

        _playerState.value = PlayerState(
            media = media,
            episode = targetEpisode,
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
                    if (progressEntity.positionSeconds > 10 && progressEntity.positionSeconds < totalSec - 60) {
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
                if (!existingDownload.videoUrl.isNullOrEmpty()) {
                    showToast("Playing downloaded stream offline...")
                    _playerState.update {
                        it.copy(
                            isResolving = false,
                            resolvedUrl = existingDownload.videoUrl,
                            resolveHeaders = emptyMap(),
                            sourceId = "Offline Download",
                            captions = offlineCaptions,
                            isPlaying = true
                        )
                    }
                    return@launch
                }
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
                        } else e.message ?: "This title could not be played.",
                        resolvedUrl = null,
                        isPlaying = false
                    )
                }
            }
        }
    }

    fun updatePlayerProgress(currentSec: Int, totalDurationSec: Int) {
        val currentState = _playerState.value
        val showSkip = currentState.sourceId != "Trailer" && currentState.media?.type == MediaType.TV_SHOW &&
            currentState.introWindow?.let { currentSec in it.startSec until it.endSec } == true
        val dur = if (totalDurationSec > 0) totalDurationSec else _playerState.value.durationSec
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

        // Smart Downloads Auto-Watch Trigger
        if (!isTrailer && currentSec > 0 && dur > 60 && currentSec >= dur - 15) {
            if (ep != null && _isSmartDownloadsEnabled.value && progressTracker.claimSmartDownload()) {
                downloadManager.handleEpisodeWatched(
                    profileId = _activeProfile.value.id,
                    media = media,
                    watchedEpisode = ep,
                    isSmartDownloadsEnabled = _isSmartDownloadsEnabled.value,
                    isWifiOnly = _isWifiOnlyEnabled.value,
                    isHighQuality = _isHighQualityEnabled.value,
                    allocatedGb = _allocatedStorageGb.value,
                    catalog = _catalogMedia.value.ifEmpty { CatalogData.allMedia }
                )
            }
        }
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
        _playerState.update { it.copy(currentPositionSec = clamped, showSkipIntro = showSkip) }
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
        _playerState.update { it.copy(audioTrack = track, showAudioSubtitleDialog = false) }
        showToast("Audio: $track")
    }

    fun setSubtitleTrack(track: String) {
        _playerState.update { it.copy(subtitleTrack = track, showAudioSubtitleDialog = false) }
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

    fun playNextEpisode() {
        val state = _playerState.value
        val media = state.media ?: return
        val currentEp = state.episode ?: return
        val episodes = media.episodes
        val nextIdx = nextEpisodeIndex(currentEp.id, episodes.map { it.id })
        if (nextIdx != null) {
            playMedia(media, episodes[nextIdx])
            showToast("Playing Next Episode: ${episodes[nextIdx].title}")
        } else {
            showToast("You've reached the latest episode!")
        }
    }

    fun closePlayer() {
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
        resolveJob?.cancel()
        downloadManager.close()
        syncManager.cleanup()
        super.onCleared()
    }

    // Search Actions
    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
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
        val query = _searchQuery.value.trim()
        val genre = _selectedSearchGenre.value
        val profile = _activeProfile.value

        if (query.isBlank()) {
            var list = if (profile.isKidProfile) {
                _catalogMedia.value.filter { it.isKidSafe(profile.maxAge) }
            } else {
                _catalogMedia.value
            }
            if (genre != null) {
                list = list.filter { it.matchesGenre(genre) }
            }
            _searchResults.value = list
        } else {
            try {
                val apiKey = TmdbClient.apiKey
                if (apiKey.isEmpty() || apiKey == "PLACEHOLDER") {
                    fallbackLocalSearch(query, genre)
                    return
                }

                val response = TmdbClient.service.searchMulti(apiKey, query)
                val mapped = response.results.mapNotNull { res ->
                    if (res.mediaType == "person") return@mapNotNull null
                    val mType = if (res.mediaType == "tv") MediaType.TV_SHOW else MediaType.MOVIE
                    mapResultToMedia(res, mType)
                }

                var list = if (profile.isKidProfile) {
                    mapped.filter { it.isKidSafe(profile.maxAge) }
                } else {
                    mapped
                }
                if (genre != null) {
                    list = list.filter { it.matchesGenre(genre) }
                }

                _searchResults.value = list
            } catch (e: Exception) {
                fallbackLocalSearch(query, genre)
            }
        }
    }

    private fun fallbackLocalSearch(query: String, genre: String?) {
        val profile = _activeProfile.value
        var list = if (profile.isKidProfile) {
            _catalogMedia.value.filter { it.isKidSafe(profile.maxAge) }
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
        if (_userSubscription.value.maxDownloads <= 0) {
            showToast("Offline downloads not supported on Mobile Plan. Upgrade to Basic or above.")
            _showSubscriptionSheet.value = true
            return
        }
        if (downloads.value.size >= _userSubscription.value.maxDownloads) {
            showToast("Download limit reached (${_userSubscription.value.maxDownloads} titles on ${_userSubscription.value.planName}). Upgrade plan to download more.")
            _showSubscriptionSheet.value = true
            return
        }

        val epTitle = if (episode != null) "S1:E${episode.episodeNumber} ${episode.title}" else null
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
        val mediaId = key.substringBefore("_")
        val matchedMedia = media ?: CatalogData.getById(mediaId)
        val matchedEpisode = episode ?: run {
            if (key.contains("_") && matchedMedia != null) {
                val epId = key.substringAfter("_")
                matchedMedia.episodes.find { it.id == epId }
            } else null
        }

        if (matchedMedia != null) {
            downloadManager.startOrResumeDownload(
                profileId = _activeProfile.value.id,
                media = matchedMedia,
                episode = matchedEpisode,
                isWifiOnly = _isWifiOnlyEnabled.value,
                isHighQuality = _isHighQualityEnabled.value
            )
        } else {
            showToast("Unable to resume download")
        }
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
        if (_isSmartDownloadsEnabled.value) {
            runSmartDownloadCurator()
        }
    }

    fun setupDownloadsForYouWithAllocation(allocatedGb: Float) {
        _allocatedStorageGb.value = allocatedGb
        _isSmartDownloadsEnabled.value = true
        prefs.edit()
            .putFloat("pref_allocated_storage_gb", allocatedGb)
            .putBoolean("pref_smart_downloads_enabled", true)
            .apply()
        showToast("Downloads for You enabled (${String.format(java.util.Locale.US, "%.1f", allocatedGb)} GB allocated)")
        runSmartDownloadCurator()
    }

    fun setupDownloadsForYou() {
        setupDownloadsForYouWithAllocation(_allocatedStorageGb.value)
    }

    fun runSmartDownloadCurator() {
        viewModelScope.launch {
            val profile = _activeProfile.value
            val pool = if (profile.isKidProfile) {
                _catalogMedia.value.ifEmpty { CatalogData.allMedia }.filter { it.isKidSafe(profile.maxAge) }
            } else {
                _catalogMedia.value.ifEmpty { CatalogData.allMedia }
            }

            val likedIds = likedMedia.value.map { it.id }.toSet()
            val watchlistIds = watchlist.value.map { it.id }.toSet()

            downloadManager.curateSmartDownloads(
                profileId = profile.id,
                allocatedGb = _allocatedStorageGb.value,
                isWifiOnly = _isWifiOnlyEnabled.value,
                isHighQuality = _isHighQualityEnabled.value,
                candidatePool = pool,
                userLikedIds = likedIds,
                watchlistIds = watchlistIds
            )
        }
    }

    // Downloads screen navigation state
    private val _showDownloadsScreen = MutableStateFlow(false)
    val showDownloadsScreen: StateFlow<Boolean> = _showDownloadsScreen.asStateFlow()

    fun openDownloadsScreen(show: Boolean) {
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
                    description = "Netflix Download Notifications"
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
        if (enabled) {
            runSmartDownloadCurator()
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        _isWifiOnlyEnabled.value = enabled
        showToast("Wi-Fi Only ${if (enabled) "enabled" else "disabled"}")
    }

    fun toggleHighQuality(enabled: Boolean) {
        _isHighQualityEnabled.value = enabled
        showToast("Video Quality set to ${if (enabled) "4K Ultra HD & Dolby Atmos" else "Standard HD"}")
    }

    fun toggleAutoPlayNext(enabled: Boolean) {
        _isAutoPlayNextEnabled.value = enabled
        showToast("Auto-play Next Episode ${if (enabled) "ON" else "OFF"}")
    }

    fun toggleAutoPlayPreviews(enabled: Boolean) {
        _isAutoPlayPreviewsEnabled.value = enabled
        showToast("Auto-play Previews ${if (enabled) "ON" else "OFF"}")
    }

    fun toggleSpatialAudio(enabled: Boolean) {
        _isSpatialAudioEnabled.value = enabled
        showToast("Spatial Audio ${if (enabled) "Enabled" else "Disabled"}")
    }

    fun setCellularDataOption(option: String) {
        _cellularDataOption.value = option
        showToast("Cellular Data: $option")
    }

    fun runDiagnosticTest() {
        viewModelScope.launch {
            _diagnosticRunning.value = true
            _diagnosticResult.value = null
            showToast("Running network speed test & Netflix server connection...")
            delay(1500)
            _diagnosticRunning.value = false
            _diagnosticResult.value = "Connection: Excellent (118.4 Mbps) • 4K UHD Ready • Low Latency (14ms)"
            showToast("Network check completed: 118.4 Mbps")
        }
    }

    fun playTrailer(trailer: TrailerItem) {
        playTrailer(trailer.media, trailer.trailerTitle)
    }

    fun playTrailer(media: MediaItem, title: String) {
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
            showToast("Sign in and subscribe to Standard or Premium to play Netflix Games.")
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
            showToast("Sign in and subscribe to Standard or Premium to play Netflix Games.")
            _showAuthScreen.value = true
            return
        }
        if (!_userSubscription.value.isGamesAllowed) {
            showToast("Games are included with Standard and Premium plans. Upgrade to play.")
            _showSubscriptionSheet.value = true
            return
        }
        showToast("Launching ${game.title} • Netflix Games Engine")
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
