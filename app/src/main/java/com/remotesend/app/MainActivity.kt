package com.remotesend.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.remotesend.app.data.service.RemoteSendService
import com.remotesend.app.domain.model.AppSettings
import com.remotesend.app.domain.model.FrameRateMode
import com.remotesend.app.domain.model.ThemeMode
import com.remotesend.app.ui.LocalAppContainer
import com.remotesend.app.ui.RemoteSendRoot
import com.remotesend.app.ui.theme.RemoteSendTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RemoteSendApp).container

        lifecycleScope.launch {
            val state = container.stateStore.snapshot()
            if (state.server.autoStart && !container.serverManager.isRunning) {
                runCatching { RemoteSendService.startServer(this@MainActivity) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                state.settings.notificationsEnabled &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            val settings by container.settingsRepository.settings
                .collectAsStateWithLifecycle(initialValue = AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(settings.frameRateMode) {
                applyFrameRate(settings.frameRateMode)
            }
            val view = LocalView.current
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }

            RemoteSendTheme(
                darkTheme = dark,
                dynamicColor = settings.dynamicColor,
            ) {
                CompositionLocalProvider(LocalAppContainer provides container) {
                    RemoteSendRoot()
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun applyFrameRate(mode: FrameRateMode) {
        val attributes = window.attributes
        when (mode) {
            FrameRateMode.AUTO -> {
                attributes.preferredRefreshRate = 0f
                attributes.preferredDisplayModeId = 0
            }

            FrameRateMode.FPS_60 -> applyFrameRateTarget(attributes, 60f)
            FrameRateMode.FPS_120 -> applyFrameRateTarget(attributes, 120f)
        }
        window.attributes = attributes
    }

    @Suppress("DEPRECATION")
    private fun applyFrameRateTarget(
        attributes: android.view.WindowManager.LayoutParams,
        target: Float,
    ) {
        val currentDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        } ?: return
        val modes = currentDisplay.supportedModes
        if (modes.isEmpty()) return
        val best = modes
            .filter { it.refreshRate >= target - 0.5f }
            .minByOrNull { it.refreshRate }
            ?: modes.maxByOrNull { it.refreshRate }
            ?: return
        attributes.preferredDisplayModeId = best.modeId
        attributes.preferredRefreshRate = best.refreshRate
    }
}
