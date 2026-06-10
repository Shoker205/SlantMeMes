package com.example

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import com.example.ui.theme.MyApplicationTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        PresenceManager.onAppForeground()
    }

    override fun onPause() {
        super.onPause()
        PresenceManager.onAppBackground()
    }

  @OptIn(ExperimentalPermissionsApi::class)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    AppPreferences.init(applicationContext)
    
    // Временно отключен FLAG_SECURE для работы потокового эмулятора AI Studio.
    enableEdgeToEdge()
    setContent {
      // Request notification permission on Android 13+
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          val permissionState = rememberPermissionState(android.Manifest.permission.POST_NOTIFICATIONS)
          LaunchedEffect(Unit) {
              if (!permissionState.status.isGranted) {
                  permissionState.launchPermissionRequest()
              }
          }
      }

      LaunchedEffect(Unit) {
          PushNotificationManager.init(applicationContext)
          val intent = android.content.Intent(applicationContext, ChatRealtimeService::class.java)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
              applicationContext.startForegroundService(intent)
          } else {
              applicationContext.startService(intent)
          }
      }

      val isDarkTheme by AppPreferences.isDarkTheme.collectAsState()
      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    PresenceManager.markActive()
                }
            }
        }, color = MaterialTheme.colorScheme.background) {
            MainAppNavigation()
        }
      }
    }
  }
}
