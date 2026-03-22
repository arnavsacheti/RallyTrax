package com.rallytrax.app.data.sync

data class SyncStatus(
    val lastSyncTime: Long = 0L,
    val isSyncing: Boolean = false,
    val pendingChanges: Boolean = false,
    val error: String? = null,
)
