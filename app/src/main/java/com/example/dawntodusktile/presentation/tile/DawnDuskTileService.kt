package com.example.dawntodusktile.presentation.tile

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.example.dawntodusktile.presentation.DateDawnDusk
import com.example.dawntodusktile.presentation.DawnDuskRepo
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

private const val RESOURCES_VERSION = "0"

@OptIn(ExperimentalHorologistApi::class)
class DawnDuskTileService : SuspendingTileService() {
    private lateinit var repo: DawnDuskRepo
    private lateinit var renderer: DawnDuskTileRenderer
    private lateinit var tileStateFlow: StateFlow<DawnDuskTileState?>
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        repo = DawnDuskRepo(this)
        renderer = DawnDuskTileRenderer(this)
        tileStateFlow = repo.getInitialValues().map { data ->
            DawnDuskTileState(
                data.dates,
                data.locationName,
                data.latitude,
                data.longitude,
                data.lastUpdatedMillis
            )
        }.stateIn(
            lifecycleScope, started = SharingStarted.Eagerly, initialValue = null
        )
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        Log.d(TAG, "tileRequest")
        val tileState = latestTileState()
        return renderer.renderTimeline(tileState, requestParams)
    }

    private suspend fun latestTileState(): DawnDuskTileState {
        Log.d(TAG, "latestTileState")
        val now = System.currentTimeMillis()
        val today = LocalDate.now().toString()

        // 1. Try to get current state from flow quickly.
        val tileState = withTimeoutOrNull(500.milliseconds) {
            tileStateFlow.filterNotNull().first()
        }

        val result = tileState ?: DawnDuskTileState(emptyList())

        // 2. Check if we need to refresh
        // Date check: We want today's data to be the middle element of our 3-day window
        val isDateStale = result.dawnDuskDates.size < 3 || result.dawnDuskDates[1].date != today
        
        // Time check: Data is more than 4 hours old
        val isTimeStale = (now - result.lastUpdatedMillis) > 4 * 60 * 60 * 1000L
        
        // Distance check: Check if system's cached location is > 50km from stored location
        val isDistanceStale = result.lastUpdatedMillis > 0 && checkDistanceStale(result.latitude, result.longitude)

        val isStale = isDateStale || isTimeStale || isDistanceStale

        if (isStale) {
            Log.d(TAG, "Data is stale (date=$isDateStale, time=$isTimeStale, dist=$isDistanceStale). Triggering background refresh...")
            // 3. Trigger refresh in background to avoid blocking the tile response.
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val location = fetchLocation()
                    if (location != null) {
                        refreshData(location)
                        // 4. Request an update so the tile shows the new data once saved
                        getUpdater(this@DawnDuskTileService).requestUpdate(DawnDuskTileService::class.java)
                    } else {
                        Log.w(TAG, "Could not fetch location for refresh")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Background refresh failed", e)
                }
            }
        }

        return result
    }

    private suspend fun checkDistanceStale(storedLat: Double, storedLng: Double): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            val lastLocation = suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }
            
            if (lastLocation != null) {
                val results = FloatArray(1)
                Location.distanceBetween(storedLat, storedLng, lastLocation.latitude, lastLocation.longitude, results)
                val distanceInMeters = results[0]
                Log.d(TAG, "Distance from stored location: ${distanceInMeters / 1000}km")
                distanceInMeters > 50_000 // 50km threshold
            } else {
                false
            }
        } catch (ignore: Exception) {
            false
        }
    }

    private suspend fun fetchLocation(): Location? {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Location permission not granted")
            return null
        }

        // 1. Try to get the Last Known Location first (Low Power)
        val lastLocation = try {
            suspendCancellableCoroutine<Location?> { continuation ->
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    continuation.resume(location)
                }.addOnFailureListener {
                    continuation.resume(null)
                }
            }
        } catch (ignore: Exception) {
            null
        }

        // If lastLocation is "fresh enough" (e.g., within 30 minutes), use it to save battery
        if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 30 * 60 * 1000) {
            Log.d(TAG, "Using fresh lastLocation: ${lastLocation.latitude}")
            return lastLocation
        }

        // 2. If no fresh lastLocation, request a "Balanced" current location (Medium Power)
        Log.d(TAG, "No fresh lastLocation, requesting balanced current location...")
        return withTimeoutOrNull(10000.milliseconds) {
            suspendCancellableCoroutine { continuation ->
                val cancellationTokenSource = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationTokenSource.token
                ).addOnSuccessListener { location ->
                    Log.d(TAG, "Balanced location fetched: $location")
                    if (continuation.isActive) continuation.resume(location)
                }.addOnFailureListener { exception ->
                    Log.e(TAG, "Failed to get balanced location", exception)
                    if (continuation.isActive) continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
            }
        }
    }

    private suspend fun fetchLocationName(location: Location): String? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://geocoding.geo.census.gov/geocoder/geographies/coordinates?x=${location.longitude}&y=${location.latitude}&format=json&benchmark=2020&vintage=2020&layers=County+Subdivisions"
            Log.d(TAG, "Fetching location name from: $urlString")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val result = json.getJSONObject("result")
                val geographies = result.getJSONObject("geographies")
                val countySubdivisions = geographies.getJSONArray("County Subdivisions")
                if (countySubdivisions.length() > 0) {
                    val basename = countySubdivisions.getJSONObject(0).getString("BASENAME")
                    Log.d(TAG, "Location name fetched: $basename")
                    return@withContext basename
                }
            } else {
                Log.e(TAG, "Geocoding API error: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch location name", e)
        }
        null
    }

    private suspend fun fetchDawnDuskDates(location: Location): List<DateDawnDusk>? = withContext(Dispatchers.IO) {
        try {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            val tomorrow = today.plusDays(1)
            
            val urlString = "https://api.sunrisesunset.io/json?lat=${location.latitude}&lng=${location.longitude}&date_start=$yesterday&date_end=$tomorrow&time_format=24"
            Log.d(TAG, "Fetching dawn/dusk data from: $urlString")
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val results = json.getJSONArray("results")
                val dawnDuskList = mutableListOf<DateDawnDusk>()
                
                for (i in 0 until results.length()) {
                    val dayData = results.getJSONObject(i)
                    dawnDuskList.add(
                        DateDawnDusk(
                            date = dayData.getString("date"),
                            sunrise = dayData.getString("sunrise"),
                            sunset = dayData.getString("sunset"),
                            dawn = dayData.getString("dawn"),
                            dusk = dayData.getString("dusk")
                        )
                    )
                }
                Log.d(TAG, "Dawn/dusk data fetched for ${dawnDuskList.size} days")
                return@withContext dawnDuskList
            } else {
                Log.e(TAG, "Dawn/dusk API error: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch dawn/dusk data", e)
        }
        null
    }

    private suspend fun refreshData(location: Location) {
        Log.d(TAG, "Refreshing data for location: ${location.latitude}, ${location.longitude}")
        val locationName = fetchLocationName(location) ?: "Unknown Location"
        val dawnDuskDates = fetchDawnDuskDates(location) ?: DawnDuskRepo.currentDates
        
        repo.updateDateDawnDusk(
            dawnDuskDates,
            locationName,
            location.latitude,
            location.longitude,
            System.currentTimeMillis()
        )
    }

    override suspend fun resourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ResourceBuilders.Resources {
        return ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    companion object {
        const val TAG = "DawnDuskTileService"
    }
}
