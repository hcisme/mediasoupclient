package io.github.hcisme.mediasoupclient.client

import android.util.Log
import org.webrtc.MediaStreamTrack

fun MediaStreamTrack?.safeDispose() {
    try {
        this?.dispose()
    } catch (e: Exception) {
        Log.e("Track safeDispose Ext", "Failed to dispose track", e)
    }
}