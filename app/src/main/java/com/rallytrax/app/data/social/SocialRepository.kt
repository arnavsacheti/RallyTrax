package com.rallytrax.app.data.social

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val usersCollection get() = firestore.collection("users")

    private val currentUid: String?
        get() = auth.currentUser?.uid

    // ---- User Profile ----

    suspend fun createOrUpdateProfile() {
        val user = auth.currentUser ?: return
        val profile = mapOf(
            "uid" to user.uid,
            "displayName" to user.displayName,
            "photoUrl" to user.photoUrl?.toString(),
            "email" to user.email,
            "joinedAt" to (user.metadata?.creationTimestamp ?: System.currentTimeMillis()),
        )
        usersCollection.document(user.uid).set(profile, SetOptions.merge()).await()
    }

    suspend fun getUserProfile(uid: String): UserProfile? {
        val snapshot = usersCollection.document(uid).get().await()
        return snapshot.toObject(UserProfile::class.java)
    }

    suspend fun searchUsersByEmail(email: String): List<UserProfile> {
        val snapshot = usersCollection
            .whereEqualTo("email", email)
            .get()
            .await()
        return snapshot.toObjects(UserProfile::class.java)
    }

    // ---- Follow / Unfollow ----

    suspend fun follow(targetUid: String) {
        val myUid = currentUid ?: return
        val timestamp = mapOf("followedAt" to System.currentTimeMillis())
        firestore.runBatch { batch ->
            batch.set(
                usersCollection.document(myUid)
                    .collection("following").document(targetUid),
                timestamp,
            )
            batch.set(
                usersCollection.document(targetUid)
                    .collection("followers").document(myUid),
                timestamp,
            )
        }.await()
    }

    suspend fun unfollow(targetUid: String) {
        val myUid = currentUid ?: return
        firestore.runBatch { batch ->
            batch.delete(
                usersCollection.document(myUid)
                    .collection("following").document(targetUid),
            )
            batch.delete(
                usersCollection.document(targetUid)
                    .collection("followers").document(myUid),
            )
        }.await()
    }

    fun getFollowing(): Flow<List<UserProfile>> = observeRelationship("following")

    fun getFollowers(): Flow<List<UserProfile>> = observeRelationship("followers")

    private fun observeRelationship(subcollection: String): Flow<List<UserProfile>> {
        val uid = currentUid ?: return flowOf(emptyList())
        return callbackFlow {
            val registration = usersCollection.document(uid)
                .collection(subcollection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val uids = snapshot?.documents?.mapNotNull { it.id } ?: emptyList()
                launch {
                    val profiles = uids.mapNotNull { uid -> getUserProfile(uid) }
                    trySend(profiles)
                }
            }
            awaitClose { registration.remove() }
        }
    }

    // ---- Shared Tracks ----

    suspend fun publishTrack(track: SharedTrack) {
        usersCollection.document(track.ownerUid)
            .collection("sharedTracks")
            .document(track.trackId)
            .set(track)
            .await()
    }

    suspend fun getFriendActivities(
        followingUids: List<String>,
        limit: Int = 20,
    ): List<SharedTrack> {
        if (followingUids.isEmpty()) return emptyList()

        return coroutineScope {
            followingUids.map { uid ->
                async {
                    usersCollection.document(uid)
                        .collection("sharedTracks")
                        .orderBy("publishedAt", Query.Direction.DESCENDING)
                        .limit(limit.toLong())
                        .get()
                        .await()
                        .toObjects(SharedTrack::class.java)
                }
            }.flatMap { it.await() }
                .sortedByDescending { it.publishedAt }
                .take(limit)
        }
    }
}
