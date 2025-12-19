package io.github.hcisme.mediasoupclient.client

import android.util.Log
import io.github.hcisme.mediasoupclient.utils.JsonKey
import io.github.hcisme.mediasoupclient.utils.SocketEvent
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class SignalingClient(private val serverUrl: String) {
    companion object {
        private const val TAG = "SignalingClient"
    }

    val jsonFormat = Json { ignoreUnknownKeys = true }
    var socket: Socket? = null
        private set

    fun connect(listener: ConnectListener) {
        socket = IO.socket(serverUrl).apply {
            // 基础连接事件
            on(SocketEvent.CONNECT) { listener.onConnect() }

            // 业务事件监听
            on(SocketEvent.PEER_JOINED) { args ->
                val data = args.firstOrNull() as? JSONObject
                val socketId = data?.optString(JsonKey.SOCKET_ID)
                socketId?.let { listener.onPeerJoined(socketId = it) }
            }

            on(SocketEvent.NEW_PRODUCER) { args ->
                handleEvent<NewProducerResponse>(args) { listener.onNewProducer(data = it) }
            }

            on(SocketEvent.CONSUMER_CLOSED) { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val consumerId = data.optString(JsonKey.CONSUMER_ID)
                listener.onConsumerClosed(consumerId = consumerId)
            }

            on(SocketEvent.PRODUCER_PAUSED) { args ->
                handleEvent<RemotePauseResumeDataResponse>(args) {
                    listener.onProducerPaused(data = it)
                }
            }

            on(SocketEvent.PRODUCER_RESUMED) { args ->
                handleEvent<RemotePauseResumeDataResponse>(args) {
                    listener.onProducerResumed(data = it)
                }

            }

            on(SocketEvent.DISCONNECT) { listener.onDisconnect() }

            on(SocketEvent.PEER_LEAVE) { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val socketId = data.optString(JsonKey.SOCKET_ID)
                listener.onPeerLeave(socketId = socketId)
            }

            on(SocketEvent.ACTIVE_SPEAKER) { args ->
                try {
                    val data = args.firstOrNull() as? JSONArray
                    if (data != null) {
                        val list = mutableListOf<AudioLevelData>()
                        for (i in 0..<data.length()) {
                            val item = data.getJSONObject(i)
                            list.add(
                                AudioLevelData(
                                    audioProducerId = item.getString(JsonKey.AUDIO_PRODUCER_ID),
                                    volume = item.getInt(JsonKey.VOLUME)
                                )
                            )
                        }
                        listener.onActiveSpeaker(volumes = list.toTypedArray())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Parse audio level failed", e)
                }
            }
        }
        socket?.connect()
    }

    suspend inline fun <reified T> suspendSafeEmit(event: String, data: JSONObject? = null): T {
        return withContext(Dispatchers.IO) {
            suspendCoroutine { cont ->
                val args = if (data != null) arrayOf(data) else emptyArray()
                socket?.emit(event, args) { responses ->
                    try {
                        val jsonStr = (responses.firstOrNull() as? JSONObject)?.toString()
                            ?: error("Empty response for $event")
                        val result = jsonFormat.decodeFromString<T>(jsonStr)
                        cont.resume(result)
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                } ?: cont.resumeWithException(IOException("Socket not connected"))
            }
        }
    }

    /**
     * 发送通知 无返回
     */
    fun emit(event: String, data: JSONObject) {
        socket?.emit(event, data)
    }

    /**
     * 断开并关闭连接
     */
    fun disconnect() {
        socket?.apply { disconnect(); off(); }
        socket = null
    }

    /**
     * 序列化处理响应的信息
     */
    private inline fun <reified T> handleEvent(args: Array<Any>, action: (T) -> Unit) {
        try {
            val data = args.firstOrNull() as? JSONObject ?: return
            val result = jsonFormat.decodeFromString<T>(data.toString())
            action(result)
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }
    }

    /**
     * 信令事件监听器
     */
    interface ConnectListener {
        /**
         * 连接成功回调
         */
        fun onConnect()

        /**
         * 断开连接回调
         */
        fun onDisconnect()

        /**
         * 有新人加入房间的回调
         * @param socketId 新用户的 Socket ID
         */
        fun onPeerJoined(socketId: String)

        /**
         * 有人离开房间的回调
         * @param socketId 离开用户的 Socket ID
         */
        fun onPeerLeave(socketId: String)

        /**
         * 有新人发布流回调
         */
        fun onNewProducer(data: NewProducerResponse)

        /**
         * 有人停止消费（流关闭）的回调
         * @param consumerId 被关闭的 Consumer ID
         */
        fun onConsumerClosed(consumerId: String)

        /**
         * 远程流暂停回调（对方关摄像头/麦克风）
         */
        fun onProducerPaused(data: RemotePauseResumeDataResponse)

        /**
         * 远程流恢复回调（对方开摄像头/麦克风）
         */
        fun onProducerResumed(data: RemotePauseResumeDataResponse)

        /**
         * 房间内说话者音量大小回调
         * @param volumes 包含所有正在说话者的 ID 和音量数据的数组
         */
        fun onActiveSpeaker(volumes: Array<AudioLevelData>)
    }
}
