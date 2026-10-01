package com.example.data

import android.content.Context
import android.util.Log
import com.example.data.local.WatchProgressEntity
import com.example.data.local.WatchlistEntity
import com.example.data.model.MediaItem
import com.example.data.model.AvatarType
import com.example.data.model.UserProfile
import com.example.data.model.ProfilePin
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class CloudMyListChange(
    val mediaId: String,
    val isAdded: Boolean,
    val addedAt: Long
)

data class CloudWatchHistoryItem(
    val mediaId: String,
    val title: String,
    val posterUrl: String,
    val type: String,
    val lastWatchedTimestamp: Long,
    val isCompleted: Boolean
)

data class TvSessionData(
    val sessionCode: String = "",
    val status: String = "PENDING", // PENDING, PAIRED, EXPIRED
    val userId: String = "",
    val userEmail: String = "",
    val activeProfileId: String = "",
    val deviceName: String = "Living Room Android TV",
    val pairedAt: Long = 0L,
    val profilesJson: String = ""
)

class FirebaseSyncManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var isFirebaseReady = false
    private var observedUid: String? = null

    private val _isCloudSyncEnabled = MutableStateFlow(false)
    val isCloudSyncEnabled: StateFlow<Boolean> = _isCloudSyncEnabled.asStateFlow()

    private val _lastSyncStatus = MutableStateFlow("Local / Ready")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus.asStateFlow()

    private val _pairedTvSessions = MutableStateFlow<List<TvSessionData>>(emptyList())
    val pairedTvSessions: StateFlow<List<TvSessionData>> = _pairedTvSessions.asStateFlow()

    private var profileListener: ListenerRegistration? = null
    private val continueWatchingGeneration = java.util.concurrent.atomic.AtomicLong(0L)
    private var continueWatchingListener: ListenerRegistration? = null
    private val _continueWatchingMedia = MutableStateFlow<Map<String, MediaItem>>(emptyMap())
    val continueWatchingMedia: StateFlow<Map<String, MediaItem>> = _continueWatchingMedia.asStateFlow()
    private var myListListener: ListenerRegistration? = null
    private var watchHistoryListener: ListenerRegistration? = null
    private var subscriptionListener: ListenerRegistration? = null
    private var tvSessionListener: ListenerRegistration? = null

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    init {
        ensureFirebase()
    }

    private fun ensureFirebase(): Boolean {
        return try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            if (firestore == null) {
                firestore = FirebaseFirestore.getInstance()
            }
            if (auth == null) {
                auth = FirebaseAuth.getInstance()
                auth?.addAuthStateListener { firebaseAuth ->
                    val user = firebaseAuth.currentUser
                    if (observedUid != user?.uid) cleanupListeners()
                    observedUid = user?.uid
                    _currentUserEmail.value = user?.email
                    if (user != null) {
                        if (!user.isAnonymous) ContinueWatchingOutbox.resume(context)
                        _lastSyncStatus.value = "Connected as ${user.email}"
                    } else {
                        cleanupListeners()
                        _lastSyncStatus.value = "Local / Ready"
                    }
                }
            }
            isFirebaseReady = true
            _currentUserEmail.value = auth?.currentUser?.email
            _isCloudSyncEnabled.value = true
            _lastSyncStatus.value = if (auth?.currentUser != null) {
                "Connected as ${auth?.currentUser?.email}"
            } else {
                "Local / Ready"
            }
            Log.d("FirebaseSync", "✅ Firebase Firestore & Auth initialized successfully")
            true
        } catch (e: Exception) {
            Log.w("FirebaseSync", "Firebase initialization exception: ${e.message}", e)
            isFirebaseReady = false
            _isCloudSyncEnabled.value = false
            _lastSyncStatus.value = "Local / Ready"
            false
        }
    }

    fun getUserId(): String {
        return auth?.currentUser?.uid.orEmpty()
    }

    fun isAuthenticated(): Boolean = auth?.currentUser?.isAnonymous == false

    fun requestPasswordReset(onResult: (Boolean) -> Unit) {
        val account = auth
        val email = account?.currentUser?.email
        if (account == null || email.isNullOrBlank() || !isAuthenticated()) { onResult(false); return }
        account.sendPasswordResetEmail(email).addOnCompleteListener { onResult(it.isSuccessful) }
    }

    // ==========================================
    // AUTHENTICATION (SIGN IN & SIGN UP)
    // ==========================================

    suspend fun signInWithEmail(
        email: String,
        pass: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        ensureFirebase()
        try {
            val a = auth
            if (a != null && isFirebaseReady) {
                val result = a.signInWithEmailAndPassword(email.trim(), pass).await()
                val user = result.user
                val userEmail = user?.email ?: email
                _currentUserEmail.value = userEmail
                _lastSyncStatus.value = "Signed in as $userEmail"

                // Ensure user doc & subscription doc exist on sign in
                if (user != null) {
                    val db = firestore
                    if (db != null) {
                        try {
                            val userRef = db.collection("users").document(user.uid)
                            userRef.set(
                                hashMapOf(
                                    "uid" to user.uid,
                                    "email" to userEmail,
                                    "lastSignInAt" to System.currentTimeMillis()
                                ),
                                SetOptions.merge()
                            ).await()
                        } catch (docErr: Exception) {
                            Log.w("FirebaseSync", "User doc sync on sign-in: ${docErr.message}")
                        }
                    }
                }

                onSuccess(userEmail)
            } else {
                onError("Sign in is unavailable. Check your connection and try again.")
            }
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Sign in error: ${e.message}")
            onError(e.message ?: "Sign in failed. Check email and password.")
        }
    }

    suspend fun signUpWithEmail(
        email: String,
        pass: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        ensureFirebase()
        try {
            val a = auth
            if (a != null && isFirebaseReady) {
                val result = a.createUserWithEmailAndPassword(email.trim(), pass).await()
                val user = result.user
                val userEmail = user?.email ?: email
                _currentUserEmail.value = userEmail

                // Initialize default documents for new user
                if (user != null) {
                    val db = firestore
                    if (db != null) {
                        try {
                            // 1. users/{uid} base doc
                            val userRef = db.collection("users").document(user.uid)
                            userRef.set(hashMapOf(
                                "uid" to user.uid,
                                "email" to userEmail,
                                "createdAt" to System.currentTimeMillis(),
                                "updatedAt" to System.currentTimeMillis(),
                                "subscriptionPlanId" to "plan_guest",
                                "subscriptionStatus" to "NONE"
                            ), SetOptions.merge()).await()

                            // Subscription entitlement is issued only after payment verification.
                            // Account creation must never grant an active paid plan.
                            Log.d("FirebaseSync", "✅ Successfully created user & subscription documents in Firestore for ${user.uid}")
                        } catch (e: Exception) {
                            Log.e("FirebaseSync", "Failed to initialize user documents in Firestore: ${e.message}", e)
                        }
                    }
                }

                _lastSyncStatus.value = "Account created: $userEmail"
                onSuccess(userEmail)
            } else {
                onError("Account creation is unavailable. Check your connection and try again.")
            }
        } catch (e: Exception) {
            Log.e("FirebaseSync", "Sign up error: ${e.message}", e)
            onError(e.message ?: "Sign up failed. Try again.")
        }
    }

    fun signOutUser() {
        cleanupListeners()
        auth?.signOut()
        _currentUserEmail.value = null
        _lastSyncStatus.value = "Signed out"
    }

    // ==========================================
    // PROFILES SYNC
    // ==========================================

    fun syncProfilesToCloud(profiles: List<UserProfile>, activeProfileId: String? = null) {
        ensureFirebase()
        scope.launch {
            try {
                val db = firestore
                val user = auth?.currentUser
                if (db != null && isFirebaseReady && user != null) {
                    val uid = user.uid
                    val batch = db.batch()
                    profiles.forEach { profile ->
                        val docRef = db.collection("users").document(uid).collection("profiles").document(profile.id)
                        val data = hashMapOf(
                            "id" to profile.id,
                            "name" to profile.name,
                            "avatarUrl" to (profile.avatarUrl ?: ""),
                            "avatarType" to profile.avatarType.name,
                            "isKids" to profile.isKids,
                            "maxAge" to profile.maxAge,
                            "pin" to (ProfilePin.hash(profile.pin) ?: ""),
                            "language" to profile.language,
                            "audioLanguage" to profile.audioLanguage,
                            "subtitleLanguage" to profile.subtitleLanguage,
                            "autoplayNext" to profile.autoplayNext,
                            "autoplayPreviews" to profile.autoplayPreviews,
                            "gameHandle" to (profile.gameHandle ?: ""),
                            "updatedAt" to System.currentTimeMillis()
                        )
                        batch.set(docRef, data, SetOptions.merge())
                    }
                    if (activeProfileId != null) {
                        val userDoc = db.collection("users").document(uid)
                        batch.set(userDoc, mapOf("activeProfileId" to activeProfileId, "lastSync" to System.currentTimeMillis()), SetOptions.merge())
                    }
                    batch.commit().await()
                    _lastSyncStatus.value = "Profiles Synced (${profiles.size})"
                    Log.d("FirebaseSync", "✅ Successfully synced ${profiles.size} profiles to Firestore")
                } else {
                    _lastSyncStatus.value = "Profiles Saved Locally (${profiles.size})"
                }
            } catch (e: Exception) {
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.d("FirebaseSync", "Firestore permission denied for profiles sync (offline mode active)")
                } else {
                    Log.w("FirebaseSync", "Profiles sync note: ${e.message}")
                }
                _lastSyncStatus.value = "Profiles Saved Locally (${profiles.size})"
            }
        }
    }

    fun deleteProfileFromCloud(profileId: String) {
        scope.launch {
            try {
                val db = firestore
                val user = auth?.currentUser
                if (db != null && isFirebaseReady && user != null) {
                    db.collection("users").document(user.uid).collection("profiles").document(profileId).delete().await()
                    Log.d("FirebaseSync", "Deleted profile $profileId from Firestore")
                }
            } catch (e: Exception) {
                Log.d("FirebaseSync", "Delete profile note: ${e.message}")
            }
        }
    }

    fun listenToProfiles(onProfilesLoaded: (List<UserProfile>) -> Unit) {
        val db = firestore
        val user = auth?.currentUser
        if (db != null && isFirebaseReady && user != null) {
            val uid = user.uid
            profileListener?.remove()
            profileListener = db.collection("users").document(uid).collection("profiles")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d("FirebaseSync", "Firestore profile permission not granted yet (using local profiles)")
                            profileListener?.remove()
                            profileListener = null
                        } else {
                            Log.w("FirebaseSync", "Profile listener notice: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null && !snapshot.isEmpty) {
                        val list = snapshot.documents.mapNotNull { doc ->
                            try {
                                UserProfile(
                                    id = doc.getString("id") ?: doc.id,
                                    name = doc.getString("name") ?: "Profile",
                                    avatarUrl = doc.getString("avatarUrl")?.takeIf { it.isNotBlank() },
                                    avatarType = try {
                                        AvatarType.valueOf(doc.getString("avatarType") ?: "CUSTOM")
                                    } catch (e: Exception) { AvatarType.CUSTOM },
                                    isKids = doc.getBoolean("isKids") ?: false,
                                    maxAge = (doc.getLong("maxAge") ?: 18).toInt(),
                                    pin = doc.getString("pin")?.takeIf { it.isNotBlank() },
                                    language = doc.getString("language") ?: "English",
                                    audioLanguage = doc.getString("audioLanguage") ?: "Original",
                                    subtitleLanguage = doc.getString("subtitleLanguage") ?: "Off",
                                    autoplayNext = doc.getBoolean("autoplayNext") ?: true,
                                    autoplayPreviews = doc.getBoolean("autoplayPreviews") ?: true,
                                    gameHandle = doc.getString("gameHandle")?.takeIf { it.isNotBlank() }
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (list.isNotEmpty()) {
                            onProfilesLoaded(list)
                        }
                    }
                }
        }
    }

    // ==========================================
    // SUBSCRIPTION SYNC
    // ==========================================

    // Activation is committed only by PayheroVerifier's receipt transaction.
    // The UI callback reads that commit rather than granting a local plan.
    suspend fun loadConfirmedSubscription(receipt: String, paymentReference: String): com.example.data.model.UserSubscription? {
        if (!ensureFirebase()) return null
        val db = firestore ?: return null
        val user = auth?.currentUser ?: return null
        if (user.isAnonymous || receipt.isBlank()) return null
        val snapshot = db.collection("users").document(user.uid)
            .collection("subscription").document("current").get(Source.SERVER).await()
        if (auth?.currentUser?.uid != user.uid || !snapshot.exists() || snapshot.metadata.hasPendingWrites() ||
            snapshot.getString("mpesaReceipt") != receipt || snapshot.getString("paymentReference") != paymentReference) return null
        return com.example.data.model.UserSubscription(
            status = snapshot.getString("status") ?: "NONE",
            planId = snapshot.getString("planId") ?: "plan_guest",
            planName = snapshot.getString("planName") ?: "Guest",
            amount = (snapshot.getLong("amount") ?: 0L).toInt(),
            currency = snapshot.getString("currency") ?: "KES",
            paymentReference = snapshot.getString("paymentReference") ?: "",
            mpesaReceipt = snapshot.getString("mpesaReceipt") ?: "",
            subscribedAt = snapshot.getLong("subscribedAt") ?: 0L,
            expiresAt = snapshot.getLong("expiresAt") ?: 0L
        )
    }

    fun listenToSubscription(onSubscriptionLoaded: (com.example.data.model.UserSubscription) -> Unit) {
        val db = firestore
        val user = auth?.currentUser
        if (db != null && isFirebaseReady && user != null) {
            subscriptionListener?.remove()
            subscriptionListener = db.collection("users").document(user.uid).collection("subscription").document("current")
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (auth?.currentUser?.uid != user.uid) return@addSnapshotListener
                    if (error != null) {
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.w("FirebaseSync", "Subscription read denied")
                            onSubscriptionLoaded(com.example.data.model.UserSubscription())
                        } else {
                            Log.w("FirebaseSync", "Subscription listener notice: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot?.metadata?.hasPendingWrites() == true) return@addSnapshotListener
                    if (snapshot != null && snapshot.exists()) {
                        if (!snapshot.metadata.isFromCache) runCatching { snapshot.getTimestamp("updatedAt")?.toDate()?.time }.getOrNull()?.let {
                            SubscriptionTime.observeServerTimestamp(it)
                        }
                        val status = snapshot.getString("status") ?: "NONE"
                        val planId = snapshot.getString("planId") ?: "plan_guest"
                        val planName = snapshot.getString("planName") ?: "Guest"
                        val expiresAt = snapshot.getLong("expiresAt") ?: 0L

                        val sub = com.example.data.model.UserSubscription(
                            status = status,
                            planId = planId,
                            planName = planName,
                            amount = (snapshot.getLong("amount") ?: 0L).toInt(),
                            currency = snapshot.getString("currency") ?: "KES",
                            paymentReference = snapshot.getString("paymentReference") ?: "",
                            mpesaReceipt = snapshot.getString("mpesaReceipt") ?: "",
                            subscribedAt = snapshot.getLong("subscribedAt") ?: 0L,
                            expiresAt = expiresAt
                        )
                        onSubscriptionLoaded(sub)
                    } else if (user != null) {
                        onSubscriptionLoaded(com.example.data.model.UserSubscription())
                    }
                }
        }
    }

    // My List is scoped to a Firebase account and profile. Tombstones keep removals
    // visible to devices that were offline when a title was removed.
    suspend fun seedMyListIfCloudEmpty(
        profileId: String,
        localEntries: List<WatchlistEntity>
    ): Boolean {
        val user = auth?.currentUser ?: return false
        val db = firestore ?: return false
        return try {
            val collection = db.collection("users").document(user.uid)
                .collection("profiles").document(profileId).collection("my_list")
            // A server read prevents an empty offline cache from resurrecting
            // titles that another device has removed.
            val remote = collection.get(Source.SERVER).await()
            if (remote.isEmpty && localEntries.isNotEmpty()) {
                val batch = db.batch()
                localEntries.take(500).forEach { entry ->
                    batch.set(collection.document(entry.mediaId), mapOf(
                        "profileId" to profileId,
                        "mediaId" to entry.mediaId,
                        "isAdded" to true,
                        "addedAt" to entry.addedTimestamp,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ), SetOptions.merge())
                }
                batch.commit().await()
            }
            true
        } catch (e: Exception) {
            Log.w("FirebaseSync", "My List migration deferred: " + e.message)
            false
        }
    }

    fun syncMyListItem(profileId: String, mediaId: String, isAdded: Boolean, addedAt: Long) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        scope.launch {
            try {
                db.collection("users").document(user.uid)
                    .collection("profiles").document(profileId)
                    .collection("my_list").document(mediaId)
                    .set(
                        mapOf(
                            "profileId" to profileId,
                            "mediaId" to mediaId,
                            "isAdded" to isAdded,
                            "addedAt" to addedAt,
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    ).await()
            } catch (e: Exception) {
                Log.w("FirebaseSync", "My List sync failed: " + e.message)
            }
        }
    }

    fun listenToMyList(profileId: String, onChanges: (List<CloudMyListChange>) -> Unit) {
        myListListener?.remove()
        myListListener = null
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        myListListener = db.collection("users").document(user.uid)
            .collection("profiles").document(profileId).collection("my_list")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseSync", "My List listener error: " + error.message)
                    return@addSnapshotListener
                }
                val changes = snapshot?.documentChanges?.mapNotNull { change ->
                    val doc = change.document
                    val mediaId = doc.getString("mediaId") ?: doc.id
                    if (mediaId.isBlank()) null else CloudMyListChange(
                        mediaId = mediaId,
                        isAdded = change.type != DocumentChange.Type.REMOVED &&
                            (doc.getBoolean("isAdded") ?: true),
                        addedAt = doc.getLong("addedAt") ?: System.currentTimeMillis()
                    )
                } ?: emptyList()
                if (changes.isNotEmpty()) onChanges(changes)
            }
    }

    // ==========================================
    // CONTINUE WATCHING SYNC
    // ==========================================

    fun syncWatchProgressToCloud(progress: WatchProgressEntity, media: MediaItem? = null) {
        ensureFirebase()
        val ownerUid = auth?.currentUser?.takeUnless { it.isAnonymous }?.uid ?: return
        scope.launch {
            try {
                val db = firestore
                val user = auth?.currentUser
                if (db != null && isFirebaseReady && user != null && user.uid == ownerUid) {
                    val docId = "${progress.profileId}_${progress.mediaId}"
                    val continueDocRef = db.collection("users").document(user.uid)
                        .collection("continue_watching").document(docId)

                    val posMs = progress.positionSeconds * 1000L
                    val durMs = progress.totalSeconds * 1000L
                    val isCompleted = progress.totalSeconds > 0 &&
                        progress.positionSeconds >= (progress.totalSeconds * 0.95)
                    val removeFromContinue = isCompleted && media?.type == com.example.data.model.MediaType.MOVIE

                    val data = hashMapOf(
                        "profileId" to progress.profileId,
                        "mediaId" to progress.mediaId,
                        "positionSeconds" to progress.positionSeconds,
                        "totalSeconds" to progress.totalSeconds,
                        "durationSeconds" to progress.totalSeconds,
                        "playbackPositionMs" to posMs,
                        "durationMs" to durMs,
                        "lastWatchedTimestamp" to progress.lastWatchedTimestamp,
                        "isCompleted" to isCompleted,
                        "episodeId" to (progress.episodeId ?: ""),
                        "episodeTitle" to (progress.episodeTitle ?: ""),
                        "episodeName" to (progress.episodeTitle ?: ""),
                        "season" to progress.season,
                        "episode" to progress.episode,
                        "syncedFromDevice" to "Android Mobile",
                        "updatedAt" to System.currentTimeMillis()
                    )
                    if (media != null) {
                        data["title"] = media.title
                        data["posterUrl"] = media.posterUrl.orEmpty()
                        data["backdropUrl"] = media.backdropUrl.orEmpty()
                        data["description"] = media.description
                        data["type"] = if (media.type.name == "TV_SHOW") "Series" else "Movie"
                        data["year"] = media.releaseYear.toString()
                        data["rating"] = media.maturityRating
                        data["duration"] = media.durationOrSeasons
                        data["logoUrl"] = media.logoUrl.orEmpty()
                    }

                    data["isRemoved"] = removeFromContinue
                    ContinueWatchingOutbox.enqueue(context, ownerUid, progress.profileId, progress.mediaId, data)

                    Log.d("FirebaseSync", "✅ Synced continue watching and watch history for ${progress.mediaId}")
                }
            } catch (e: Exception) {
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                    Log.d("FirebaseSync", "Watch progress sync: permission denied (local caching active)")
                } else {
                    Log.d("FirebaseSync", "Watch progress sync note: ${e.message}")
                }
            }
        }
    }

    fun listenToContinueWatching(
        profileId: String,
        onProgressUpdated: (List<WatchProgressEntity>, List<String>) -> Unit
    ) {
        val generation = continueWatchingGeneration.incrementAndGet()
        val db = firestore
        val user = auth?.currentUser
        _continueWatchingMedia.value = emptyMap()
        if (db != null && isFirebaseReady && user != null) {
            continueWatchingListener?.remove()
            continueWatchingListener = db.collection("users").document(user.uid)
                .collection("continue_watching")
                .whereEqualTo("profileId", profileId)
                .addSnapshotListener { snapshot, error ->
                    if (continueWatchingGeneration.get() != generation || auth?.currentUser?.uid != user.uid) return@addSnapshotListener
                    if (error != null) {
                        if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.d("FirebaseSync", "Continue watching permission notice (using local Room DB)")
                            continueWatchingListener?.remove()
                            continueWatchingListener = null
                            _continueWatchingMedia.value = emptyMap()
                        } else {
                            Log.w("FirebaseSync", "Continue watching listener notice: ${error.message}")
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot != null && auth?.currentUser?.uid == user.uid) {
                        _continueWatchingMedia.value = snapshot.documents.filter { it.getBoolean("isRemoved") != true }
                            .mapNotNull { doc ->
                                val id = doc.getString("mediaId") ?: return@mapNotNull null
                                val title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                                val type = if (doc.getString("type").equals("Series", true) || doc.getString("type").equals("TV_SHOW", true))
                                    com.example.data.model.MediaType.TV_SHOW else com.example.data.model.MediaType.MOVIE
                                id to MediaItem(id, title, type, doc.getString("description").orEmpty(), "", 0,
                                    doc.getString("rating") ?: "18+", doc.getString("year")?.toIntOrNull() ?: 0,
                                    doc.getString("duration").orEmpty(), isOriginal = false, genres = emptyList(), cast = emptyList(), director = "",
                                    posterUrl = doc.getString("posterUrl"), backdropUrl = doc.getString("backdropUrl"), logoUrl = doc.getString("logoUrl"))
                            }.toMap()
                        val removed = snapshot.documentChanges
                            .filter { it.type == DocumentChange.Type.REMOVED }
                            .mapNotNull { it.document.getString("mediaId") }
                        val list = snapshot.documentChanges
                            .filter { it.type != DocumentChange.Type.REMOVED }
                            .mapNotNull { change ->
                            val doc = change.document
                            try {
                                val posMs = doc.getLong("playbackPositionMs")
                                val posSec = if (posMs != null && posMs > 0L) (posMs / 1000L).toInt() else (doc.getLong("positionSeconds") ?: 0L).toInt()
                                val durMs = doc.getLong("durationMs")
                                val totalSec = if (durMs != null && durMs > 0L) (durMs / 1000L).toInt() else (doc.getLong("totalSeconds") ?: doc.getLong("durationSeconds") ?: 100L).toInt()

                                val epTitle = doc.getString("episodeTitle")?.takeIf { it.isNotBlank() } ?: doc.getString("episodeName")?.takeIf { it.isNotBlank() }
                                val seasonVal = (doc.getLong("season") ?: 1L).toInt()
                                val epVal = (doc.getLong("episode") ?: 1L).toInt()

                                WatchProgressEntity(
                                    profileId = doc.getString("profileId") ?: profileId,
                                    mediaId = doc.getString("mediaId") ?: return@mapNotNull null,
                                    positionSeconds = if (doc.getBoolean("isRemoved") == true) totalSec.coerceAtLeast(1) else posSec,
                                    totalSeconds = totalSec.coerceAtLeast(1),
                                    episodeId = if (doc.getString("episodeId").isNullOrBlank()) "" else
                                        ContinueWatchingEventPolicy.mobileEpisodeId(doc.getString("mediaId").orEmpty(), seasonVal, epVal),
                                    episodeTitle = epTitle,
                                    season = seasonVal,
                                    episode = epVal,
                                    lastWatchedTimestamp = doc.getLong("lastWatchedTimestamp") ?: 0L
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        if (list.isNotEmpty() || removed.isNotEmpty()) {
                            onProgressUpdated(list, removed)
                        }
                    }
                }
        }
    }

    suspend fun removeContinueWatchingFromCloud(profileId: String, mediaId: String): Boolean {
        val user = auth?.currentUser ?: return false
        val db = firestore ?: return false
        return try {
            ContinueWatchingOutbox.enqueue(context, user.uid, profileId, mediaId, mapOf(
                "profileId" to profileId, "mediaId" to mediaId, "isRemoved" to true,
                "lastWatchedTimestamp" to ContinueWatchingEventPolicy.newTimestamp()))
            true
        } catch (_: Exception) { false }
    }

    // ==========================================
    // TV PAIRING & SCAN LOGIN FLOW
    // ==========================================

    companion object {
        fun extractPairingCode(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val trimmed = raw.trim()

            val queryParamRegex = Regex("""(?i)(?:[?&]code=|code=|session=|pin=)\s*([^&/\s?#]+)""")
            val queryMatch = queryParamRegex.find(trimmed)
            if (queryMatch != null && queryMatch.groupValues.size > 1) {
                val extracted = sanitizeCode(queryMatch.groupValues[1])
                if (extracted.isNotBlank()) return extracted
            }

            if (trimmed.contains("://") || trimmed.contains("?") || trimmed.startsWith("www.") || trimmed.contains(".app") || trimmed.contains(".com")) {
                try {
                    val fullUriString = if (!trimmed.contains("://")) "https://$trimmed" else trimmed
                    val uri = android.net.Uri.parse(fullUriString)

                    for (paramName in uri.queryParameterNames) {
                        if (paramName.equals("code", ignoreCase = true) ||
                            paramName.equals("session", ignoreCase = true) ||
                            paramName.equals("c", ignoreCase = true) ||
                            paramName.equals("pin", ignoreCase = true)
                        ) {
                            val value = uri.getQueryParameter(paramName)
                            if (!value.isNullOrBlank()) {
                                val sanitized = sanitizeCode(value)
                                if (sanitized.isNotBlank()) return sanitized
                            }
                        }
                    }

                    val segments = uri.pathSegments
                    if (!segments.isNullOrEmpty()) {
                        val last = segments.last()
                        val upper = last.uppercase()
                        if (last.isNotBlank() && upper != "PAIR" && upper != "LOGIN" && upper != "AUTH" && upper != "CONNECT") {
                            val sanitized = sanitizeCode(last)
                            if (sanitized.isNotBlank()) return sanitized
                        }
                    }
                } catch (_: Exception) {}
            }

            var cleaned = trimmed
                .replace(Regex("""(?i)^netflix-tv-auth:"""), "")
                .replace(Regex("""(?i)^netflix://pair\?code="""), "")
                .replace(Regex("""(?i)^netflix://"""), "")
                .replace(Regex("""(?i)^https?://"""), "")
                .trim()

            if (cleaned.contains("?")) {
                val afterQ = cleaned.substringAfter("?")
                for (part in afterQ.split("&")) {
                    if (part.contains("=")) {
                        val (k, v) = part.split("=", limit = 2)
                        if (k.trim().equals("code", ignoreCase = true) || k.trim().equals("session", ignoreCase = true)) {
                            val sanitized = sanitizeCode(v)
                            if (sanitized.isNotBlank()) return sanitized
                        }
                    }
                }
                cleaned = cleaned.substringBefore("?")
            }

            if (cleaned.contains("/")) {
                val lastSegment = cleaned.substringAfterLast("/")
                val upper = lastSegment.uppercase()
                if (lastSegment.isNotBlank() && upper != "PAIR" && upper != "LOGIN" && upper != "AUTH") {
                    cleaned = lastSegment
                }
            }

            return sanitizeCode(cleaned)
        }

        fun sanitizeCode(code: String): String {
            var sanitized = code.trim()
                .replace("/", "")
                .replace("\\", "")
                .replace(Regex("""[^a-zA-Z0-9_-]"""), "")
                .uppercase()

            if (sanitized.startsWith("NF") && !sanitized.startsWith("NF-")) {
                sanitized = "NF-" + sanitized.removePrefix("NF")
            } else if (sanitized.startsWith("NET") && !sanitized.startsWith("NET-")) {
                sanitized = "NET-" + sanitized.removePrefix("NET")
            } else if (sanitized.length in 5..8 && !sanitized.contains("-")) {
                sanitized = "NF-$sanitized"
            }
            return sanitized
        }
    }

    suspend fun pairTvWithSessionCode(
        sessionCode: String,
        profiles: List<UserProfile>,
        activeProfile: UserProfile,
        onSuccess: (TvSessionData) -> Unit,
        onError: (String) -> Unit
    ) {
        // A client-written Firestore session cannot prove the user's identity to a TV.
        // The TV must authenticate with Firebase Auth directly while no trusted
        // pairing service is available.
        onError("For secure account access, sign in with your email and password on the TV.")
    }

    fun unpairTv(sessionCode: String) {
        // No revocation callable exists yet. Remove this phone's local control entry only.
        val cleanCode = extractPairingCode(sessionCode)
        _pairedTvSessions.value = _pairedTvSessions.value.filterNot { it.sessionCode == cleanCode }
        _lastSyncStatus.value = "TV removed from this phone"
    }

    private var streamHeartbeatJob: kotlinx.coroutines.Job? = null

    fun startActiveStreamHeartbeat(deviceId: String, deviceName: String, mediaTitle: String, maxAllowedScreens: Int, onLimitExceeded: (Int, Int) -> Unit) {
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        streamHeartbeatJob?.cancel()

        streamHeartbeatJob = scope.launch {
            while (this.isActive) {
                try {
                    val streamData = hashMapOf(
                        "deviceId" to deviceId,
                        "deviceName" to deviceName,
                        "mediaTitle" to mediaTitle,
                        "lastHeartbeat" to System.currentTimeMillis()
                    )
                    db.collection("users").document(user.uid).collection("active_streams").document(deviceId)
                        .set(streamData, SetOptions.merge()).await()

                    val activeCutoff = System.currentTimeMillis() - 45_000L
                    val snapshot = db.collection("users").document(user.uid).collection("active_streams")
                        .whereGreaterThan("lastHeartbeat", activeCutoff)
                        .get().await()

                    val activeCount = snapshot.size()
                    if (activeCount > maxAllowedScreens) {
                        onLimitExceeded(activeCount, maxAllowedScreens)
                    }
                } catch (e: Exception) {
                    Log.d("FirebaseSync", "Heartbeat notice: ${e.message}")
                }
                kotlinx.coroutines.delay(20_000L)
            }
        }
    }

    fun stopActiveStreamHeartbeat(deviceId: String) {
        streamHeartbeatJob?.cancel()
        val user = auth?.currentUser ?: return
        val db = firestore ?: return
        scope.launch {
            try {
                db.collection("users").document(user.uid).collection("active_streams").document(deviceId).delete()
            } catch (_: Exception) {}
        }
    }

    fun sendRemoteCommandToTv(sessionCode: String, command: String, extraMs: Long = 0L) {
        // Session-code control belonged to the removed client-only pairing flow.
        // Both devices now require their own Firebase Auth session.
    }

    fun listenToWatchHistory(
        profileId: String,
        onHistoryLoaded: (List<CloudWatchHistoryItem>) -> Unit
    ) {
        watchHistoryListener?.remove()
        watchHistoryListener = null
        val db = firestore ?: return
        val uid = auth?.currentUser?.takeUnless { it.isAnonymous }?.uid ?: return
        watchHistoryListener = db.collection("users").document(uid)
            .collection("profiles").document(profileId).collection("watch_history")
            .orderBy("lastWatchedTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(100L)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("FirebaseSync", "Watch History listener failed: " + error.message)
                    return@addSnapshotListener
                }
                if (auth?.currentUser?.uid != uid) return@addSnapshotListener
                val items = snapshot?.documents?.mapNotNull { doc ->
                    val mediaId = doc.getString("mediaId") ?: doc.id
                    if (mediaId.isBlank()) return@mapNotNull null
                    CloudWatchHistoryItem(
                        mediaId = mediaId,
                        title = doc.getString("title")?.takeIf { it.isNotBlank() } ?: "Watched title",
                        posterUrl = doc.getString("posterUrl").orEmpty(),
                        type = doc.getString("type").orEmpty(),
                        lastWatchedTimestamp = doc.getLong("lastWatchedTimestamp") ?: 0L,
                        isCompleted = doc.getBoolean("isCompleted") ?: false
                    )
                }.orEmpty()
                onHistoryLoaded(items)
            }
    }

    private fun cleanupListeners() {
        profileListener?.remove()
        profileListener = null
        continueWatchingGeneration.incrementAndGet()
        continueWatchingListener?.remove()
        continueWatchingListener = null
        _continueWatchingMedia.value = emptyMap()
        myListListener?.remove()
        myListListener = null
        watchHistoryListener?.remove()
        watchHistoryListener = null
        subscriptionListener?.remove()
        subscriptionListener = null
        tvSessionListener?.remove()
        tvSessionListener = null
    }

    fun cleanup() {
        streamHeartbeatJob?.cancel()
        cleanupListeners()
    }
}
