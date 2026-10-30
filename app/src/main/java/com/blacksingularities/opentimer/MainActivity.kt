package com.blacksingularities.opentimer

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.blacksingularities.opentimer.data.Store
import com.blacksingularities.opentimer.notify.Notifications
import com.blacksingularities.opentimer.ui.AppRoot
import com.blacksingularities.opentimer.ui.AppViewModel
import com.blacksingularities.opentimer.ui.theme.OpenTimerTheme
import com.blacksingularities.opentimer.update.UpdateManager

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val notificationsLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        Notifications.refresh(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        UpdateManager.clearUpdatedNotification(this)
        if (savedInstanceState == null) vm.handle(intent)
        if (savedInstanceState == null && !Store.settings.value.onboarded) {
            Store.updateSettings { it.copy(onboarded = true) }
            requestNotifications()
        }
        setContent {
            OpenTimerTheme {
                AppRoot(vm, onRequestNotifications = ::requestNotifications)
            }
        }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.allowed(this)) {
            notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        vm.checkUpdateIfDue()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vm.handle(intent)
    }

    companion object {
        const val EXTRA_OPEN = "open"
    }
}
