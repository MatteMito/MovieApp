package com.example.movieapp.data.network

import android.util.Log
import com.example.movieapp.config.AppConfig
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URISyntaxException

/**
 * Servizio WebSocket REALE per notifiche real-time dal backend
 *
 * IMPORTANTE: Ricevi aggiornamenti in tempo reale durante l'enrichment!
 *
 * Installa le dipendenze necessarie in build.gradle:
 * implementation("io.socket:socket.io-client:2.1.0")
 */
class WebSocketService private constructor() {

    private val TAG = "WebSocketService"

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _enrichmentUpdates = MutableStateFlow<EnrichmentUpdate?>(null)
    val enrichmentUpdates: StateFlow<EnrichmentUpdate?> = _enrichmentUpdates.asStateFlow()

    private val _chartUpdates = MutableStateFlow<ChartUpdate?>(null)
    val chartUpdates: StateFlow<ChartUpdate?> = _chartUpdates.asStateFlow()

    private val _systemNotifications = MutableStateFlow<SystemNotification?>(null)
    val systemNotifications: StateFlow<SystemNotification?> = _systemNotifications.asStateFlow()

    // 🔌 Socket.IO client reale
    private var socket: Socket? = null
    private var currentSessionId: String? = null
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
        Log.d(TAG, "Backend URL: ${AppConfig.WEBSOCKET_URL}")
    }

    /**
     * Connette al backend WebSocket usando Socket.IO
     */
    fun connect(sessionId: String? = null) {
        if (socket != null && socket!!.connected()) {
            Log.d(TAG, "⚠️ WebSocket già connesso")
            return
        }

        Log.d(TAG, "🔌 Connessione a ${AppConfig.WEBSOCKET_URL}")
        currentSessionId = sessionId
        _connectionStatus.value = ConnectionStatus.CONNECTING

        try {
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = maxReconnectAttempts
                reconnectionDelay = 2000
                timeout = 10000
            }

            socket = IO.socket(AppConfig.WEBSOCKET_URL, opts)

            setupSocketListeners()

            socket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "❌ Errore URI WebSocket", e)
            _connectionStatus.value = ConnectionStatus.ERROR
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore connessione WebSocket", e)
            _connectionStatus.value = ConnectionStatus.ERROR
        }
    }

    /**
     * Setup listeners per tutti gli eventi WebSocket
     */
    private fun setupSocketListeners() {
        socket?.apply {
            // === EVENTI CONNESSIONE ===
            on(Socket.EVENT_CONNECT) {
                Log.d(TAG, "✅ WebSocket connesso!")
                _connectionStatus.value = ConnectionStatus.CONNECTED
                reconnectAttempts = 0

                notifySystemStatus("✅ Backend connesso")
            }

            on(Socket.EVENT_DISCONNECT) {
                Log.d(TAG, "❌ WebSocket disconnesso")
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }

            on(Socket.EVENT_CONNECT_ERROR) { args ->
                Log.e(TAG, "❌ Errore connessione: ${args.firstOrNull()}")
                _connectionStatus.value = ConnectionStatus.ERROR
            }

            // === EVENTI ENRICHMENT (QUESTI SONO FONDAMENTALI PER LA PROGRESS BAR!) ===

            /**
             * 📊 Aggiornamento progresso enrichment in tempo reale
             * Questo evento viene emesso dal backend per ogni film processato
             */
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

                    Log.d(TAG, "📊 Progress: $processed/$total ($percentage%) - $currentMovie")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing progress", e)
                }
            }

            /**
             * ✅ Enrichment completato
             */
            on("enrichment:completed") { args ->
                try {
                    val data = args[0] as JSONObject
                    val sessionId = data.getString("sessionId")
                    val total = data.getInt("total")
                    val successful = data.getInt("successful")
                    val message = data.getString("message")

                    val update = EnrichmentUpdate(
                        sessionId = sessionId,
                        type = "completed",
                        total = total,
                        processed = total,
                        message = message,
                        percentage = 100
                    )

                    _enrichmentUpdates.value = update

                    Log.d(TAG, "✅ Enrichment completato: $successful/$total")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing completed", e)
                }
            }

            /**
             * ❌ Errore enrichment
             */
            on("enrichment:error") { args ->
                try {
                    val data = args[0] as JSONObject
                    val sessionId = data.getString("sessionId")
                    val error = data.getString("error")
                    val message = data.getString("message")

                    val update = EnrichmentUpdate(
                        sessionId = sessionId,
                        type = "error",
                        message = message,
                        percentage = 0
                    )

                    _enrichmentUpdates.value = update

                    Log.e(TAG, "❌ Errore enrichment: $error")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing error", e)
                }
            }

            // === EVENTI BATCH ===
            on("batch:completed") { args ->
                try {
                    val data = args[0] as JSONObject
                    val message = data.getString("message")
                    notifySystemStatus(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing batch", e)
                }
            }

            // === EVENTI GRAFICI ===
            on("chart:update") { args ->
                try {
                    val data = args[0] as JSONObject
                    val chartType = data.getString("type")
                    val dataPoints = data.getInt("dataPoints")
                    val message = data.getString("message")

                    val chartUpdate = ChartUpdate(
                        type = chartType,
                        status = "generated",
                        dataPoints = dataPoints,
                        message = message
                    )

                    _chartUpdates.value = chartUpdate

                    Log.d(TAG, "📈 Grafico aggiornato: $chartType")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing chart", e)
                }
            }

            on("chart:error") { args ->
                try {
                    val data = args[0] as JSONObject
                    val chartType = data.getString("type")
                    val error = data.getString("error")

                    val chartUpdate = ChartUpdate(
                        type = chartType,
                        status = "error",
                        dataPoints = 0,
                        message = error,
                        error = error
                    )

                    _chartUpdates.value = chartUpdate

                    Log.e(TAG, "❌ Errore grafico: $chartType")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing chart error", e)
                }
            }

            // === EVENTI SISTEMA ===
            on("system:notification") { args ->
                try {
                    val data = args[0] as JSONObject
                    val type = data.getString("type")
                    val message = data.getString("message")

                    notifySystemStatus(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing system", e)
                }
            }

            // === MESSAGGIO CONNESSIONE ===
            on("connection") { args ->
                try {
                    val data = args[0] as JSONObject
                    val message = data.getString("message")
                    Log.d(TAG, "💬 Backend: $message")
                    notifySystemStatus(message)
                } catch (e: Exception) {
                    Log.e(TAG, "Errore parsing connection", e)
                }
            }
        }
    }

    /**
     * Disconnette dal WebSocket
     */
    fun disconnect() {
        Log.d(TAG, "🔌 Disconnessione WebSocket")
        socket?.disconnect()
        socket?.off()
        socket = null
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        currentSessionId = null
        reconnectAttempts = 0
    }

    /**
     * Notifica sistema
     */
    private fun notifySystemStatus(message: String) {
        val notification = SystemNotification(
            type = "system",
            message = message,
            timestamp = System.currentTimeMillis()
        )
        _systemNotifications.value = notification
    }

    /**
     * Controlla se connesso
     */
    fun isConnected(): Boolean = socket?.connected() ?: false

    /**
     * Reset tutti gli stati
     */
    fun reset() {
        _enrichmentUpdates.value = null
        _chartUpdates.value = null
        _systemNotifications.value = null
    }
}

// === DATA CLASSES ===

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class EnrichmentUpdate(
    val sessionId: String,
    val type: String, // "progress", "completed", "error", "connected"
    val total: Int = 0,
    val processed: Int = 0,
    val currentMovie: String = "",
    val message: String,
    val percentage: Int
)

data class ChartUpdate(
    val type: String,
    val status: String, // "generating", "generated", "error"
    val dataPoints: Int,
    val message: String,
    val error: String? = null
)

data class SystemNotification(
    val type: String,
    val message: String,
    val timestamp: Long
)