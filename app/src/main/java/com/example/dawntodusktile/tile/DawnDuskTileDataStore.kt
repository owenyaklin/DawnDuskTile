package com.example.dawntodusktile.tile

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tile_state")

class DawnDuskTileDataStore(private val context: Context) {
    private val locationNameKey = stringPreferencesKey("location_name")
    private val sunriseKey = stringPreferencesKey("sunrise")
    private val sunsetKey = stringPreferencesKey("sunset")

    val tileStateFlow: Flow<DawnDuskTileState> = context.dataStore.data.map { preferences ->
        DawnDuskTileState(
            locationName = preferences[locationNameKey] ?: "Unknown",
            sunrise = preferences[sunriseKey] ?: "--:--",
            sunset = preferences[sunsetKey] ?: "--:--"
        )
    }

    suspend fun updateState(state: DawnDuskTileState) {
        context.dataStore.edit { preferences ->
            preferences[locationNameKey] = state.locationName
            preferences[sunriseKey] = state.sunrise
            preferences[sunsetKey] = state.sunset
        }
    }
}
