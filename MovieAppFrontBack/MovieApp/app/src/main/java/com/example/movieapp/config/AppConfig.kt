package com.example.movieapp.config

/**
 * Configurazione centralizzata app Android - OTTIMIZZATA
 *
 * ✅ WebSocket URL corretto per Socket.IO
 * ✅ Architettura semplificata (database = cache)
 */
object AppConfig {
    // ===== BACKEND CONFIGURATION =====
    const val BACKEND_HOST = "192.168.1.163"
    const val BACKEND_PORT = 3001

    // URL costruiti automaticamente
    const val BASE_URL = "http://$BACKEND_HOST:$BACKEND_PORT/api/v1/"
    const val BACKEND_URL = "http://$BACKEND_HOST:$BACKEND_PORT"

    // ✅ CORRETTO: Socket.IO URL (senza /ws nel path base)
    // Il namespace /ws viene gestito da Socket.IO automaticamente
    const val WEBSOCKET_URL = "http://$BACKEND_HOST:$BACKEND_PORT"
    const val WEBSOCKET_NAMESPACE = "/ws"

    // ===== TIMEOUT CONNESSIONI (secondi) =====
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // ===== SHARED PREFERENCES =====
    const val REPO_PREFS_NAME = "movieapp_repository_v2"
    const val AUTH_PREFS_NAME = "movieapp_auth_v2"

    // Film per batch salvataggio
    const val BATCH_SIZE = 50

    // ===== DATABASE CONFIGURATION =====
    const val DATABASE_TYPE = "postgresql"

    // ===== APP METADATA =====
    const val APP_VERSION = "2.3.0"  // ✅ Versione con WebSocket fix + UX migliorata
    const val APP_NAME = "MovieApp"

    // ===== FEATURE FLAGS =====
    const val ENABLE_WEBSOCKET = true
    const val ENABLE_AUTO_SYNC = true
    const val ENABLE_REALTIME_PROGRESS = true  // ✅ Nuovo: progress bar real-time

    // ===== VALIDATION =====
    fun isBackendConfigValid(): Boolean {
        return BACKEND_HOST.isNotEmpty() &&
                BACKEND_PORT > 0 &&
                BASE_URL.isNotEmpty()
    }

    // ===== LOGGING INFO =====
    fun getBackendInfo(): Map<String, String> {
        return mapOf(
            "host" to BACKEND_HOST,
            "port" to BACKEND_PORT.toString(),
            "base_url" to BASE_URL,
            "websocket_url" to WEBSOCKET_URL,
            "websocket_namespace" to WEBSOCKET_NAMESPACE,
            "version" to APP_VERSION,
            "database" to DATABASE_TYPE
        )
    }

    fun getConfigSummary(): String {
        return buildString {
            appendLine("=== MOVIEAPP CONFIG v$APP_VERSION ===")
            appendLine("Backend: $BACKEND_HOST:$BACKEND_PORT")
            appendLine("WebSocket: $WEBSOCKET_URL$WEBSOCKET_NAMESPACE")
            appendLine("Database: $DATABASE_TYPE (= Cache intelligente!)")
            appendLine("WebSocket: ${if (ENABLE_WEBSOCKET) "attivo" else "disattivo"}")
            appendLine("Progress real-time: ${if (ENABLE_REALTIME_PROGRESS) "attivo" else "disattivo"}")
            appendLine("Auto-sync: ${if (ENABLE_AUTO_SYNC) "attivo" else "disattivo"}")
            appendLine()
            appendLine("💾 Architettura semplificata:")
            appendLine("   Database PostgreSQL = Cache permanente")
            appendLine("   WebSocket per progress real-time")
            appendLine("   Nessuna duplicazione di dati")
        }
    }
}