package io.github.hcisme.mediasoupclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import io.github.hcisme.mediasoupclient.navigation.AppNav
import io.github.hcisme.mediasoupclient.ui.theme.MediaSoupClientTheme
import io.github.hcisme.mediasoupclient.utils.LocalNavController
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestPermissions(
            arrayOf(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
            ),
            1949234
        )

        val roomClient = RoomClient(context = application)

        setContent {
            val navController = rememberNavController()

            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalRoomClient provides roomClient
            ) {
                MediaSoupClientTheme(
                    dynamicColor = false,
                    darkTheme = false
                ) {
                    AppNav()
                }
            }
        }
    }
}
