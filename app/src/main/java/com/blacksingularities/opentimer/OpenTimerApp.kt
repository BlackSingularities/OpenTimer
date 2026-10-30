package com.blacksingularities.opentimer

import android.app.Application
import com.blacksingularities.opentimer.core.Actions
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.i18n.I18n
import com.blacksingularities.opentimer.notify.Notifications

class OpenTimerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        I18n.apply(Store.settings.value.language)
        Notifications.createChannels(this)
        // Harmonogram mógł przepaść (aktualizacja aplikacji, wymuszone zatrzymanie) – ustawiamy go od nowa.
        Actions.resync(this)
    }
}
