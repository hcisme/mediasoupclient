package io.github.hcisme.mediasoupclient.utils

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavHostController
import io.github.hcisme.mediasoupclient.RoomClient

val LocalNavController = compositionLocalOf<NavHostController> { error("No NavController found!") }

val LocalRoomClient = compositionLocalOf<RoomClient> { error("No RoomClient found!") }
