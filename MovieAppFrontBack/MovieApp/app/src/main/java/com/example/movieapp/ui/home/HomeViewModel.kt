package com.example.movieapp.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.WebSocketService
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.repository.CsvType
import com.example.movieapp.data.repository.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

/**
 * viewmodel per home con import in background tramite workmanager
 */
class HomeViewModel : ViewModel() {

    private val TAG = "HomeViewModel"

    private var applicationContext: Context? = null
    private var movieRepository: MovieRepository? = null
    private val webSocketService = WebSocketService.getInstance()

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

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        movieRepository = MovieRepository.getInstance(context)

        //carica film dal database locale
        viewModelScope.launch {
            _isLoading.value = true
            movieRepository?.loadMoviesFromDatabase()
            val movies = movieRepository?.movies?.value ?: emptyList()
            _movies.value = movies
            _isLoading.value = false

            Log.d(TAG, "inizializzazione completata: ${movies.size} film caricati dal db")
        }

        //osserva progress websocket
        observeWebSocketProgress()
    }

    /**
     * refresh dati dal backend
     */
    fun refreshFromBackend() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = movieRepository?.refreshFromBackend() ?: false

                if (success) {
                    val movies = movieRepository?.movies?.value ?: emptyList()
                    _movies.value = movies
                    Log.d(TAG, "refresh completato: ${movies.size} film")
                } else {
                    _message.value = "errore refresh"
                    Log.e(TAG, "refresh fallito")
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore refresh: ${e.message}", e)
                _message.value = "errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * cancella tutti i film
     */
    fun clearAllMovies() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                //ottieni userId corrente
                val userId = ApiService.getCurrentUserId()
                if (userId == null) {
                    _message.value = "errore: utente non autenticato"
                    Log.e(TAG, "userId non disponibile per clearAllMovies")
                    _isLoading.value = false
                    return@launch
                }

                //chiama api per cancellare tutti i film dal backend
                val result = ApiService.deleteAllUserMovies(userId)

                if (result.isSuccess) {
                    _movies.value = emptyList()
                    _message.value = "tutti i film eliminati"
                    Log.d(TAG, "tutti i film eliminati con successo")
                } else {
                    _message.value = "errore eliminazione"
                    Log.e(TAG, "errore eliminazione film")
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore clear: ${e.message}", e)
                _message.value = "errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * avvia import in background usando workmanager
     */
    fun startImport(context: Context, inputStream: InputStream, filename: String, csvType: CsvType) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "preparazione import: $filename ($csvType)")

                //salva file temporaneo
                val tempFile = File(context.cacheDir, "temp_${System.currentTimeMillis()}.csv")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }

                Log.d(TAG, "file salvato: ${tempFile.absolutePath}")

                //crea work request
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val inputData = Data.Builder()
                    .putString(ImportWorker.KEY_FILE_PATH, tempFile.absolutePath)
                    .putString(ImportWorker.KEY_CSV_TYPE, csvType.name)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<ImportWorker>()
                    .setConstraints(constraints)
                    .setInputData(inputData)
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
                                Log.d(TAG, "work running: $progress%")
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                _isImporting.value = false
                                _importProgress.value = 100

                                //refresh per aggiornare ui
                                refreshFromBackend()

                                Log.d(TAG, "work completato con successo")
                            }
                            WorkInfo.State.FAILED -> {
                                _isImporting.value = false
                                _importProgress.value = 0
                                Log.e(TAG, "work fallito")
                            }
                            WorkInfo.State.CANCELLED -> {
                                _isImporting.value = false
                                _importProgress.value = 0
                                Log.w(TAG, "work annullato")
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
     * osserva progress websocket real-time
     */
    private fun observeWebSocketProgress() {
        viewModelScope.launch {
            webSocketService.enrichmentUpdates.collect { update ->
                if (update != null && _isImporting.value == true) {
                    //calcola progress: 40-95% per enrichment
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
}