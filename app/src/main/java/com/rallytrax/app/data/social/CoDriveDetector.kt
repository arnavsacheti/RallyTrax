package com.rallytrax.app.data.social

import com.rallytrax.app.data.local.entity.TrackEntity

object CoDriveDetector {

    private const val TIME_TOLERANCE_MS = 3_600_000L // 1 hour

    data class CoDriveMatch(
        val friendTrack: SharedTrack,
        val localTrackId: String,
        val localTrackName: String,
    )

    /**
     * Detects co-drives by finding friend tracks that overlap with local tracks
     * both spatially (bounding boxes intersect) and temporally (within 1 hour).
     */
    fun detectCoDrives(
        friendTracks: List<SharedTrack>,
        localTracks: List<TrackEntity>,
    ): List<CoDriveMatch> {
        val matches = mutableListOf<CoDriveMatch>()
        for (friendTrack in friendTracks) {
            for (localTrack in localTracks) {
                if (boundingBoxesOverlap(friendTrack, localTrack) &&
                    timeWindowsOverlap(friendTrack, localTrack)
                ) {
                    matches.add(
                        CoDriveMatch(
                            friendTrack = friendTrack,
                            localTrackId = localTrack.id,
                            localTrackName = localTrack.name,
                        )
                    )
                }
            }
        }
        return matches
    }

    private fun boundingBoxesOverlap(friend: SharedTrack, local: TrackEntity): Boolean {
        if (friend.boundingBoxSouthLat > local.boundingBoxNorthLat) return false
        if (friend.boundingBoxNorthLat < local.boundingBoxSouthLat) return false
        if (friend.boundingBoxWestLon > local.boundingBoxEastLon) return false
        if (friend.boundingBoxEastLon < local.boundingBoxWestLon) return false
        return true
    }

    private fun timeWindowsOverlap(friend: SharedTrack, local: TrackEntity): Boolean {
        val friendStart = friend.recordedAt
        val friendEnd = friendStart + friend.durationMs
        val localStart = local.recordedAt
        val localEnd = localStart + local.durationMs
        return friendStart <= localEnd + TIME_TOLERANCE_MS &&
            friendEnd >= localStart - TIME_TOLERANCE_MS
    }
}
