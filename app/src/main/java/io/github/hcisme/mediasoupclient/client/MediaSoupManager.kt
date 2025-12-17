package io.github.hcisme.mediasoupclient.client

import android.app.Application
import android.content.Context
import android.util.Log
import io.github.crow_misia.mediasoup.Device
import io.github.crow_misia.mediasoup.MediasoupClient
import io.github.crow_misia.mediasoup.RecvTransport
import io.github.crow_misia.mediasoup.SendTransport
import io.github.crow_misia.mediasoup.Transport
import io.github.crow_misia.webrtc.log.DefaultLogHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory

class MediaSoupManager(
    private val context: Context,
    private val eglBaseContext: EglBase.Context
) {
    companion object {
        private const val TAG = "MediaSoupManager"
    }

    private var isMediaSoupInitialized = false

    // WebRTC 工厂
    lateinit var peerConnectionFactory: PeerConnectionFactory
        private set

    // MediaSoup 核心
    private var device: Device? = null
    var sendTransport: SendTransport? = null
        private set
    var recvTransport: RecvTransport? = null
        private set

    // 当 Transport 连接时回调
    var onConnectTransport: ((id: String, dtlsParameters: String) -> Unit)? = null

    // 当 SendTransport 产生数据时回调 (同步阻塞返回 ID)
    var onProduceTransport: ((id: String, kind: String, rtpParameters: String) -> String)? = null

    init {
        intMediaSoupAndWebRTC()
    }

    private fun intMediaSoupAndWebRTC() {
        if (isMediaSoupInitialized) return
        MediasoupClient.initialize(
            context = context.applicationContext as Application,
            logHandler = DefaultLogHandler
        )
        val options =
            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBaseContext))
            .createPeerConnectionFactory()
        isMediaSoupInitialized = true
    }

    /**
     * 加载 Device
     */
    suspend fun loadDevice(rtpCapabilities: String) = withContext(Dispatchers.IO) {
        device = Device(peerConnectionFactory).apply { load(rtpCapabilities) }
    }

    fun getRtpCapabilities(): String = device?.rtpCapabilities ?: ""

    /**
     * 创建发送通道
     */
    fun createSendTransport(
        id: String,
        iceParameters: String,
        iceCandidates: String,
        dtlsParameters: String
    ) {
        sendTransport = device?.createSendTransport(
            sendListener,
            id,
            iceParameters,
            iceCandidates,
            dtlsParameters
        )
    }

    /**
     * 创建接收通道
     */
    fun createReceiveTransport(
        id: String,
        iceParameters: String,
        iceCandidates: String,
        dtlsParameters: String
    ) {
        recvTransport = device?.createRecvTransport(
            receiveListener, id, iceParameters, iceCandidates, dtlsParameters
        )
    }

    // 监听

    private val sendListener = object : SendTransport.Listener {
        override fun onConnect(transport: Transport, dtlsParameters: String) {
            onConnectTransport?.invoke(transport.id, dtlsParameters)
        }

        override fun onProduce(
            transport: Transport,
            kind: String,
            rtpParameters: String,
            appData: String?
        ): String {
            return onProduceTransport?.invoke(transport.id, kind, rtpParameters) ?: ""
        }

        override fun onConnectionStateChange(transport: Transport, newState: String) {
            Log.d(TAG, "Send transport connection state changed: $newState")
        }

        override fun onProduceData(
            transport: Transport,
            sctpStreamParameters: String,
            label: String,
            protocol: String,
            appData: String?
        ): String = ""
    }

    private val receiveListener = object : RecvTransport.Listener {
        override fun onConnect(transport: Transport, dtlsParameters: String) {
            onConnectTransport?.invoke(transport.id, dtlsParameters)
        }

        override fun onConnectionStateChange(transport: Transport, newState: String) {
            Log.d(TAG, "Receive transport connection state changed: $newState")
        }
    }

    /**
     * 销毁资源
     */
    fun dispose() {
        try {
            sendTransport?.dispose()
            recvTransport?.dispose()
            device?.dispose()
        } catch (e: Exception) {
            Log.e(TAG, "Dispose $TAG error", e)
        } finally {
            sendTransport = null
            recvTransport = null
            device = null
        }
    }
}