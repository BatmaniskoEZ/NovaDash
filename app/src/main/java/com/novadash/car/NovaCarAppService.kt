package com.novadash.car

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** Entry point for the Android Auto car app. Personal/sideloaded use only (category-gated). */
class NovaCarAppService : CarAppService() {

    // Allow all hosts — fine for a sideloaded personal app. For distribution, restrict this.
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = NovaCarSession()
}
