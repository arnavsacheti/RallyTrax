package com.rallytrax.app.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/** Description of a polyline to draw on the map. */
data class OsmPolylineData(
    val points: List<GeoPoint>,
    val color: Color = Color(0xFF1A73E8),
    val width: Float = 12f,
)

/** Description of a marker to place on the map. */
data class OsmMarkerData(
    val position: GeoPoint,
    val title: String = "",
    val hue: Float = 0f, // 0–360, similar to BitmapDescriptorFactory hues
    val alpha: Float = 1f,
    val icon: android.graphics.Bitmap? = null, // custom icon bitmap (e.g. rally pace note icon)
)

/**
 * A Compose wrapper around osmdroid's [MapView] that displays OpenStreetMap tiles
 * with optional polylines and markers.
 *
 * @param modifier           Layout modifier.
 * @param centerLat           Centre latitude.
 * @param centerLng           Centre longitude.
 * @param zoom                Initial zoom level (1–20).
 * @param polylines           Polylines to draw.
 * @param markers             Markers to place.
 * @param darkMode            Invert tile colours for a dark-map look.
 * @param zoomControlsEnabled Show +/- zoom buttons.
 * @param scrollEnabled       Allow panning.
 * @param fitBounds           If non-null, camera fits this bounding box instead of using center+zoom.
 * @param followPosition      If non-null, camera continuously follows this position.
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    centerLat: Double = 0.0,
    centerLng: Double = 0.0,
    zoom: Double = 15.0,
    polylines: List<OsmPolylineData> = emptyList(),
    markers: List<OsmMarkerData> = emptyList(),
    darkMode: Boolean = false,
    zoomControlsEnabled: Boolean = false,
    scrollEnabled: Boolean = true,
    fitBounds: BoundingBox? = null,
    followPosition: GeoPoint? = null,
) {
    val context = LocalContext.current

    // Configure osmdroid once
    remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
        }
    }

    val mapViewRef = remember { MapView(context) }

    // Clean up when leaving composition
    DisposableEffect(Unit) {
        onDispose {
            mapViewRef.onDetach()
        }
    }

    AndroidView(
        factory = {
            mapViewRef.apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                isTilesScaledToDpi = true
                controller.setZoom(zoom)
                controller.setCenter(GeoPoint(centerLat, centerLng))
                @Suppress("DEPRECATION")
                setBuiltInZoomControls(zoomControlsEnabled)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false

                if (darkMode) {
                    applyDarkFilter(this)
                }
            }
        },
        modifier = modifier,
        update = { mapView ->
            // Update scroll ability
            mapView.setScrollableAreaLimitLatitude(
                MapView.getTileSystem().maxLatitude,
                MapView.getTileSystem().minLatitude,
                0,
            )

            // Enable/disable scrolling
            if (!scrollEnabled) {
                mapView.setScrollableAreaLimitDouble(mapView.boundingBox)
            }

            // Dark mode filter
            if (darkMode) {
                applyDarkFilter(mapView)
            } else {
                mapView.overlayManager.tilesOverlay?.setColorFilter(null)
            }

            // Clear existing overlays (keep tile overlay)
            mapView.overlays.clear()

            // Add polylines
            for (polyData in polylines) {
                if (polyData.points.size >= 2) {
                    val osmPolyline = Polyline(mapView).apply {
                        setPoints(polyData.points)
                        outlinePaint.color = polyData.color.toArgb()
                        outlinePaint.strokeWidth = polyData.width * context.resources.displayMetrics.density
                    }
                    mapView.overlays.add(osmPolyline)
                }
            }

            // Add markers
            for (markerData in markers) {
                val osmMarker = Marker(mapView).apply {
                    position = markerData.position
                    title = markerData.title
                    alpha = markerData.alpha
                    if (markerData.icon != null) {
                        icon = android.graphics.drawable.BitmapDrawable(context.resources, markerData.icon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    } else {
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                }
                mapView.overlays.add(osmMarker)
            }

            // Camera: follow position → fit bounds → center+zoom
            when {
                followPosition != null -> {
                    mapView.controller.animateTo(followPosition, 17.0, 500L)
                }
                fitBounds != null -> {
                    mapView.zoomToBoundingBox(fitBounds, false, 64)
                }
                else -> {
                    mapView.controller.setCenter(GeoPoint(centerLat, centerLng))
                    mapView.controller.setZoom(zoom)
                }
            }

            mapView.invalidate()
        },
    )
}

/** Apply an inverted colour matrix for a dark-mode tile appearance. */
private fun applyDarkFilter(mapView: MapView) {
    val invertMatrix = ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    // Desaturate slightly after inversion for a nicer dark look
    val desaturate = ColorMatrix()
    desaturate.setSaturation(0.3f)
    invertMatrix.postConcat(desaturate)
    mapView.overlayManager.tilesOverlay?.setColorFilter(ColorMatrixColorFilter(invertMatrix))
}
