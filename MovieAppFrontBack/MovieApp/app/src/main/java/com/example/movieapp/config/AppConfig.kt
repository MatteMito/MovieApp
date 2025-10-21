package com.example.movieapp.config

//configurazione centralizzata app android con parametri backend

object AppConfig {
    //BACKEND CONFIGURATION
    const val BACKEND_HOST = "192.168.1.163"
    const val BACKEND_PORT = 3001

    //url costruiti automaticamente
    const val BASE_URL = "http://$BACKEND_HOST:$BACKEND_PORT/api/v1/"
    const val BACKEND_URL = "http://$BACKEND_HOST:$BACKEND_PORT"
    const val WEBSOCKET_URL = "ws://$BACKEND_HOST:$BACKEND_PORT/ws"

    //timeout connessioni (secondi)
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    //cache configuration
    const val CACHE_PREFS_NAME = "movieapp_cache_v2"
    const val REPO_PREFS_NAME = "movieapp_repository_v2"
    const val AUTH_PREFS_NAME = "movieapp_auth_v2"
    //film per batch salvataggio
    const val BATCH_SIZE = 50

    //database configuration
    const val DATABASE_TYPE = "room_sqlite"

    //app metadata
    const val APP_VERSION = "2.1.0"
    const val APP_NAME = "MovieApp"

    //feature flags
    const val ENABLE_WEBSOCKET = true
    const val ENABLE_CACHE = true
    const val ENABLE_AUTO_SYNC = true
    const val ENABLE_OFFLINE_MODE = true

    //verifica configurazione valida
    fun isBackendConfigValid(): Boolean {
        return BACKEND_HOST.isNotEmpty() &&
                BACKEND_PORT > 0 &&
                BASE_URL.isNotEmpty()
    }

    //info backend formattata per logging
    fun getBackendInfo(): Map<String, String> {
        return mapOf(
            "host" to BACKEND_HOST,
            "port" to BACKEND_PORT.toString(),
            "base_url" to BASE_URL,
            "websocket_url" to WEBSOCKET_URL,
            "version" to APP_VERSION
        )
    }

    //summary configurazione per debug
    fun getConfigSummary(): String {
        return buildString {
            appendLine("=== MOVIEAPP CONFIG v$APP_VERSION ===")
            appendLine("Backend: $BACKEND_HOST:$BACKEND_PORT")
            appendLine("Database: $DATABASE_TYPE")
            appendLine("Cache: ${if (ENABLE_CACHE) "attiva" else "disattiva"}")
            appendLine("WebSocket: ${if (ENABLE_WEBSOCKET) "attivo" else "disattivo"}")
            appendLine("Auto-sync: ${if (ENABLE_AUTO_SYNC) "attivo" else "disattivo"}")
        }
    }
}