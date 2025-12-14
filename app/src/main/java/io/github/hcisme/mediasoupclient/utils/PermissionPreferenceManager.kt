package io.github.hcisme.mediasoupclient.utils

import android.content.Context
import androidx.core.content.edit

object PermissionPreferenceManager {
    private const val PREFS_NAME = "app_permission_prefs"

    /**
     * 获取是否是第一次请求权限
     */
    fun isFirstRequestPermission(context: Context, permissionType: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return !prefs.contains(permissionType)
    }

    /**
     * 设置第一次请求权限完毕
     */
    fun setFirstRequestComplete(context: Context, permissionType: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                putBoolean(permissionType, true)
            }
    }

    fun managePermissionRequestFlow(
        context: Context,
        permissionType: String,
        onFirstRequest: () -> Unit = {},
        onFollowUpRequest: () -> Unit = {}
    ) {
        val isFirstRequest = isFirstRequestPermission(context, permissionType)

        if (isFirstRequest) {
            onFirstRequest()
            setFirstRequestComplete(context, permissionType)
        } else {
            onFollowUpRequest()
        }
    }
}
