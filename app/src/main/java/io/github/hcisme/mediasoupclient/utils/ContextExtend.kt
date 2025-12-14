package io.github.hcisme.mediasoupclient.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * 去设置中的应用界面
 */
fun Context.startSettingActivity(tooltip: String) {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", this@startSettingActivity.packageName, null)
        }
    )
    Toast.makeText(this, tooltip, Toast.LENGTH_LONG).show()
}
