package com.rallytrax.app.data.local

import com.rallytrax.app.data.local.dao.GridCellDao
import com.rallytrax.app.data.local.dao.LatLonProjection
import com.rallytrax.app.data.local.entity.GridCellEntity
import com.rallytrax.app.data.local.entity.TrackPointEntity

/**
 * Incrementally updates grid cells when a new track is saved.
 * Only cells touched by the new track are updated.
 */
object GridCellComputer {

    suspend fun updateForTrack(points: List<TrackPointEntity>, gridCellDao: GridCellDao) {
        updateForLatLon(points.map { LatLonProjection(it.lat, it.lon) }, gridCellDao)
    }

    /**
     * Lightweight incremental update using only lat/lon data.
     * Avoids loading full TrackPointEntity fields when only coordinates are needed.
     */
    suspend fun updateForLatLon(points: List<LatLonProjection>, gridCellDao: GridCellDao) {
        if (points.isEmpty()) return

        // Bucket points into grid cells
        val cellBuckets = mutableMapOf<Long, Int>()
        val cellCoords = mutableMapOf<Long, Pair<Int, Int>>() // cellId -> (gridLat, gridLon)
        for (pt in points) {
            val gLat = GridCellEntity.gridLatFor(pt.lat)
            val gLon = GridCellEntity.gridLonFor(pt.lon)
            val cellId = GridCellEntity.encodeCellId(gLat, gLon)
            cellBuckets[cellId] = (cellBuckets[cellId] ?: 0) + 1
            cellCoords.putIfAbsent(cellId, gLat to gLon)
        }

        // Update or create cells
        val updatedCells = cellBuckets.map { (cellId, count) ->
            val existing = gridCellDao.getCellById(cellId)
            val (gLat, gLon) = cellCoords.getValue(cellId)
            GridCellEntity(
                cellId = cellId,
                gridLat = gLat,
                gridLon = gLon,
                trackCount = (existing?.trackCount ?: 0) + 1,
                pointCount = (existing?.pointCount ?: 0) + count,
                lastUpdated = System.currentTimeMillis(),
            )
        }

        if (updatedCells.isNotEmpty()) {
            gridCellDao.insertCells(updatedCells)
        }
    }

    /**
     * Full recompute from all track points. Used from Settings > Data Management.
     */
    suspend fun fullRecompute(
        allPoints: List<TrackPointEntity>,
        gridCellDao: GridCellDao,
    ) {
        gridCellDao.deleteAllCells()

        // Group by cell, count distinct tracks per cell
        data class CellData(val trackIds: MutableSet<String> = mutableSetOf(), var pointCount: Int = 0)
        val cells = mutableMapOf<Long, CellData>()

        for (pt in allPoints) {
            val gLat = GridCellEntity.gridLatFor(pt.lat)
            val gLon = GridCellEntity.gridLonFor(pt.lon)
            val cellId = GridCellEntity.encodeCellId(gLat, gLon)
            val data = cells.getOrPut(cellId) { CellData() }
            data.trackIds.add(pt.trackId)
            data.pointCount++
        }

        val now = System.currentTimeMillis()
        val entities = cells.map { (cellId, data) ->
            // Decode gridLat/gridLon from any point in this cell
            val samplePt = allPoints.first { pt ->
                val gLat = GridCellEntity.gridLatFor(pt.lat)
                val gLon = GridCellEntity.gridLonFor(pt.lon)
                GridCellEntity.encodeCellId(gLat, gLon) == cellId
            }
            GridCellEntity(
                cellId = cellId,
                gridLat = GridCellEntity.gridLatFor(samplePt.lat),
                gridLon = GridCellEntity.gridLonFor(samplePt.lon),
                trackCount = data.trackIds.size,
                pointCount = data.pointCount,
                lastUpdated = now,
            )
        }

        if (entities.isNotEmpty()) {
            // Insert in batches to avoid SQLite variable limit
            entities.chunked(500).forEach { batch ->
                gridCellDao.insertCells(batch)
            }
        }
    }
}
