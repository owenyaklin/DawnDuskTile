package com.example.dawntodusktile.tile

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class DawnDuskRepository(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun updateTileData(dataStore: DawnDuskTileDataStore) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Get Location
                val locationTask = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                val location = Tasks.await(locationTask, 10, TimeUnit.SECONDS)

                if (location != null) {
                    // 2. Make API Call (Example: Sunrise-Sunset API)
                    val url = URL("https://api.sunrise-sunset.org/json?lat=${location.latitude}&lng=${location.longitude}&formatted=0")
                    val connection = url.openConnection() as HttpURLConnection
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    
                    val json = JSONObject(response)
                    val results = json.getJSONObject("results")
                    val sunrise = results.getString("sunrise")
                    val sunset = results.getString("sunset")

                    // 3. Update DataStore
                    dataStore.updateState(
                        DawnDuskTileState(
                            locationName = "My Location", // In a real app, use Geocoder
                            sunrise = formatTime(sunrise),
                            sunset = formatTime(sunset)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                dataStore.updateState(DawnDuskTileState(error = e.message))
            }
        }
    }

    private fun formatTime(isoTime: String): String {
        // Simple extraction for demonstration
        // "2023-10-27T12:34:56+00:00" -> "12:34"
        return try {
            isoTime.split("T")[1].substring(0, 5)
        } catch (e: Exception) {
            isoTime
        }
    }
}
