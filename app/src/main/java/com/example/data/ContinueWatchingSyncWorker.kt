package com.example.data

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import org.json.JSONObject

/** Coalesces each title to its latest event and survives process death and offline viewing. */
object ContinueWatchingOutbox {
    private const val STORE = "continue-watching-outbox"
    @Synchronized fun enqueue(context: Context, uid: String, profile: String, media: String, data: Map<String, Any>) {
        val key = "$uid/$profile/$media"
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val saved = prefs.getString(key, null)?.let { JSONObject(it).getJSONObject("data").optLong("lastWatchedTimestamp") }
        val stamp = (data["lastWatchedTimestamp"] as Number).toLong()
        if (ContinueWatchingEventPolicy.isNewer(stamp, saved)) {
            val json = JSONObject().put("uid", uid).put("profile", profile).put("media", media).put("data", JSONObject(data))
            check(prefs.edit().putString(key, json.toString()).commit()) { "Watch progress could not be stored" }
        }
        resume(context)
    }
    fun resume(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("continue-watching-sync", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ContinueWatchingSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    @Synchronized fun pending(context: Context): Map<String, String> = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        .all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
    @Synchronized fun acknowledge(context: Context, key: String, value: String) {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        if (prefs.getString(key, null) == value) prefs.edit().remove(key).commit()
    }
}

class ContinueWatchingSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val user = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous } ?: return Result.success()
        val db = FirebaseFirestore.getInstance()
        for ((key, value) in ContinueWatchingOutbox.pending(applicationContext)) {
            val json = JSONObject(value)
            if (json.getString("uid") != user.uid) continue
            if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) return Result.success()
            try {
                val profile = json.getString("profile")
                val media = json.getString("media")
                val objectData = json.getJSONObject("data")
                val data = objectData.keys().asSequence().associateWith { objectData.get(it) }.toMutableMap()
                data["updatedAt"] = FieldValue.serverTimestamp()
                val paths = mutableListOf(
                    "users/${user.uid}/continue_watching" to "${profile}_$media",
                    "users/${user.uid}/profiles/$profile/continue_watching" to media,
                    "users/${user.uid}/profiles/$profile/continueWatching" to media)
                if (data["isRemoved"] != true || data.containsKey("playbackPositionMs")) {
                    paths.add("users/${user.uid}/profiles/$profile/watch_history" to media)
                    paths.add("users/${user.uid}/watch_history" to "${profile}_$media")
                }
                ContinueWatchingCloudCommit.write(db, paths.map { (path, id) -> db.collection(path).document(id) to data })
                ContinueWatchingOutbox.acknowledge(applicationContext, key, value)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { return Result.retry() }
        }
        return Result.success()
    }
}
