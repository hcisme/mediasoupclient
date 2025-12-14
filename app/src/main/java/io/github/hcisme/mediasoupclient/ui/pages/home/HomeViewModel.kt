package io.github.hcisme.mediasoupclient.ui.pages.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    var roomId by mutableStateOf("865678")
    var isEntering by mutableStateOf(false)
    var isOpenCamera by mutableStateOf(true)
    var isOpenMic by mutableStateOf(true)
}