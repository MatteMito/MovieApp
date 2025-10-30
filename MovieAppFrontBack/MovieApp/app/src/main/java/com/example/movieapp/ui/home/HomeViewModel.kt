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

        viewModelScope.launch {
            _isLoading.value = true
            movieRepository?.loadMoviesFromDatabase()
            val movies = movieRepository?.movies?.value ?: emptyList()
            _movies.value = movies
            _isLoading.value = false

            Log.d(TAG, "inizializzazione completata: ${movies.size} film")
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
                    //rimosso toast fastidioso
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
     * processa csv imdb watched
     */
    fun processImdbWatchedCsv(inputStream: InputStream) {
        startBackgroundImport(inputStream, CsvType.IMDB_WATCHED, "IMDB Watched")
    }

    /**
     * processa csv imdb watchlist
     */
    fun processImdbWatchlistCsv(inputStream: InputStream) {
        startBackgroundImport(inputStream, CsvType.IMDB_WATCHLIST, "IMDB Watchlist")
    }

    /**
     * processa csv letterboxd watched
     */
    fun processLetterboxdWatchedCsv(inputStream: InputStream) {
        startBackgroundImport(inputStream, CsvType.LETTERBOXD_WATCHED, "Letterboxd Watched")
    }

    /**
     * processa csv letterboxd watchlist
     */
    fun processLetterboxdWatchlistCsv(inputStream: InputStream) {
        startBackgroundImport(inputStream, CsvType.LETTERBOXD_WATCHLIST, "Letterboxd Watchlist")
    }

    /**
     * avvia import in background con workmanager
     */
    private fun startBackgroundImport(inputStream: InputStream, csvType: CsvType, typeName: String) {
        viewModelScope.launch {
            try {
                val context = applicationContext ?: return@launch

                Log.d(TAG, "=== avvio import background: $typeName ===")

                //salva file temporaneo
                val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.csv")
                tempFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }

                Log.d(TAG, "file salvato: ${tempFile.absolutePath}")

                //crea work request
                val inputData = Data.Builder()
                    .putString(ImportWorker.KEY_FILE_PATH, tempFile.absolutePath)
                    .putString(ImportWorker.KEY_CSV_TYPE, csvType.name)
                    .build()

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<ImportWorker>()
                    .setInputData(inputData)
                    .setConstraints(constraints)
                    .addTag("movie_import")
                    .build()

                //enqueue work
                val workManager = WorkManager.getInstance(context)
                workManager.enqueueUniqueWork(
                    "movie_import",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

                Log.d(TAG, "work enqueued: ${workRequest.id}")

                //osserva progress
                observeWorkProgress(workManager, workRequest.id.toString())

                withContext(Dispatchers.Main) {
                    //rimosso toast "import avviato"
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
                        when (workInfo.state) {
                            WorkInfo.State.RUNNING -> {
                                val progress = workInfo.progress.getInt(ImportWorker.KEY_PROGRESS, 0)
                                _importProgress.value = progress
                                Log.d(TAG, "work progress: $progress%")
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
                                _message.value = "import fallito"
                                Log.e(TAG, "work fallito")
                            }
                            WorkInfo.State.CANCELLED -> {
                                _isImporting.value = false
                                _importProgress.value = 0
                                _message.value = "import annullato"
                                Log.w(TAG, "work annullato")
                            }
                            else -> {
                                //enqueued, blocked
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
                    if (currentProgress >= 40 && currentProgress < 95) {
                        val enrichmentProgress = ((update.processed.toFloat() / update.total.toFloat()) * 55f).toInt()
                        val totalProgress = 40 + enrichmentProgress

                        _importProgress.value = totalProgress

                        Log.d(TAG, "websocket progress: ${update.processed}/${update.total} (${totalProgress}%)")
                    }
                }
            }
        }
    }

    /**
     * elimina tutti i film
     */
    fun clearAllMovies() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "inizio eliminazione tutti i film")
                _isLoading.value = true

                //elimina dal backend
                val userId = ApiService.getCurrentUserId()
                if (userId != null) {
                    Log.d(TAG, "eliminazione dal backend per user $userId")
                    val response = ApiService.apiInterface.deleteAllUserMovies(userId)

                    if (response.isSuccessful) {
                        Log.d(TAG, "eliminazione backend completata")
                    } else {
                        Log.e(TAG, "errore eliminazione backend: ${response.code()}")
                        withContext(Dispatchers.Main) {
                            _message.value = "errore eliminazione dal server"
                        }
                        return@launch
                    }
                } else {
                    Log.e(TAG, "user id null")
                    withContext(Dispatchers.Main) {
                        _message.value = "errore: utente non autenticato"
                    }
                    return@launch
                }

                //pulisci repository locale
                movieRepository?.clearAll()
                _movies.value = emptyList()

                withContext(Dispatchers.Main) {
                    _message.value = "tutti i film eliminati"
                }

                Log.d(TAG, "eliminazione completata")

            } catch (e: Exception) {
                Log.e(TAG, "errore eliminazione: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _message.value = "errore: ${e.message}"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }
}