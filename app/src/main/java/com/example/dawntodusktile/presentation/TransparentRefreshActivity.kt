package com.example.dawntodusktile.presentation

import android.app.Activity
import android.os.Bundle
import android.util.Log

class TransparentRefreshActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("RefreshActivity", "Forced refresh requested from Tile")
        SolarDataUtils.forceRefresh(this)
        finish()
    }
}
