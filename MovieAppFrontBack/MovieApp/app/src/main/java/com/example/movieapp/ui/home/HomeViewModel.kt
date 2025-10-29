package com.example.movieapp.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.WebSocketService
import com.example.movieapp.data.parser.CsvProcessor
import com.example.movieapp.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.InputStream

class HomeViewModel : ViewModel() {

    private val TAG = "HomeViewModel"

    private var applicationContext: Context? = null
    private var movieRepository: MovieRepository? = null
    private val csvProcessor = CsvProcessor()
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
    }

    fun refreshFromBackend() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val success = movieRepository?.refreshFromBackend() ?: false

                if (success) {
                    val movies = movieRepository?.movies?.value ?: emptyList()
                    _movies.value = movies
                    _message.value = "dati aggiornati"
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

    fun processImdbWatchedCsv(inputStream: InputStream) {
        processCsv(inputStream, CsvType.IMDB_WATCHED)
    }

    fun processImdbWatchlistCsv(inputStream: InputStream) {
        processCsv(inputStream, CsvType.IMDB_WATCHLIST)
    }

    fun processLetterboxdWatchedCsv(inputStream: InputStream) {
        processCsv(inputStream, CsvType.LETTERBOXD_WATCHED)
    }

    fun processLetterboxdWatchlistCsv(inputStream: InputStream) {
        processCsv(inputStream, CsvType.LETTERBOXD_WATCHLIST)
    }

    private fun processCsv(inputStream: InputStream, csvType: CsvType) = viewModelScope.launch {
        try {
            withContext(Dispatchers.Main) {
                _isImporting.value = true
                _importProgress.value = 0
            }

            Log.d(TAG, "=== inizio import $csvType ===")

            //step 1: parsing csv (0-15%)
            withContext(Dispatchers.Main) {
                _importProgress.value = 5
            }

            val movies = withContext(Dispatchers.IO) {
                try {
                    when (csvType) {
                        CsvType.IMDB_WATCHED -> csvProcessor.parseImdbWatchedCsv(inputStream).movies
                        CsvType.IMDB_WATCHLIST -> csvProcessor.parseImdbWatchlistCsv(inputStream).movies
                        CsvType.LETTERBOXD_WATCHED -> csvProcessor.parseLetterboxdWatchedCsv(inputStream).movies
                        CsvType.LETTERBOXD_WATCHLIST -> csvProcessor.parseLetterboxdWatchlistCsv(inputStream).movies
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "errore parsing csv: ${e.message}", e)
                    emptyList()
                }
            }

            if (movies.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _message.value = "nessun film trovato nel file"
                    _isImporting.value = false
                    _importProgress.value = 0
                }
                return@launch
            }

            Log.d(TAG, "parsing completato: ${movies.size} film")

            withContext(Dispatchers.Main) {
                _importProgress.value = 15
            }

            //step 2: connetti websocket (15-20%)
            try {
                webSocketService.connect()
                delay(1500)

                withContext(Dispatchers.Main) {
                    _importProgress.value = 20
                }

                Log.d(TAG, "websocket connesso")
            } catch (e: Exception) {
                Log.w(TAG, "websocket non disponibile, continuo senza progress real-time")
            }

            //step 3: upload batch (20-50%)
            Log.d(TAG, "upload ${movies.size} film...")

            val isWatched = (csvType == CsvType.IMDB_WATCHED || csvType == CsvType.LETTERBOXD_WATCHED)
            val watchlistMovies = if (!isWatched) movies else emptyList()
            val watchedMovies = if (isWatched) movies else emptyList()

            withContext(Dispatchers.Main) {
                _importProgress.value = 30
            }

            val uploadSuccess = uploadMoviesBatch(watchlistMovies, watchedMovies)

            if (!uploadSuccess) {
                withContext(Dispatchers.Main) {
                    _message.value = "errore durante upload"
                    _isImporting.value = false
                    _importProgress.value = 0
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                _importProgress.value = 50
            }

            Log.d(TAG, "upload completato, inizio enrichment...")

            //step 4: monitora enrichment (50-90%)
            val enrichmentSuccess = monitorEnrichmentWithTimeout(movies.size)

            withContext(Dispatchers.Main) {
                _importProgress.value = 90
            }

            //step 5: refresh finale (90-100%)
            Log.d(TAG, "refresh finale...")
            delay(2000)

            val refreshSuccess = movieRepository?.refreshFromBackend() ?: false

            if (refreshSuccess) {
                val finalMovies = movieRepository?.movies?.value ?: emptyList()

                withContext(Dispatchers.Main) {
                    _movies.value = finalMovies
                    _importProgress.value = 100
                    _message.value = "import completato: ${finalMovies.size} film"
                    Log.d(TAG, "import completato con successo")
                }
            } else {
                withContext(Dispatchers.Main) {
                    _message.value = "import completato ma refresh fallito"
                    Log.w(TAG, "refresh finale fallito")
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "errore import: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _message.value = "errore: ${e.message}"
            }
        } finally {
            withContext(Dispatchers.Main) {
                _isImporting.value = false
                delay(1000)
                _importProgress.value = 0
            }
        }
    }

    private suspend fun uploadMoviesBatch(watchlist: List<Movie>, watched: List<Movie>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "batch upload: ${watchlist.size} watchlist + ${watched.size} watched")

                val result = ApiService.batchUpload(watchlist, watched)

                if (result.isSuccess) {
                    Log.d(TAG, "batch upload completato")
                    true
                } else {
                    Log.e(TAG, "batch upload fallito: ${result.exceptionOrNull()?.message}")
                    false
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore batch upload: ${e.message}", e)
                false
            }
        }
    }

    private suspend fun monitorEnrichmentWithTimeout(totalMovies: Int): Boolean {
        //timeout di 15 minuti per enrichment
        val result = withTimeoutOrNull(15 * 60 * 1000L) {
            monitorEnrichment(totalMovies)
        }

        return result ?: run {
            Log.w(TAG, "timeout enrichment dopo 15 minuti")
            false
        }
    }

    private suspend fun monitorEnrichment(totalMovies: Int): Boolean {
        Log.d(TAG, "monitoring enrichment per $totalMovies film")

        var lastProcessed = 0
        var stuckCount = 0
        val maxStuckCount = 40

        repeat(300) { iteration ->
            delay(1000)

            val update = webSocketService.enrichmentUpdates.value

            if (update != null && update.processed > lastProcessed) {
                lastProcessed = update.processed
                stuckCount = 0

                //calcola progress 50-90%
                val enrichmentProgress = 50 + ((update.processed * 40) / update.total)
                withContext(Dispatchers.Main) {
                    _importProgress.value = enrichmentProgress
                }

                Log.d(TAG, "progress: ${update.processed}/${update.total} (${update.percentage}%)")

                if (update.type == "completed") {
                    Log.d(TAG, "enrichment completato!")
                    return true
                }
            } else {
                stuckCount++
                if (stuckCount >= maxStuckCount) {
                    Log.w(TAG, "nessun aggiornamento per ${maxStuckCount}s, esco")
                    return false
                }
            }

            //log ogni 30 secondi
            if (iteration % 30 == 0) {
                Log.d(TAG, "attesa enrichment... (${iteration}s, processed: $lastProcessed)")
            }
        }

        Log.w(TAG, "timeout monitoring (5 minuti)")
        return false
    }

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

enum class CsvType {
    IMDB_WATCHED,
    IMDB_WATCHLIST,
    LETTERBOXD_WATCHED,
    LETTERBOXD_WATCHLIST
}