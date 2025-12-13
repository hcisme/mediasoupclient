package io.github.hcisme.mediasoupclient.navigation

import androidx.navigation.NavHostController

fun NavHostController.navigationToRoom(roomId: String, cam: Boolean, mic: Boolean) {
    navigate("${NavRouteConstant.ROOM}/$roomId?cam=${cam}&mic=${mic}")
}
