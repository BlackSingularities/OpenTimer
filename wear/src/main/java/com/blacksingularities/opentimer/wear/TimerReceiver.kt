package com.blacksingularities.opentimer.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Koniec odliczania (alarm systemu) i wyłączenie dzwonienia z powiadomienia. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WearStore.load(context)
        when (intent.action) {
            FIRE -> WearStore.finish(context)
            STOP -> WearStore.stopRinging(context)
        }
    }

    companion object {
        const val FIRE = "com.blacksingularities.opentimer.wear.FIRE"
        const val STOP = "com.blacksingularities.opentimer.wear.STOP"
    }
}
