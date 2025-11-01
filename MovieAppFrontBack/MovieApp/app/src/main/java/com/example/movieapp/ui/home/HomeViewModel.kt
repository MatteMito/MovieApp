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
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.ImportWorker
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.WebSocketService
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.EnrichmentUpdate
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * viewmodel - gestisce worker + websocket monitoring
 */
class HomeViewModel : ViewModel() {

    private val TAG = "HomeViewModel"

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _isImporting = MutableLiveData<Boolean>()
    val isImporting: LiveData<Boolean> = _isImporting

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private lateinit var repository: MovieRepository
    private val webSocketService = WebSocketService.getInstance()

    private var currentSessionId: String? = null

    fun initialize(context: Context) {
        repository = MovieRepository.getInstance(context)

        repository.movies.observeForever { moviesList ->
            _movies.value = moviesList
            Log.d(TAG, "movies aggiornati: ${moviesList.size}")
        }

        loadMovies()
    }

    fun observeWebSocketUpdates(): StateFlow<EnrichmentUpdate?> {
        return webSocketService.enrichmentUpdates
    }

    fun loadMovies() {
        viewModelScope.launch {
            _isLoading.value = true
            val movies = repository.movies.value ?: emptyList()
            _movies.value = movies
            _isLoading.value = false
            Log.d(TAG, "film caricati: ${movies.size}")
        }
    }

    fun refreshFromBackend() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = repository.refreshFromBackend()
                if (success) {
                    val movies = repository.movies.value ?: emptyList()
                    _movies.value = movies
                    Log.d(TAG, "refresh completato: ${movies.size} film")
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore refresh: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun startImport(context: Context, filePath: String, csvType: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== AVVIO IMPORT ===")

                //reset stato
                _importProgress.value = 0
                _isImporting.value = true

                //connetti websocket
                webSocketService.connect()
                delay(1000)

                //crea work
                val workRequest = OneTimeWorkRequestBuilder<ImportWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            ImportWorker.KEY_FILE_PATH to filePath,
                            ImportWorker.KEY_CSV_TYPE to csvType
                        )
                    )
                    .build()

                val workManager = WorkManager.getInstance(context)
                workManager.enqueueUniqueWork(
                    "movie_import",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                //osserva worker
                workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                val progress = workInfo.progress.getInt(ImportWorker.KEY_PROGRESS, 0)
                                _importProgress.value = progress
                                Log.d(TAG, "worker progress: $progress%")
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                Log.d(TAG, "✅ WORKER COMPLETATO")

                                //estrai sessionId
                                currentSessionId = workInfo.outputData.getString(ImportWorker.KEY_SESSION_ID)
                                Log.d(TAG, "session ID per monitoring: $currentSessionId")

                                //ora il worker è finito, ma l'enrichment continua sul server
                                //l'UI continuerà a monitorare via websocket
                                _importProgress.value = 40 //reset a 40% per enrichment
                            }
                            WorkInfo.State.FAILED -> {
                                Log.e(TAG, "❌ WORKER FALLITO")
                                _isImporting.value = false
                                _importProgress.value = 0
                            }
                            else -> {}
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore startImport: ${e.message}", e)
                _isImporting.value = false
            }
        }
    }

    /**
     * chiamato dall'UI quando websocket dice "completed"
     */
    fun onEnrichmentCompleted() {
        viewModelScope.launch {
            Log.d(TAG, "=== ENRICHMENT COMPLETATO ===")

            _importProgress.value = 100
            delay(1000)

            //refresh per caricare film arricchiti
            refreshFromBackend()
            delay(1000)

            //termina import
            _isImporting.value = false
            _importProgress.value = 0
            currentSessionId = null

            Log.d(TAG, "import terminato - UI sbloccata")
        }
    }

    fun clearAllMovies() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "eliminazione tutti i film...")

                val result = ApiService.deleteAllUserMovies()

                if (result.isSuccess) {
                    repository.refreshFromBackend()
                    _movies.value = emptyList()
                    _message.value = "Tutti i film eliminati"
                    Log.d(TAG, "eliminazione completata")
                } else {
                    _message.value = "Errore eliminazione"
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore clearAll: ${e.message}", e)
                _message.value = "Errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}