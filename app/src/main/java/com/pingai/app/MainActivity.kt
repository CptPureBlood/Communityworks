package com.pingai.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import com.pingai.app.ui.common.BottomNav
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.pingai.app.core.LocaleHelper
import com.pingai.app.data.Prefs
import com.pingai.app.ui.main.HomeScreen
import com.pingai.app.ui.main.AchievementsScreen
import com.pingai.app.ui.main.SettingsScreen
import com.pingai.app.ui.main.SavedScreen
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pingai.app.ui.onboarding.OnboardingScreen
import com.pingai.app.ui.theme.PingAiTheme
import com.pingai.app.ui.theme.WinColors
import com.pingai.app.work.WorkScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onStart() {
        super.onStart()
        com.pingai.app.ads.AdGate.onStart(this)
    }

    override fun onStop() {
        super.onStop()
        com.pingai.app.ads.AdGate.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        com.pingai.app.ads.AdGate.requestConsent(this)

        lifecycleScope.launch {
            if (Prefs(applicationContext).onboardingDone.first()) {
                WorkScheduler.ensureScheduled(applicationContext)
            }
        }

        setContent {
            val prefs = remember { Prefs(applicationContext) }
            val onboardingDone by prefs.onboardingDone.collectAsState(initial = null)
            var page by remember { mutableStateOf("home") }
            BackHandler(enabled = page != "home") { page = "home" }
            val appThemeId by prefs.appTheme.collectAsState(initial = "navy")
            val isProNow by prefs.isPro.collectAsState(initial = false)
            androidx.compose.runtime.SideEffect {
                WinColors.current = com.pingai.app.ui.theme.AppThemes.byId(appThemeId, isProNow)
            }
            PingAiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = WinColors.Bg) {
                    when (onboardingDone) {
                        null -> {}
                        false -> OnboardingScreen()
                        true -> Column(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                androidx.compose.animation.Crossfade(
                                    targetState = page,
                                    animationSpec = androidx.compose.animation.core.tween(220),
                                    label = "page"
                                ) { p ->
                                    when (p) {
                                        "saved" -> SavedScreen { page = "home" }
                                        "ach" -> AchievementsScreen { page = "home" }
                                        "settings" -> SettingsScreen { page = "home" }
                                        else -> HomeScreen()
                                    }
                                }
                            }
                            BottomNav(page) { page = it }
                        }
                    }
                }
            }
        }
    }
}
