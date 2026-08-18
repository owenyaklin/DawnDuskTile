package com.example.dawntodusktile.presentation.tile

import com.example.dawntodusktile.presentation.DateDawnDusk

data class DawnDuskTileState(
    val dawnDuskDates: List<DateDawnDusk>,
    val locationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val lastUpdatedMillis: Long = 0L
)