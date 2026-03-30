package com.rallytrax.app.data.export

import com.rallytrax.app.data.local.entity.TrackEntity
import com.rallytrax.app.data.local.entity.VehicleEntity
import com.rallytrax.app.util.formatDateTime
import java.io.OutputStream

object CsvExporter {
    fun export(
        tracks: List<TrackEntity>,
        vehicles: List<VehicleEntity>,
        outputStream: OutputStream,
    ) {
        val writer = outputStream.bufferedWriter()
        writer.write("Name,Date,Distance (m),Duration (s),Avg Speed (m/s),Max Speed (m/s),Elevation Gain (m),Difficulty,Surface,Vehicle,Tags")
        writer.newLine()

        val vehicleMap = vehicles.associate { it.id to "${it.year} ${it.make} ${it.model}" }

        for (track in tracks) {
            val date = formatDateTime(track.recordedAt)
            val vehicle = track.vehicleId?.let { vehicleMap[it] } ?: ""
            writer.write(csvEscape(track.name))
            writer.write(",${csvEscape(date)}")
            writer.write(",${track.distanceMeters}")
            writer.write(",${track.durationMs / 1000}")
            writer.write(",${track.avgSpeedMps}")
            writer.write(",${track.maxSpeedMps}")
            writer.write(",${track.elevationGainM}")
            writer.write(",${csvEscape(track.difficultyRating ?: "")}")
            writer.write(",${csvEscape(track.primarySurface ?: "")}")
            writer.write(",${csvEscape(vehicle)}")
            writer.write(",${csvEscape(track.tags)}")
            writer.newLine()
        }
        writer.flush()
    }

    private fun csvEscape(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
