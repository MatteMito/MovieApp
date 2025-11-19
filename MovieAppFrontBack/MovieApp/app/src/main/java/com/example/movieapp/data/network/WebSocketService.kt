package com.example.movieapp.data.network

import android.util.Log
import com.example.movieapp.config.AppConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Servizio WebSocket per notifiche real-time
 */
class WebSocketService private constructor() {

    private val TAG = "WebSocketService"

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)

    private val _enrichmentUpdates = MutableStateFlow<EnrichmentUpdate?>(null)
    val enrichmentUpdates: StateFlow<EnrichmentUpdate?> = _enrichmentUpdates.asStateFlow()

    private var socket: Socket? = null
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5

    companion object {
        @Volatile
        private var INSTANCE: WebSocketService? = null

        fun getInstance(): WebSocketService {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebSocketService().also { INSTANCE = it }
            }
        }
    }

    init {
        Log.d(TAG, "🔌 WebSocket Service inizializzato")
        Log.d(TAG, "Backend: ${AppConfig.WEBSOCKET_URL}${AppConfig.WEBSOCKET_NAMESPACE}")
    }

    /**
     * Connette al backend WebSocket con namespace corretto
     */
    fun connect() {
        if (socket != null && socket!!.connected()) {
            Log.d(TAG, "⚠️ WebSocket già connesso")
            return
        }

        val fullUrl = "${AppConfig.WEBSOCKET_URL}${AppConfig.WEBSOCKET_NAMESPACE}"

        Log.d(TAG, "🔌 Connessione a $fullUrl")
        _connectionStatus.value = ConnectionStatus.CONNECTING

        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = maxReconnectAttempts
                reconnectionDelay = 2000
                timeout = 10000
                transports = arrayOf("websocket", "polling")
            }

            socket = IO.socket(fullUrl, opts)
            setupSocketListeners()
            socket?.connect()

            Log.d(TAG, "✅ Socket.IO connect() chiamato")

        } catch (e: URISyntaxException) {
            Log.e(TAG, "❌ Errore URI WebSocket: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore connessione WebSocket: ${e.message}", e)
            _connectionStatus.value = ConnectionStatus.ERROR
        }
    }

    /**
     * Setup listeners per eventi WebSocket
     */
    private fun setupSocketListeners() {
        socket?.apply {
            //EVENTI CONNESSIONE
            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "✅ WebSocket CONNESSO!")
                _connectionStatus.value = ConnectionStatus.CONNECTED
                reconnectAttempts = 0
            }

            on(Socket.EVENT_DISCONNECT) { args ->
                val reason = args.firstOrNull()?.toString() ?: "unknown"
                Log.w(TAG, "❌ WebSocket DISCONNESSO: $reason")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown error"
                Log.e(TAG, "❌ ERRORE CONNESSIONE: $error")
                _connectionStatus.value = ConnectionStatus.ERROR
                reconnectAttempts++
            }

            //EVENTO BENVENUTO
            on("connection") { args ->
                try {
                    val data = args[0] as JSONObject
                    val message = data.getString("message")
                    val clientId = data.optString("clientId", "unknown")

                    Log.d(TAG, "💬 Backend: $message (client: $clientId)")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing connection message", e)
                }
            }

            //EVENTI ENRICHMENT
            on("enrichment:progress") { args ->
                try {
                    val data = args[0] as JSONObject
                    val sessionId = data.getString("sessionId")
                    val processed = data.getInt("processed")
                    val total = data.getInt("total")
                    val percentage = data.getInt("percentage")
                    val message = data.getString("message")
                    val currentMovie = data.optString("currentMovie", "")

                    val update = EnrichmentUpdate(
                        sessionId = sessionId,
                        type = "progress",
                        total = total,
                        processed = processed,
                        currentMovie = currentMovie,
                        message = message,
                        percentage = percentage
                    )

                    _enrichmentUpdates.value = update

                    Log.d(TAG, "📊 PROGRESS: $processed/$total ($percentage%) - $currentMovie")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Errore parsing progress", e)
                }
            }

            on("enrichment:completed") { args ->
                try {
                    val data = args[0] as JSONObject
                    val sessionId = data.getString("sessionId")
                    val total = data.getInt("total")
                    val successful = data.optInt("successful", total)
                    val message = data.getString("message")

                    val update = EnrichmentUpdate(
                        sessionId = sessionId,
                        type = "completed",
                        total = total,
                        processed = total,
                        currentMovie = "",
                        message = message,
                        percentage = 100
                    )

                    _enrichmentUpdates.value = update

                    Log.d(TAG, "✅ COMPLETATO: $successful/$total film")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Errore parsing completed", e)
                }
            }

            on("enrichment:error") { args ->
                try {
                    val data = args[0] as JSONObject
                    val sessionId = data.getString("sessionId")
                    val error = data.optString("error", "unknown error")
                    val message = data.getString("message")

                    val update = EnrichmentUpdate(
                        sessionId = sessionId,
                        type = "error",
                        total = 0,
                        processed = 0,
                        currentMovie = "",
                        message = message,
                        percentage = 0
                    )

                    _enrichmentUpdates.value = update

                    Log.e(TAG, "❌ ERRORE ENRICHMENT: $error")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Errore parsing error", e)
                }
            }

            //EVENTI DEBUG
            on("error") { args ->
                val error = args.firstOrNull()?.toString() ?: "unknown"
                Log.e(TAG, "❌ Socket error: $error")
            }

            on("reconnect") { args ->
                val attempt = args.firstOrNull() as? Int ?: 0
                Log.d(TAG, "🔄 Reconnecting... (attempt $attempt)")
            }

            on("reconnect_attempt") { args ->
                val attempt = args.firstOrNull() as? Int ?: 0
                Log.d(TAG, "🔄 Reconnect attempt $attempt/$maxReconnectAttempts")
            }

            on("reconnect_failed") {
                Log.e(TAG, "❌ Reconnect FAILED dopo $maxReconnectAttempts tentativi")
                _connectionStatus.value = ConnectionStatus.ERROR
            }
        }
    }

    /**
     * Controlla se connesso
     */
    fun isConnected(): Boolean = socket?.connected() ?: false

}

//DATA CLASSES
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class EnrichmentUpdate(
    val sessionId: String,
    val type: String,
    val total: Int,
    val processed: Int,
    val currentMovie: String,
    val message: String,
    val percentage: Int
)