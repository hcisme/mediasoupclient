package io.github.hcisme.mediasoupclient.pages.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.hcisme.mediasoupclient.R
import io.github.hcisme.mediasoupclient.components.RotationIcon
import io.github.hcisme.mediasoupclient.navigation.navigationToRoom
import io.github.hcisme.mediasoupclient.utils.LocalNavController
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient

@Composable
fun HomePage() {
    val navHostController = LocalNavController.current
    val roomClient = LocalRoomClient.current
    var isEntering by remember { mutableStateOf(false) }
    var roomId by remember { mutableStateOf("12345") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(value = roomId, onValueChange = { roomId = it }, label = { Text("房间 ID") })

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                isEntering = true
                roomClient.connectToRoom(
                    roomId = roomId,
                    onSuccess = {
                        isEntering = false
                        navHostController.navigationToRoom(roomId = roomId)
                    },
                    onError = {
                        isEntering = false
                    }
                )
            },
            shape = MaterialTheme.shapes.small,
            enabled = !isEntering
        ) {
            Text("进入房间")
            Spacer(modifier = Modifier.width(8.dp))
            if (isEntering) {
                RotationIcon(painter = painterResource(R.drawable.loading_circle))
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}