package com.rallytrax.app.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSyncHelper @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val auth: FirebaseAuth,
) {
    private val currentUid: String? get() = auth.currentUser?.uid

    private fun syncDoc(uid: String, docName: String) =
        firestore.collection("users").document(uid).collection("sync").document(docName)

    // ── Settings ─────────────────────────────────────────────────────────────

    suspend fun getSettingsDoc(): Pair<SyncableSettings?, String?> = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext Pair(null, null)
        val doc = syncDoc(uid, "settings").get().await()
        if (doc.exists()) {
            val settings = doc.getString("json")?.let { SyncableSettings.fromJson(it) }
            val md5 = doc.getString("md5")
            Pair(settings, md5)
        } else {
            Pair(null, null)
        }
    }

    suspend fun setSettings(settings: SyncableSettings) = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext
        val json = settings.toJson()
        syncDoc(uid, "settings")
            .set(
                mapOf(
                    "json" to json,
                    "md5" to md5Hash(json),
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    // ── Garage ───────────────────────────────────────────────────────────────

    suspend fun getGarageMd5(): String? = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext null
        val doc = syncDoc(uid, "garage").get().await()
        doc.getString("md5")
    }

    suspend fun setGarageData(json: String) = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext
        syncDoc(uid, "garage")
            .set(
                mapOf(
                    "json" to json,
                    "md5" to md5Hash(json),
                    "updatedAt" to System.currentTimeMillis(),
                ),
            )
            .await()
    }

    // ── Manifest ─────────────────────────────────────────────────────────────

    suspend fun getManifest(): SyncManifest? = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext null
        val doc = syncDoc(uid, "manifest").get().await()
        if (doc.exists()) doc.getString("json")?.let { SyncManifest.fromJson(it) } else null
    }

    suspend fun setManifest(manifest: SyncManifest) = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext
        syncDoc(uid, "manifest")
            .set(mapOf("json" to manifest.toJson(), "updatedAt" to System.currentTimeMillis()))
            .await()
    }

    // ── GPX via Cloud Storage ────────────────────────────────────────────────

    suspend fun uploadGpxFile(trackId: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext
        val ref = storage.reference.child("users/$uid/tracks/$trackId.gpx")
        ref.putBytes(bytes).await()
    }

    suspend fun downloadGpxFile(trackId: String): ByteArray? = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext null
        val ref = storage.reference.child("users/$uid/tracks/$trackId.gpx")
        try {
            ref.getBytes(MAX_GPX_BYTES).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download GPX for track $trackId", e)
            null
        }
    }

    suspend fun listBackedUpTrackIds(): List<String> = withContext(Dispatchers.IO) {
        val uid = currentUid ?: return@withContext emptyList()
        val ref = storage.reference.child("users/$uid/tracks/")
        try {
            ref.listAll().await().items.map { it.name.removeSuffix(".gpx") }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list backed up tracks", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "FirestoreSyncHelper"
        private const val MAX_GPX_BYTES = 50_000_000L // 50MB max

        fun md5Hash(content: String): String {
            val digest = MessageDigest.getInstance("MD5")
            val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
