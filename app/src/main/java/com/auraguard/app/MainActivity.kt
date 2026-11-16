package com.auraguard.app

import android.Manifest
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.AnticipateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.auraguard.app.service.TelemetryService
import com.auraguard.app.ui.navigation.AppNavigation
import com.auraguard.app.ui.theme.AuraGuardTheme
import com.auraguard.app.viewmodel.MainViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val iconView = splashScreenViewProvider.iconView

            // Gmail-style smooth scale & alpha transition using AnticipateInterpolator
            val scaleX = ObjectAnimator.ofFloat(iconView, View.SCALE_X, 1.0f, 0.0f)
            val scaleY = ObjectAnimator.ofFloat(iconView, View.SCALE_Y, 1.0f, 0.0f)
            val alpha = ObjectAnimator.ofFloat(splashScreenViewProvider.view, View.ALPHA, 1.0f, 0.0f)

            scaleX.interpolator = AnticipateInterpolator()
            scaleY.interpolator = AnticipateInterpolator()
            alpha.interpolator = AnticipateInterpolator()

            scaleX.duration = 700L
            scaleY.duration = 700L
            alpha.duration = 700L

            scaleX.doOnEnd { splashScreenViewProvider.remove() }

            scaleX.start()
            scaleY.start()
            alpha.start()
        }

        enableEdgeToEdge()

        setContent {
            AuraGuardTheme {
                val viewModel: MainViewModel = viewModel()

                val permissions = mutableListOf(
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.RECORD_AUDIO,
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }

                val permissionsState = rememberMultiplePermissionsState(permissions = permissions)

                LaunchedEffect(Unit) {
                    if (!permissionsState.allPermissionsGranted) {
                        permissionsState.launchMultiplePermissionRequest()
                    }
                    TelemetryService.startService(this@MainActivity)
                }

                AppNavigation(viewModel = viewModel)
            }
        }
    }
}
