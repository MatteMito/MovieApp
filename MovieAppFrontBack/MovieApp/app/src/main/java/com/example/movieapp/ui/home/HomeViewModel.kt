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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * viewmodel per homefragment
 * fix: progress 100%, refresh automatico con stats aggiornate, bottoni riabilitati
 */
class HomeViewModel : ViewModel() {

    private val TAG = "HomeViewModel"

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isImporting = MutableLiveData<Boolean>()
    val isImporting: LiveData<Boolean> = _isImporting

    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress

    private lateinit var repository: MovieRepository
    private val webSocketService = WebSocketService.getInstance()

    fun initialize(context: Context) {
        repository = MovieRepository.getInstance(context)

        //osserva movies dal repository
        repository.movies.observeForever { moviesList ->
            _movies.value = moviesList
            Log.d(TAG, "movies aggiornati da repository: ${moviesList.size}")
        }

        //carica iniziale
        loadMovies()
    }

    /**
     * espone websocket updates per la ui
     */
    fun observeWebSocketUpdates(): StateFlow<EnrichmentUpdate?> {
        return webSocketService.enrichmentUpdates
    }

    /**
     * carica film dal repository
     */
    fun loadMovies() {
        viewModelScope.launch {
            _isLoading.value = true

            val movies = repository.movies.value ?: emptyList()
            _movies.value = movies

            _isLoading.value = false
            Log.d(TAG, "film caricati: ${movies.size}")
        }
    }

    /**
     * refresh dal backend
     */
    fun refreshFromBackend() {
        viewModelScope.launch {
            _isLoading.value = true

            try {
                Log.d(TAG, "refresh from backend...")
                val success = repository.refreshFromBackend()
                if (success) {
                    val movies = repository.movies.value ?: emptyList()
                    _movies.value = movies
                    Log.d(TAG, "refresh completato: ${movies.size} film")
                } else {
                    Log.e(TAG, "errore refresh backend")
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore refresh: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * avvia import in background con workmanager
     */
    fun startImport(context: Context, filePath: String, csvType: String) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "avvio import: $filePath, tipo: $csvType")

                //reset progress e mostra import
                _importProgress.value = 0
                _isImporting.value = true

                //connetti websocket per progress real-time
                webSocketService.connect()
                delay(500)

                //crea work request
                val workRequest = OneTimeWorkRequestBuilder<ImportWorker>()
                    .setInputData(
                        androidx.work.workDataOf(
                            ImportWorker.KEY_FILE_PATH to filePath,
                            ImportWorker.KEY_CSV_TYPE to csvType
                        )
                    )
                    .build()

                val workManager = WorkManager.getInstance(context)

                //avvia worker con unique work (cancella precedenti se esistono)
                workManager.enqueueUniqueWork(
                    "movie_import",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                //osserva progress worker
                workManager.getWorkInfoByIdLiveData(workRequest.id).observeForever { workInfo ->
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                val progress = workInfo.progress.getInt(ImportWorker.KEY_PROGRESS, 0)
                                _importProgress.value = progress
                                Log.d(TAG, "worker progress: $progress%")
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                Log.d(TAG, "worker completato con successo")
                                _importProgress.value = 100

                                //aspetta 1 secondo poi termina import
                                viewModelScope.launch {
                                    delay(1000)
                                    _isImporting.value = false

                                    //refresh automatico per aggiornare stats
                                    refreshFromBackend()

                                    Log.d(TAG, "import terminato -> isImporting=false, stats aggiornate")
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                Log.e(TAG, "worker fallito")
                                val errorMsg = workInfo.outputData.getString(ImportWorker.KEY_MESSAGE)
                                _message.value = "Errore import: $errorMsg"
                                _isImporting.value = false
                                _importProgress.value = 0
                            }
                            WorkInfo.State.CANCELLED -> {
                                Log.w(TAG, "worker cancellato")
                                _isImporting.value = false
                                _importProgress.value = 0
                            }
                            else -> {
                                Log.d(TAG, "worker state: ${workInfo.state}")
                            }
                        }
                    }
                }

                Log.d(TAG, "worker avviato con id: ${workRequest.id}")

            } catch (e: Exception) {
                Log.e(TAG, "errore startImport: ${e.message}", e)
                _message.value = "Errore avvio import: ${e.message}"
                _isImporting.value = false
            }
        }
    }

    /**
     * elimina tutti i film
     */
    fun clearAllMovies() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "eliminazione tutti i film...")

                //usa il metodo deleteAllUserMovies di ApiService
                val result = ApiService.deleteAllUserMovies()

                if (result.isSuccess) {
                    //refresh dal backend per aggiornare lista vuota
                    repository.refreshFromBackend()
                    _movies.value = emptyList()
                    _message.value = "Tutti i film eliminati"
                    Log.d(TAG, "tutti i film eliminati con successo")
                } else {
                    _message.value = "Errore eliminazione film"
                    Log.e(TAG, "errore eliminazione film: ${result.exceptionOrNull()?.message}")
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore clearAllMovies: ${e.message}", e)
                _message.value = "Errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}