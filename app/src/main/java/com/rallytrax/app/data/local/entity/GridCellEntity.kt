package com.rallytrax.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Heatmap grid cell. Each cell represents a ~111m x ~111m area (at equator).
 * gridLat/gridLon are bucket indices: floor(lat * 1000), floor(lon * 1000).
 */
@Entity(tableName = "grid_cells")
data class GridCellEntity(
    @PrimaryKey
    val cellId: Long, // encoded from gridLat/gridLon
    val gridLat: Int, // floor(lat * 1000)
    val gridLon: Int, // floor(lon * 1000)
    val trackCount: Int = 0, // distinct tracks passing through
    val pointCount: Int = 0, // total TrackPoints in cell (heatmap weight)
    val lastUpdated: Long = System.currentTimeMillis(),
) {
    companion object {
        fun encodeCellId(gridLat: Int, gridLon: Int): Long {
            return (gridLat.toLong() shl 32) or (gridLon.toLong() and 0xFFFFFFFFL)
        }

        fun gridLatFor(lat: Double): Int = kotlin.math.floor(lat * 1000.0).toInt()
        fun gridLonFor(lon: Double): Int = kotlin.math.floor(lon * 1000.0).toInt()
    }
}
