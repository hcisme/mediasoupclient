package io.github.hcisme.mediasoupclient.navigation

import androidx.navigation.NavHostController

fun NavHostController.navigationToRoom(roomId: String) {
    navigate("${NavRouteConstant.ROOM}/$roomId")
}
