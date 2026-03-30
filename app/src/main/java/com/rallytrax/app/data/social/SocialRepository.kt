package com.rallytrax.app.data.social

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
) {
    private val usersCollection get() = firestore.collection("users")

    private fun currentUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not signed in")

    private fun followingCollection(uid: String = currentUid()) =
        usersCollection.document(uid).collection("following")

    private fun followersCollection(uid: String = currentUid()) =
        usersCollection.document(uid).collection("followers")

    suspend fun searchUsersByEmail(email: String): List<UserProfile> {
        if (email.isBlank()) return emptyList()
        val query = usersCollection
            .whereGreaterThanOrEqualTo("email", email.lowercase())
            .whereLessThanOrEqualTo("email", email.lowercase() + "\uf8ff")
            .limit(20)
            .get()
            .await()
        return query.documents.mapNotNull { doc ->
            doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
        }.filter { it.uid != currentUid() }
    }

    suspend fun follow(targetUid: String) = coroutineScope {
        val uid = currentUid()
        val targetDeferred = async {
            usersCollection.document(targetUid).get().await()
                .toObject(UserProfile::class.java)?.copy(uid = targetUid)
        }
        val myDeferred = async {
            usersCollection.document(uid).get().await()
                .toObject(UserProfile::class.java)?.copy(uid = uid)
        }
        val targetProfile = targetDeferred.await() ?: return@coroutineScope
        val myProfile = myDeferred.await() ?: return@coroutineScope

        firestore.runBatch { batch ->
            batch.set(
                followingCollection(uid).document(targetUid),
                mapOf(
                    "uid" to targetUid,
                    "displayName" to targetProfile.displayName,
                    "photoUrl" to targetProfile.photoUrl,
                    "email" to targetProfile.email,
                    "followedAt" to System.currentTimeMillis(),
                ),
            )
            batch.set(
                followersCollection(targetUid).document(uid),
                mapOf(
                    "uid" to uid,
                    "displayName" to myProfile.displayName,
                    "photoUrl" to myProfile.photoUrl,
                    "email" to myProfile.email,
                    "followedAt" to System.currentTimeMillis(),
                ),
            )
        }.await()
    }

    suspend fun unfollow(targetUid: String) {
        val uid = currentUid()
        firestore.runBatch { batch ->
            batch.delete(followingCollection(uid).document(targetUid))
            batch.delete(followersCollection(targetUid).document(uid))
        }.await()
    }

    fun getFollowing(): Flow<List<UserProfile>> =
        observeProfiles(followingCollection())

    fun getFollowers(): Flow<List<UserProfile>> =
        observeProfiles(followersCollection())

    private fun observeProfiles(
        collection: com.google.firebase.firestore.CollectionReference,
    ): Flow<List<UserProfile>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val profiles = snapshot?.documents?.mapNotNull { doc ->
                UserProfile(
                    uid = doc.getString("uid") ?: doc.id,
                    displayName = doc.getString("displayName"),
                    photoUrl = doc.getString("photoUrl"),
                    email = doc.getString("email"),
                )
            } ?: emptyList()
            trySend(profiles)
        }
        awaitClose { registration.remove() }
    }
}
