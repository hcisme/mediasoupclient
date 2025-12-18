package io.github.hcisme.mediasoupclient.ui.pages.room

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class RoomViewModel : ViewModel() {
    var focusedId by mutableStateOf<String?>(null)

    // 退出页面的提示框
    var backDialogVisible by mutableStateOf(false)

    // 权限悬浮窗
    var audioDialogVisible by mutableStateOf(false)
    var videoDialogVisible by mutableStateOf(false)
}