package io.github.hcisme.mediasoupclient.ui.pages.room

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.hcisme.mediasoupclient.utils.LocalRoomClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomTopBar(modifier: Modifier = Modifier) {
    val roomClient = LocalRoomClient.current
    val roomId by roomClient.currentRoomId.collectAsState()
    val availableAudioDevices by roomClient.audioController.availableAudioDevices.collectAsState()
    val currentAudioDevice by roomClient.audioController.currentAudioDevice.collectAsState()
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier,
        title = { roomId?.let { Text(text = it) } },
        actions = {
            Box {
                IconButton(
                    onClick = {
                        roomClient.audioController.updateAvailableDevices()
                        showMenu = true
                    }
                ) {
                    Icon(
                        painter = painterResource(currentAudioDevice.iconRes),
                        contentDescription = "Audio Output"
                    )
                }

                // 下拉菜单
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    availableAudioDevices.forEach { device ->
                        DropdownMenuItem(
                            onClick = {
                                roomClient.audioController.switchAudioDevice(type = device)
                                showMenu = false
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        painter = painterResource(device.iconRes),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )

                                    Spacer(Modifier.width(8.dp))

                                    Text(device.label)

                                    if (device == currentAudioDevice) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    )
}