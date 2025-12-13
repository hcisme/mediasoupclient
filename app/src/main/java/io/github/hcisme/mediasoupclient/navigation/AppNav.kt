package io.github.hcisme.mediasoupclient.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import io.github.hcisme.mediasoupclient.pages.home.HomePage
import io.github.hcisme.mediasoupclient.pages.room.RoomPage
import io.github.hcisme.mediasoupclient.ui.theme.MediaSoupClientTheme
import io.github.hcisme.mediasoupclient.utils.LocalNavController

@Composable
fun AppNav() {
    val navController = LocalNavController.current

    NavHost(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        navController = navController,
        startDestination = NavRouteConstant.DEFAULT_ROUTE
    ) {
        composable(NavRouteConstant.HOME) {
            HomePage()
        }

        composable(
            route = "${NavRouteConstant.ROOM}/{roomId}?cam={cam}&mic={mic}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("cam") { type = NavType.BoolType },
                navArgument("mic") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
                ?: error("Nav Param roomId not be null")
            val isOpenCamera = backStackEntry.arguments?.getBoolean("cam") ?: false
            val isOpenMic = backStackEntry.arguments?.getBoolean("mic") ?: false

            MediaSoupClientTheme(
                dynamicColor = false,
                darkTheme = true
            ) {
                RoomPage(roomId = roomId, isOpenCamera = isOpenCamera, isOpenMic = isOpenMic)
            }
        }
    }
}