package io.github.hcisme.mediasoupclient.client

import android.util.Log
import io.github.hcisme.mediasoupclient.model.NewProducerResponse
import io.github.hcisme.mediasoupclient.model.RemotePauseResumeDataResponse
import io.github.hcisme.mediasoupclient.utils.JsonKey
import io.github.hcisme.mediasoupclient.utils.SocketEvent
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
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

    /**
     * 连接服务器
     * @param onConnect 连接成功回调
     * @param onDisconnect 断开连接回调
     * @param onPeerJoined 有人加入的回调
     * @param onPeerLeave 有人断开连接的回调
     * @param onNewProducer 有新人发布流回调 (data)
     * @param onConsumerClosed 有人停止消费回调 (consumerId)
     * @param onProducerPaused 远程暂停回调 (producerId, kind, socketId)
     * @param onProducerResumed 远程恢复回调 (producerId, kind, socketId)
     * @param onProducerScore 网络质量回调 (producerId, score)
     */
    fun connect(
        onConnect: () -> Unit,
        onDisconnect: () -> Unit,
        onPeerJoined: (String) -> Unit,
        onPeerLeave: (String) -> Unit,
        onNewProducer: (data: NewProducerResponse) -> Unit,
        onConsumerClosed: (consumerId: String) -> Unit,
        onProducerPaused: (data: RemotePauseResumeDataResponse) -> Unit,
        onProducerResumed: (data: RemotePauseResumeDataResponse) -> Unit,
        onProducerScore: (producerId: String, sore: Int) -> Unit
    ) {
        socket = IO.socket(serverUrl).apply {
            // 基础连接事件
            on(SocketEvent.CONNECT) { onConnect() }

            // 业务事件监听
            on(SocketEvent.PEER_JOINED) { args ->
                val data = args.firstOrNull() as? JSONObject
                val socketId = data?.optString(JsonKey.SOCKET_ID)
                socketId?.let { onPeerJoined(it) }
            }

            on(SocketEvent.NEW_PRODUCER) { args ->
                handleEvent<NewProducerResponse>(args) { onNewProducer(it) }
            }

            on(SocketEvent.CONSUMER_CLOSED) { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val consumerId = data.optString(JsonKey.CONSUMER_ID)
                onConsumerClosed(consumerId)
            }

            on(SocketEvent.PRODUCER_PAUSED) { args ->
                handleEvent<RemotePauseResumeDataResponse>(args) {
                    onProducerPaused(it)
                }
            }

            on(SocketEvent.PRODUCER_RESUMED) { args ->
                handleEvent<RemotePauseResumeDataResponse>(args) {
                    onProducerResumed(it)
                }

            }

            on(SocketEvent.PRODUCER_SCORE) { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val producerId = data.optString(JsonKey.PRODUCER_ID)
                val scores = data.optJSONArray(JsonKey.SCORE)
                if (scores != null && scores.length() > 0) {
                    val scoreVal = scores.getJSONObject(0).optInt(JsonKey.SCORE, 0)
                    onProducerScore(producerId, scoreVal)
                }
            }

            on(SocketEvent.DISCONNECT) { onDisconnect() }

            on(SocketEvent.PEER_LEAVE) { args ->
                val data = args.firstOrNull() as? JSONObject ?: return@on
                val socketId = data.optString(JsonKey.SOCKET_ID)
                onPeerLeave(socketId)
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
}
