package com.rallytrax.app.data.social

data class UserProfile(
    val uid: String = "",
    val displayName: String? = null,
    val photoUrl: String? = null,
    val email: String? = null,
    val joinedAt: Long = 0L,
)
