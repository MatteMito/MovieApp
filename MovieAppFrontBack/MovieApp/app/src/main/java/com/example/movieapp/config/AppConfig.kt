package com.example.movieapp.config

/**
 * Configurazione centralizzata app Android - SEMPLIFICATA
 *
 * ✅ RIMOSSO: Cache layer ridondante (CACHE_PREFS_NAME, ENABLE_CACHE, ecc.)
 * 💾 Il database PostgreSQL È già la cache!
 */
object AppConfig {
    // ===== BACKEND CONFIGURATION =====
    const val BACKEND_HOST = "192.168.1.163"
    const val BACKEND_PORT = 3001

    // URL costruiti automaticamente
    const val BASE_URL = "http://$BACKEND_HOST:$BACKEND_PORT/api/v1/"
    const val BACKEND_URL = "http://$BACKEND_HOST:$BACKEND_PORT"
    const val WEBSOCKET_URL = "ws://$BACKEND_HOST:$BACKEND_PORT/ws"

    // ===== TIMEOUT CONNESSIONI (secondi) =====
    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT = 30L
    const val WRITE_TIMEOUT = 30L

    // ===== SHARED PREFERENCES =====
    // ❌ RIMOSSO: CACHE_PREFS_NAME (non serve più cache ridondante)
    const val REPO_PREFS_NAME = "movieapp_repository_v2"
    const val AUTH_PREFS_NAME = "movieapp_auth_v2"

    // Film per batch salvataggio
    const val BATCH_SIZE = 50

    // ===== DATABASE CONFIGURATION =====
    const val DATABASE_TYPE = "postgresql"  // ✅ Ora è chiaro: PostgreSQL è la nostra cache!

    // ===== APP METADATA =====
    const val APP_VERSION = "2.2.0"  // ✅ Versione aggiornata (architettura semplificata)
    const val APP_NAME = "MovieApp"

    // ===== FEATURE FLAGS =====
    const val ENABLE_WEBSOCKET = true
    // ❌ RIMOSSO: ENABLE_CACHE (il database È già la cache!)
    const val ENABLE_AUTO_SYNC = true
    // ❌ RIMOSSO: ENABLE_OFFLINE_MODE (gestito direttamente dal repository/database)

    // ===== VALIDATION =====
    /**
     * Verifica configurazione backend valida
     */
    fun isBackendConfigValid(): Boolean {
        return BACKEND_HOST.isNotEmpty() &&
                BACKEND_PORT > 0 &&
                BASE_URL.isNotEmpty()
    }

    // ===== LOGGING INFO =====
    /**
     * Info backend formattata per logging
     */
    fun getBackendInfo(): Map<String, String> {
        return mapOf(
            "host" to BACKEND_HOST,
            "port" to BACKEND_PORT.toString(),
            "base_url" to BASE_URL,
            "websocket_url" to WEBSOCKET_URL,
            "version" to APP_VERSION,
            "database" to DATABASE_TYPE
        )
    }

    /**
     * Summary configurazione per debug
     */
    fun getConfigSummary(): String {
        return buildString {
            appendLine("=== MOVIEAPP CONFIG v$APP_VERSION ===")
            appendLine("Backend: $BACKEND_HOST:$BACKEND_PORT")
            appendLine("Database: $DATABASE_TYPE (= Cache intelligente!)")
            appendLine("WebSocket: ${if (ENABLE_WEBSOCKET) "attivo" else "disattivo"}")
            appendLine("Auto-sync: ${if (ENABLE_AUTO_SYNC) "attivo" else "disattivo"}")
            appendLine()
            appendLine("💾 Architettura semplificata:")
            appendLine("   Database PostgreSQL = Cache permanente")
            appendLine("   Nessuna duplicazione di dati")
        }
    }
}