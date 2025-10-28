package com.example.movieapp.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.parser.CsvProcessor
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.WebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class HomeViewModel : ViewModel() {
    private val TAG = "HomeViewModel"

    private var applicationContext: Context? = null
    private var movieRepository: MovieRepository? = null
    private val csvProcessor = CsvProcessor()
    private val webSocketService = WebSocketService.getInstance()

    //livedata
    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    //solo progress percentuale
    private val _importProgress = MutableLiveData<Int>()
    val importProgress: LiveData<Int> = _importProgress

    private val _isImporting = MutableLiveData<Boolean>()
    val isImporting: LiveData<Boolean> = _isImporting

    init {
        _isLoading.value = false
        _isImporting.value = false
        _importProgress.value = 0
        Log.d(TAG, "viewmodel inizializzato")
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        movieRepository = MovieRepository.getInstance(context)

        movieRepository?.movies?.observeForever { movies ->
            _movies.postValue(movies)
        }

        loadSavedMovies()

        //connetti websocket
        viewModelScope.launch {
            try {
                webSocketService.connect()
                delay(1000)
                if (webSocketService.isConnected()) {
                    Log.d(TAG, "websocket connesso")
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore websocket: ${e.message}")
            }
        }
    }

    private fun loadSavedMovies() {
        viewModelScope.launch {
            try {
                val savedMovies = movieRepository?.getAllMovies() ?: emptyList()
                _movies.postValue(savedMovies)
                Log.d(TAG, "caricati ${savedMovies.size} film salvati")
            } catch (e: Exception) {
                Log.e(TAG, "errore caricamento: ${e.message}")
            }
        }
    }

    fun refreshFromBackend() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = movieRepository?.refreshFromBackend() ?: false
                if (success) {
                    val movies = movieRepository?.movies?.value ?: emptyList()
                    _movies.postValue(movies)
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore refresh: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    //import csv

    fun processImdbWatchedCsv(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            processCsvFileAsync(inputStream, CsvType.IMDB_WATCHED)
        }
    }

    fun processImdbWatchlistCsv(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            processCsvFileAsync(inputStream, CsvType.IMDB_WATCHLIST)
        }
    }

    fun processLetterboxdWatchedCsv(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            processCsvFileAsync(inputStream, CsvType.LETTERBOXD_WATCHED)
        }
    }

    fun processLetterboxdWatchlistCsv(inputStream: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            processCsvFileAsync(inputStream, CsvType.LETTERBOXD_WATCHLIST)
        }
    }

    private suspend fun processCsvFileAsync(inputStream: InputStream, csvType: CsvType) {
        try {
            withContext(Dispatchers.Main) {
                _isImporting.value = true
                _importProgress.value = 0
            }

            Log.d(TAG, "parsing csv type: $csvType")

            //step 1: parsing
            val movies = withContext(Dispatchers.IO) {
                when (csvType) {
                    CsvType.IMDB_WATCHED -> csvProcessor.parseImdbWatchedCsv(inputStream).movies
                    CsvType.IMDB_WATCHLIST -> csvProcessor.parseImdbWatchlistCsv(inputStream).movies
                    CsvType.LETTERBOXD_WATCHED -> csvProcessor.parseLetterboxdWatchedCsv(inputStream).movies
                    CsvType.LETTERBOXD_WATCHLIST -> csvProcessor.parseLetterboxdWatchlistCsv(inputStream).movies
                }
            }

            if (movies.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _message.value = "nessun film trovato nel file"
                    _isImporting.value = false
                }
                return
            }

            Log.d(TAG, "parsing completato: ${movies.size} film")

            withContext(Dispatchers.Main) {
                _importProgress.value = 10 //parsing completato
            }

            //step 2: connetti websocket
            webSocketService.connect()
            delay(1000)

            withContext(Dispatchers.Main) {
                _importProgress.value = 15
            }

            //step 3: upload con chunking
            Log.d(TAG, "upload ${movies.size} film...")

            val isWatched = (csvType == CsvType.IMDB_WATCHED || csvType == CsvType.LETTERBOXD_WATCHED)
            val uploadSuccess = uploadMoviesInChunks(movies, isWatched)

            if (!uploadSuccess) {
                withContext(Dispatchers.Main) {
                    _message.value = "errore durante l'upload"
                    _isImporting.value = false
                }
                return
            }

            withContext(Dispatchers.Main) {
                _importProgress.value = 50 //upload completato
            }

            //step 4: monitora enrichment
            val enrichmentSuccess = monitorEnrichment(movies.size)

            if (enrichmentSuccess) {
                //step 5: aspetta e refresh
                Log.d(TAG, "aspetto 2 secondi prima del refresh...")
                delay(2000)

                withContext(Dispatchers.Main) {
                    _importProgress.value = 95
                }

                //refresh per aggiornare contatori
                val refreshSuccess = movieRepository?.refreshFromBackend() ?: false

                if (refreshSuccess) {
                    val finalMovies = movieRepository?.movies?.value ?: emptyList()

                    withContext(Dispatchers.Main) {
                        _movies.value = finalMovies
                        _importProgress.value = 100
                        _message.value = "import completato! ${finalMovies.size} film totali"
                    }

                    Log.d(TAG, "import completato: ${finalMovies.size} film")

                    //nascondi progress dopo 2 secondi
                    delay(2000)
                    withContext(Dispatchers.Main) {
                        _isImporting.value = false
                        _importProgress.value = 0
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _importProgress.value = 100
                        _message.value = "import completato"
                        delay(2000)
                        _isImporting.value = false
                        _importProgress.value = 0
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _message.value = "enrichment parzialmente completato"
                    _isImporting.value = false
                    _importProgress.value = 0
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "errore processing", e)
            withContext(Dispatchers.Main) {
                _message.value = "errore: ${e.message}"
                _isImporting.value = false
                _importProgress.value = 0
            }
        }
    }

    private suspend fun uploadMoviesInChunks(movies: List<Movie>, isWatched: Boolean): Boolean {
        val chunkSize = 50
        val chunks = movies.chunked(chunkSize)

        Log.d(TAG, "uploading ${chunks.size} chunks")

        chunks.forEachIndexed { index, chunk ->
            try {
                val watchlist = if (isWatched) emptyList() else chunk
                val watched = if (isWatched) chunk else emptyList()

                ApiService.batchUpload(watchlist, watched)

                //aggiorna progress (da 15% a 50%)
                val progress = 15 + ((index + 1) * 35 / chunks.size)
                withContext(Dispatchers.Main) {
                    _importProgress.value = progress
                }

                delay(500)
            } catch (e: Exception) {
                Log.e(TAG, "errore chunk ${index + 1}: ${e.message}")
            }
        }

        return true
    }

    private suspend fun monitorEnrichment(totalMovies: Int): Boolean {
        Log.d(TAG, "monitoring enrichment per $totalMovies film...")

        var lastProcessed = 0
        var lastUpdateTime = System.currentTimeMillis()
        val startTime = System.currentTimeMillis()
        val maxWaitTime = 30 * 60 * 1000L
        val noUpdateTimeout = 120 * 1000L

        while (true) {
            val update = webSocketService.enrichmentUpdates.value
            val currentTime = System.currentTimeMillis()

            if (update != null && update.processed > lastProcessed) {
                lastProcessed = update.processed
                lastUpdateTime = currentTime

                //aggiorna progress (da 50% a 90%)
                val enrichmentProgress = 50 + ((update.processed * 40) / update.total)
                withContext(Dispatchers.Main) {
                    _importProgress.value = enrichmentProgress
                }

                Log.d(TAG, "progress: ${update.processed}/${update.total} (${update.percentage}%)")

                if (update.type == "completed") {
                    Log.d(TAG, "enrichment completato!")
                    return true
                }
            }

            if (currentTime - startTime > maxWaitTime) {
                Log.w(TAG, "timeout massimo raggiunto")
                return lastProcessed > 0
            }

            if (currentTime - lastUpdateTime > noUpdateTimeout && lastProcessed > 0) {
                Log.w(TAG, "nessun update da 2 minuti ma ${lastProcessed} film ok")
                return true
            }

            delay(500)
        }
    }

    fun clearAllMovies() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                val userId = ApiService.getCurrentUserId()
                if (userId != null) {
                    ApiService.apiInterface.deleteAllUserMovies(userId)
                }

                movieRepository?.clearAll()
                _movies.value = emptyList()
                _message.value = "tutti i film eliminati"
                Log.d(TAG, "clear completato")
            } catch (e: Exception) {
                Log.e(TAG, "errore clear: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        applicationContext = null
        movieRepository = null
        Log.d(TAG, "viewmodel pulito")
    }
}

enum class CsvType {
    IMDB_WATCHED,
    IMDB_WATCHLIST,
    LETTERBOXD_WATCHED,
    LETTERBOXD_WATCHLIST
}