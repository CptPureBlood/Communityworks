package com.pingai.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.pingai.app.data.Prefs
import com.pingai.app.ui.onboarding.OnboardingScreen
import com.pingai.app.ui.settings.SettingsScreen
import com.pingai.app.ui.theme.PingAiTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val prefs = remember { Prefs(applicationContext) }
            val onboardingDone by prefs.onboardingDone.collectAsState(initial = null)

            PingAiTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (onboardingDone) {
                        null -> {}
                        false -> OnboardingScreen()
                        true -> SettingsScreen()
                    }
                }
            }
        }
    }
}
