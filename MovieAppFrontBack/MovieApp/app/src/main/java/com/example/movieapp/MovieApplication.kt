package com.example.movieapp

import android.app.Application
import android.util.Log
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.network.NotificationHelper

/**
 * application class per inizializzazione globale app
 */
class MovieApplication : Application() {

    private val TAG = "MovieApplication"

    override fun onCreate() {
        super.onCreate()

        Log.d(TAG, "=== movieapp application started ===")
        Log.d(TAG, "version: ${AppConfig.APP_VERSION}")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
        Log.d(TAG, "database: ${AppConfig.DATABASE_TYPE}")

        // verifica che la configurazione backend sia valida prima di avviare
        if (!AppConfig.isBackendConfigValid()) {
            Log.e(TAG, "configurazione backend non valida!")
            Log.e(TAG, "controlla AppConfig.kt e imposta:")
            Log.e(TAG, "  BACKEND_HOST = tuo ip locale")
            Log.e(TAG, "  BACKEND_PORT = 3001")
        } else {
            Log.d(TAG, "configurazione backend valida")
        }

        // crea notification channel per notifiche import csv in background
        NotificationHelper.createNotificationChannel(this)
        Log.d(TAG, "notification channel creato")

        // log dettagliato configurazione per debug
        AppConfig.getBackendInfo().forEach { (key, value) ->
            Log.d(TAG, "$key: $value")
        }

        // stampa riepilogo configurazione con architettura app
        Log.d(TAG, AppConfig.getConfigSummary())
    }
}