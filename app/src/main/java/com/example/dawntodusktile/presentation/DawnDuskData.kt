package com.example.dawntodusktile.presentation

data class DawnDuskData(
    val dates: List<DateDawnDusk>,
    val locationName: String,
    val latitude: Double,
    val longitude: Double,
    val lastUpdatedMillis: Long
)
