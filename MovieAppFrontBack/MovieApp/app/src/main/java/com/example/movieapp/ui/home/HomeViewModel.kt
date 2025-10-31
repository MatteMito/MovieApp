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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * viewmodel per homefragment
 * gestisce import e statistiche
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

        //fix: osserva movies dal repository SENZA suspend
        repository.movies.observeForever { moviesList ->
            _movies.value = moviesList
            Log.d(TAG, "movies aggiornati da repository: ${moviesList.size}")
        }

        //carica iniziale
        loadMovies()

        //osserva websocket progress
        observeWebSocketProgress()
    }

    /**
     * carica film dal repository
     */
    fun loadMovies() {
        viewModelScope.launch {
            _isLoading.value = true

            //usa movies dal repository
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
                val success = repository.refreshFromBackend()
                if (success) {
                    //usa movies dal repository
                    val movies = repository.movies.value ?: emptyList()
                    _movies.value = movies
                    Log.d(TAG, "refresh completato: ${movies.size} film")
                } else {
                    _message.value = "errore refresh backend"
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore refresh: ${e.message}", e)
                _message.value = "errore refresh: ${e.message}"
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

                //reset progress
                _importProgress.value = 0

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
                workManager.enqueueUniqueWork(
                    "import_movies",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                Log.d(TAG, "work enqueued: ${workRequest.id}")

                //osserva progress
                observeWorkProgress(workManager, workRequest.id.toString())

                withContext(Dispatchers.Main) {
                    _isImporting.value = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore avvio import: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _message.value = "errore: ${e.message}"
                }
            }
        }
    }

    /**
     * osserva progress del worker
     */
    private fun observeWorkProgress(workManager: WorkManager, workId: String) {
        viewModelScope.launch(Dispatchers.Main) {
            workManager.getWorkInfoByIdLiveData(java.util.UUID.fromString(workId))
                .observeForever { workInfo ->
                    if (workInfo != null) {
                        Log.d(TAG, "work state: ${workInfo.state}, progress: ${workInfo.progress.getInt(ImportWorker.KEY_PROGRESS, 0)}")

                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                val progress = workInfo.progress.getInt(ImportWorker.KEY_PROGRESS, 0)
                                _importProgress.value = progress
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                _isImporting.value = false
                                _importProgress.value = 100
                                _message.value = "Import completato!"
                                Log.d(TAG, "import worker succeeded")

                                //refresh automatico dopo import
                                viewModelScope.launch {
                                    delay(1000)
                                    refreshFromBackend()
                                }
                            }
                            WorkInfo.State.FAILED -> {
                                _isImporting.value = false
                                _importProgress.value = 0
                                val errorMsg = workInfo.outputData.getString(ImportWorker.KEY_MESSAGE) ?: "Import fallito"
                                _message.value = errorMsg
                                Log.e(TAG, "import worker failed: $errorMsg")
                            }
                            WorkInfo.State.CANCELLED -> {
                                _isImporting.value = false
                                _importProgress.value = 0
                                _message.value = "Import annullato"
                                Log.w(TAG, "import worker cancelled")
                            }
                            else -> {
                                Log.d(TAG, "work state: ${workInfo.state}")
                            }
                        }
                    }
                }
        }
    }

    /**
     * osserva websocket progress per enrichment real-time
     */
    private fun observeWebSocketProgress() {
        viewModelScope.launch {
            webSocketService.enrichmentUpdates.collect { update ->
                update?.let {
                    //progress enrichment tra 40-95%
                    val currentProgress = _importProgress.value ?: 40

                    if (update.total > 0) {
                        //mappa enrichment progress su 40-95%
                        val enrichmentPercent = (update.processed.toFloat() / update.total * 100).toInt()
                        val mappedProgress = 40 + (enrichmentPercent * 55 / 100)

                        //aggiorna solo se progress aumenta
                        if (mappedProgress > currentProgress) {
                            _importProgress.value = mappedProgress
                            Log.d(TAG, "websocket progress: ${update.processed}/${update.total} -> $mappedProgress%")
                        }
                    }
                }
            }
        }
    }

    /**
     * fix: elimina tutti i film dell'utente
     */
    fun clearAllMovies() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                Log.d(TAG, "eliminazione tutti i film...")

                withContext(Dispatchers.IO) {
                    val result = ApiService.deleteAllUserMovies()

                    if (result.isSuccess) {
                        //aggiorna ui
                        withContext(Dispatchers.Main) {
                            _movies.value = emptyList()
                            _message.value = "tutti i film eliminati"
                            Log.d(TAG, "tutti i film eliminati con successo")
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _message.value = "errore eliminazione film"
                            Log.e(TAG, "errore eliminazione film")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore clearAllMovies: ${e.message}", e)
                _message.value = "errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "viewmodel cleared")
    }
}