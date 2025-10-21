package com.example.movieapp.data.network

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Tracker per simulare progresso enrichment quando backend non fornisce aggiornamenti real-time
 */
class EnrichmentProgressTracker {
    private val TAG = "EnrichmentTracker"

    private val _progress = MutableStateFlow(EnrichmentProgress(0, 0, false))
    val progress: StateFlow<EnrichmentProgress> = _progress

    private var trackingJob: Job? = null

    data class EnrichmentProgress(
        val current: Int,
        val total: Int,
        val isComplete: Boolean
    )

    /**
     * Avvia simulazione progresso durante enrichment
     * Stima: ~2-3 secondi per film (chiamata TMDB + processing)
     */
    fun startTracking(totalMovies: Int, scope: CoroutineScope) {
        Log.d(TAG, "🎬 Avvio tracking per $totalMovies film")

        trackingJob?.cancel()
        _progress.value = EnrichmentProgress(0, totalMovies, false)

        trackingJob = scope.launch {
            try {
                val estimatedTimePerMovie = 2500L // 2.5 secondi per film
                var processedCount = 0

                // Simula progresso incrementale
                while (processedCount < totalMovies && isActive) {
                    delay(estimatedTimePerMovie)
                    processedCount++

                    _progress.value = EnrichmentProgress(
                        current = processedCount,
                        total = totalMovies,
                        isComplete = false
                    )

                    Log.d(TAG, "📊 Progresso: $processedCount/$totalMovies (${(processedCount * 100) / totalMovies}%)")
                }

            } catch (e: CancellationException) {
                Log.d(TAG, "⏸️ Tracking cancellato")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore tracking", e)
            }
        }
    }

    /**
     * Completa immediatamente il progresso
     */
    fun complete(actualCount: Int) {
        Log.d(TAG, "✅ Completato: $actualCount film")
        trackingJob?.cancel()
        _progress.value = EnrichmentProgress(actualCount, actualCount, true)
    }

    /**
     * Reset tracker
     */
    fun reset() {
        trackingJob?.cancel()
        _progress.value = EnrichmentProgress(0, 0, false)
    }
}