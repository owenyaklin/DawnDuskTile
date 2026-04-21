package com.example.dawntodusktile.tile

data class DawnDuskTileState(
    val locationName: String = "Unknown",
    val sunrise: String = "--:--",
    val sunset: String = "--:--",
    val isLoading: Boolean = false,
    val error: String? = null
)
