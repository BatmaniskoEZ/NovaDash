package com.novadash.car

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session

class NovaCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen = CarMomentsScreen(carContext)
}
