package com.example.movieapp.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.ImportWorker
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.WebSocketService
import com.example.movieapp.data.network.EnrichmentUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

// viewmodel per home fragment, gestisce import csv e sincronizzazione con backend
class HomeViewModel : ViewModel() {

    private val TAG = "HomeViewModel"

    // lista film osservabile
    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    // stato loading generale
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // stato import in corso
    private val _isImporting = MutableLiveData<Boolean>()
    val isImporting: LiveData<Boolean> = _isImporting

    // messaggi da mostrare all'utente
    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private lateinit var repository: MovieRepository
    private lateinit var workManager: WorkManager
    // singleton websocket per notifiche real-time
    private val webSocketService = WebSocketService.getInstance()

    // inizializza repository, workmanager e websocket
    fun initialize(context: Context) {
        repository = MovieRepository.getInstance(context)
        workManager = WorkManager.getInstance(context)

        // osserva cambiamenti nel repository e aggiorna livedata
        repository.movies.observeForever { moviesList ->
            _movies.value = moviesList
            Log.d(TAG, "movies aggiornati: ${moviesList.size}")
        }

        // connetti websocket per notifiche enrichment
        if (!webSocketService.isConnected()) {
            webSocketService.connect()
            Log.d(TAG, "websocket connesso")
        }

        // carica dati dal backend al primo avvio
        refreshFromBackend()
    }

    // espone flow websocket per osservare aggiornamenti enrichment
    fun observeWebSocketUpdates(): StateFlow<EnrichmentUpdate?> {
        return webSocketService.enrichmentUpdates
    }

    // ricarica film dal backend
    fun refreshFromBackend() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = repository.refreshFromBackend()
                if (success) {
                    val movies = repository.movies.value ?: emptyList()
                    _movies.value = movies
                    Log.d(TAG, "refresh ok: ${movies.size} film")
                } else {
                    Log.e(TAG, "refresh fallito")
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore refresh", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // aggiorna stato import per ui
    fun setImporting(isImporting: Boolean) {
        _isImporting.value = isImporting
        Log.d(TAG, "setImporting: $isImporting")
    }

    // avvia import csv usando workmanager per esecuzione in background
    fun startImport(filePath: String, csvType: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "avvio import worker")
                _isImporting.value = true

                // crea work request con parametri file e tipo csv
                val workRequest = OneTimeWorkRequestBuilder<ImportWorker>()
                    .setInputData(
                        workDataOf(
                            ImportWorker.KEY_FILE_PATH to filePath,
                            ImportWorker.KEY_CSV_TYPE to csvType
                        )
                    )
                    .build()

                // accoda worker con politica replace per sostituzione import precedenti
                workManager.enqueueUniqueWork(
                    "movie_import",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                // monitora stato worker
                workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                Log.d(TAG, "worker in esecuzione")
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                Log.d(TAG, "worker completato, enrichment sul server...")
                                // non fare refresh qui, aspetta notifica websocket 'completed'
                            }
                            WorkInfo.State.FAILED -> {
                                Log.e(TAG, "worker fallito")
                                _isImporting.value = false
                                _message.value = "Upload fallito. Riprova con connessione stabile."
                            }
                            else -> {}
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore startImport", e)
                _isImporting.value = false
                _message.value = "Errore: ${e.message}"
            }
        }
    }

    // chiamato quando websocket riceve notifica di enrichment completato
    fun onEnrichmentCompleted() {
        viewModelScope.launch {
            Log.d(TAG, "enrichment completato dal websocket!")

            // ricarica dati aggiornati dal backend
            refreshFromBackend()

            // nasconde loader e mostra messaggio successo
            _isImporting.value = false
            _message.value = "Import completato!"
        }
    }
}