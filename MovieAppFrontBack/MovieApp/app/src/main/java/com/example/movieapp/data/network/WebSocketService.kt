package com.example.movieapp.data.network

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * servizio websocket per notifiche real-time dal backend locale
 * fixed: ip mascherato e logging sicuro
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

    //url mascherato per logging sicuro
    private val BACKEND_URL = "ws://backend_locale:3001"
    private var isConnected = false
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
        Log.d(TAG, "websocket service inizializzato per backend locale")
    }

    /**
     * connette al backend websocket locale
     */
    fun connect(sessionId: String? = null) {
        Log.d(TAG, "connessione websocket al backend locale")
        currentSessionId = sessionId
        _connectionStatus.value = ConnectionStatus.CONNECTING

        CoroutineScope(Dispatchers.IO).launch {
            try {
                delay(1500) //simula connessione

                isConnected = true
                reconnectAttempts = 0
                _connectionStatus.value = ConnectionStatus.CONNECTED

                if (sessionId != null) {
                    simulateConnectionMessage(sessionId)
                } else {
                    notifySystemStatus("backend locale v2.0 connesso - grafici e notifiche attivi")
                }

                Log.d(TAG, "connessione backend locale stabilita!")

            } catch (e: Exception) {
                Log.e(TAG, "errore connessione backend locale: ${e.message}")
                _connectionStatus.value = ConnectionStatus.ERROR
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnessione websocket dal backend locale")
        _connectionStatus.value = ConnectionStatus.DISCONNECTED
        isConnected = false
        currentSessionId = null
        reconnectAttempts = 0
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts < maxReconnectAttempts) {
            reconnectAttempts++
            val delay = (reconnectAttempts * 2000L).coerceAtMost(10000L)

            Log.d(TAG, "tentativo riconnessione #$reconnectAttempts in ${delay}ms")

            CoroutineScope(Dispatchers.IO).launch {
                delay(delay)
                if (!isConnected) {
                    connect(currentSessionId)
                }
            }
        } else {
            Log.e(TAG, "massimo numero tentativi riconnessione raggiunto")
            notifySystemStatus("connessione backend locale fallita dopo $maxReconnectAttempts tentativi")
        }
    }

    private fun simulateConnectionMessage(sessionId: String) {
        val update = EnrichmentUpdate(
            sessionId = sessionId,
            type = "connected",
            message = "connesso al backend locale v2.0 - database room + cache + grafici",
            percentage = 0
        )

        _enrichmentUpdates.value = update
        notifySystemStatus("websocket connesso - session: $sessionId")
        Log.d(TAG, "messaggio connessione simulato per sessione: $sessionId")
    }

    fun notifyChartGenerated(chartType: String, dataPoints: Int) {
        val chartUpdate = ChartUpdate(
            type = chartType,
            status = "generated",
            dataPoints = dataPoints,
            message = "grafico $chartType generato con $dataPoints elementi"
        )

        _chartUpdates.value = chartUpdate
        Log.d(TAG, "notifica grafico generato: $chartType ($dataPoints punti)")
    }

    fun notifyChartError(chartType: String, error: String) {
        val chartUpdate = ChartUpdate(
            type = chartType,
            status = "error",
            dataPoints = 0,
            message = "errore grafico $chartType: $error",
            error = error
        )

        _chartUpdates.value = chartUpdate
        Log.e(TAG, "errore grafico $chartType: $error")
    }

    private fun notifySystemStatus(message: String) {
        val notification = SystemNotification(
            type = "status",
            message = message,
            timestamp = System.currentTimeMillis()
        )

        _systemNotifications.value = notification
        Log.d(TAG, "notifica sistema: $message")
    }

    fun simulateEnrichmentUpdate(sessionId: String, processed: Int, total: Int, currentMovie: String? = null) {
        val percentage = if (total > 0) (processed * 100) / total else 0

        val update = EnrichmentUpdate(
            sessionId = sessionId,
            type = "progress",
            total = total,
            processed = processed,
            successful = processed,
            failed = 0,
            currentMovie = currentMovie,
            message = "backend locale: processati $processed/$total film",
            percentage = percentage
        )

        _enrichmentUpdates.value = update
        Log.d(TAG, "update backend locale simulato: ${update.message}")

        if (percentage > 80) {
            notifySystemStatus("preparazione grafici in corso...")
        }
    }

    fun simulateEnrichmentCompleted(
        sessionId: String,
        total: Int,
        successful: Int,
        cacheHits: Int = 0
    ) {
        val backendProcessed = successful - cacheHits
        val limitInfo = if (total > 25) " (elaborati in batch di 25)" else ""

        val update = EnrichmentUpdate(
            sessionId = sessionId,
            type = "completed",
            total = total,
            processed = total,
            successful = successful,
            failed = total - successful,
            message = "✅ arricchimento completato$limitInfo\n" +
                    "📊 ${successful}/${total} film elaborati\n" +
                    "💾 cache hits: $cacheHits\n" +
                    "🔄 nuovi dal backend locale: $backendProcessed\n" +
                    "🎯 i tuoi grafici sono pronti!",
            percentage = 100
        )

        _enrichmentUpdates.value = update
        Log.d(TAG, "enrichment completato con backend locale: ${update.message}")

        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            notifySystemStatus("🎨 aggiornamento grafici in corso...")

            delay(800)
            notifyChartGenerated("generi-pie", 8)

            delay(500)
            notifyChartGenerated("anni-bar", 12)

            delay(500)
            notifyChartGenerated("registi-bar", 6)

            delay(300)
            notifySystemStatus("🎉 tutti i grafici sono stati aggiornati con i nuovi dati!")
        }
    }

    fun simulateEnrichmentError(sessionId: String, error: String) {
        val update = EnrichmentUpdate(
            sessionId = sessionId,
            type = "error",
            message = "errore backend locale: $error",
            error = error,
            percentage = 0
        )

        _enrichmentUpdates.value = update
        Log.e(TAG, "errore backend locale simulato:$error")
    }
    fun isWebSocketConnected(): Boolean {
        return isConnected && _connectionStatus.value == ConnectionStatus.CONNECTED
    }

    fun getCurrentStatus(): ConnectionStatus {
        return _connectionStatus.value
    }

    fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "status" to _connectionStatus.value.name,
            "connected" to isConnected,
            "backend" to "locale",
            "session_id" to (currentSessionId ?: "none"),
            "reconnect_attempts" to reconnectAttempts
        )
    }

    enum class ConnectionStatus {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }
}
//data classes websocket
data class EnrichmentUpdate(
    val sessionId: String,
    val type: String,
    val total: Int? = null,
    val processed: Int? = null,
    val successful: Int? = null,
    val failed: Int? = null,
    val currentMovie: String? = null,
    val message: String,
    val percentage: Int? = null,
    val error: String? = null
)
data class ChartUpdate(
    val type: String,
    val status: String,
    val dataPoints: Int,
    val message: String,
    val error: String? = null
)
data class SystemNotification(
    val type: String,
    val message: String,
    val timestamp: Long,
    val data: Map<String, Any>? = null
)