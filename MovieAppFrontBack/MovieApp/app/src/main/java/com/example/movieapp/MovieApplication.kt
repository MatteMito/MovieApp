package com.example.movieapp

import android.app.Application
import android.util.Log
import com.example.movieapp.config.AppConfig

/**
 * application class per inizializzazione globale con setup logging e configurazione app
 */
class MovieApplication : Application() {

    private val TAG = "MovieApplication"

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "=== movieapp application started ===")
        Log.d(TAG, "version: ${AppConfig.APP_VERSION}")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
        Log.d(TAG, "database: ${AppConfig.DATABASE_TYPE}")

        //verifica configurazione valida
        if (!AppConfig.isBackendConfigValid()) {
            Log.e(TAG, "❌ configurazione backend non valida!")
            Log.e(TAG, "controlla AppConfig.kt e imposta:")
            Log.e(TAG, "  BACKEND_HOST = tuo ip locale")
            Log.e(TAG, "  BACKEND_PORT = 3001")
        } else {
            Log.d(TAG, "✅ configurazione backend valida")
        }

        //log configurazione completa
        AppConfig.getBackendInfo().forEach { (key, value) ->
            Log.d(TAG, "$key: $value")
        }

        Log.d(TAG, AppConfig.getConfigSummary())
    }
}