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

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _backendStatus = MutableLiveData<String>()
    val backendStatus: LiveData<String> = _backendStatus

    private val _importStatus = MutableLiveData<ImportStatus>()
    val importStatus: LiveData<ImportStatus> = _importStatus

    private val _enrichmentProgress = MutableLiveData<Pair<Int, Int>>()
    val enrichmentProgress: LiveData<Pair<Int, Int>> = _enrichmentProgress

    private val _fileCounters = MutableLiveData<Pair<Int, Int>>()
    val fileCounters: LiveData<Pair<Int, Int>> = _fileCounters

    private val _totalCounters = MutableLiveData<Pair<Int, Int>>()
    val totalCounters: LiveData<Pair<Int, Int>> = _totalCounters

    init {
        _isLoading.value = false
        _importStatus.value = ImportStatus.IDLE
        _enrichmentProgress.value = 0 to 0
        Log.d(TAG, "HomeViewModel inizializzato")
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        movieRepository = MovieRepository.getInstance(context)

        movieRepository?.movies?.observeForever { movies ->
            _movies.postValue(movies ?: emptyList())
        }

        loadSavedMovies()
        testBackendConnectivity()

        viewModelScope.launch {
            try {
                webSocketService.connect()
                delay(2000)

                if (webSocketService.isConnected()) {
                    Log.d(TAG, "✅ WebSocket connesso")
                } else {
                    Log.w(TAG, "⚠️ WebSocket non connesso dopo 2s")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore connessione WebSocket: ${e.message}")
            }
        }
    }

    private fun loadSavedMovies() {
        viewModelScope.launch {
            try {
                val savedMovies = movieRepository?.getAllMovies() ?: emptyList()
                _movies.postValue(savedMovies)

                if (savedMovies.isNotEmpty()) {
                    val watched = savedMovies.count { it.isWatched }
                    val watchlist = savedMovies.size - watched
                    _totalCounters.postValue(watched to watchlist)

                    Log.d(TAG, "📚 Caricati ${savedMovies.size} film salvati")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore caricamento film salvati", e)
            }
        }
    }

    private fun testBackendConnectivity() {
        viewModelScope.launch {
            try {
                val isHealthy = ApiService.testConnection()

                withContext(Dispatchers.Main) {
                    _backendStatus.value = if (isHealthy) "✅ Backend connesso" else "❌ Backend offline"
                }

                Log.d(TAG, if (isHealthy) "✅ Backend raggiungibile" else "⚠️ Backend non raggiungibile")
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _backendStatus.value = "❌ Backend offline"
                }
                Log.w(TAG, "Backend non raggiungibile: ${e.message}")
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                movieRepository?.clearAll()
                _movies.postValue(emptyList())
                _fileCounters.postValue(0 to 0)
                _totalCounters.postValue(0 to 0)
                _message.postValue("✅ Tutti i dati eliminati")
                Log.d(TAG, "🗑️ Tutti i dati eliminati")
            } catch (e: Exception) {
                Log.e(TAG, "Errore eliminazione dati", e)
                _message.postValue("❌ Errore eliminazione: ${e.message}")
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        _isLoading.postValue(loading)
    }

    /**
     * ✅ FIX: Refresh ottimizzato usando endpoint /stats
     */
    fun refreshFromBackend() {
        viewModelScope.launch {
            try {
                setLoading(true)
                Log.d(TAG, "🔄 Refresh da backend...")

                val statsResult = ApiService.getUserStats()

                if (statsResult.isSuccess) {
                    val stats = statsResult.getOrNull()

                    if (stats != null) {
                        val watched = stats.watched_count
                        val watchlist = stats.watchlist_count
                        val total = stats.total_movies

                        withContext(Dispatchers.Main) {
                            _totalCounters.value = watched to watchlist
                            _message.value = "✅ Aggiornato: $total film ($watched visti, $watchlist da vedere)"
                        }

                        Log.d(TAG, "✅ Stats aggiornate dal backend:")
                        Log.d(TAG, "   Totali: $total")
                        Log.d(TAG, "   Visti: $watched")
                        Log.d(TAG, "   Da vedere: $watchlist")

                        launch {
                            try {
                                val moviesResult = ApiService.getUserStoredMovies()
                                val backendMovies = moviesResult.getOrNull() ?: emptyList()

                                if (backendMovies.isNotEmpty()) {
                                    movieRepository?.replaceAll(backendMovies)
                                    withContext(Dispatchers.Main) {
                                        _movies.value = backendMovies
                                    }
                                    Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")
                                }
                            } catch (e: Exception) {
                                Log.w(TAG, "⚠️ Sync film fallito (contatori OK): ${e.message}")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            _message.value = "❌ Nessun dato ricevuto"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _message.value = "❌ Errore connessione backend"
                    }
                    Log.w(TAG, "❌ Refresh fallito: ${statsResult.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore refresh", e)
                withContext(Dispatchers.Main) {
                    _message.value = "❌ Errore: ${e.message}"
                }
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * ✅ Aggiorna contatori dal backend dopo import
     */
    private suspend fun updateCountersFromBackend() {
        try {
            val statsResult = ApiService.getUserStats()

            if (statsResult.isSuccess) {
                val stats = statsResult.getOrNull()

                if (stats != null) {
                    withContext(Dispatchers.Main) {
                        _totalCounters.value = stats.watched_count to stats.watchlist_count
                    }

                    Log.d(TAG, "📊 Contatori totali aggiornati:")
                    Log.d(TAG, "   Totali: ${stats.total_movies}")
                    Log.d(TAG, "   Visti: ${stats.watched_count}")
                    Log.d(TAG, "   Da vedere: ${stats.watchlist_count}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Impossibile aggiornare contatori: ${e.message}")
        }
    }

    fun processImdbWatchedCsv(stream: InputStream) {
        viewModelScope.launch {
            processAndUploadMovies(null, stream)
        }
    }

    fun processImdbWatchlistCsv(stream: InputStream) {
        viewModelScope.launch {
            processAndUploadMovies(stream, null)
        }
    }

    fun processLetterboxdWatchedCsv(stream: InputStream) {
        viewModelScope.launch {
            processLetterboxdFile(stream, isWatched = true)
        }
    }

    fun processLetterboxdWatchlistCsv(stream: InputStream) {
        viewModelScope.launch {
            processLetterboxdFile(stream, isWatched = false)
        }
    }

    private suspend fun processAndUploadMovies(
        watchlistStream: InputStream?,
        watchedStream: InputStream?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            setLoading(true)

            if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                withContext(Dispatchers.Main) {
                    _message.value = buildString {
                        appendLine("❌ Errore: utente non autenticato")
                        appendLine()
                        appendLine("Effettua il login per importare film")
                    }
                    setLoading(false)
                }
                return@withContext Result.failure(Exception("Utente non autenticato"))
            }

            val userId = ApiService.getCurrentUserId()!!
            Log.d(TAG, "📦 Upload per utente: $userId")

            withContext(Dispatchers.Main) {
                _importStatus.postValue(ImportStatus.PARSING)
            }

            val watchlistMovies = watchlistStream?.let { stream ->
                Log.d(TAG, "📄 parsing watchlist csv...")
                csvProcessor.parseImdbWatchlistCsv(stream).movies
            } ?: emptyList()

            val watchedMovies = watchedStream?.let { stream ->
                Log.d(TAG, "📄 parsing watched csv...")
                csvProcessor.parseImdbWatchedCsv(stream).movies
            } ?: emptyList()

            Log.d(TAG, "✅ Parsing completato:")
            Log.d(TAG, "   Watchlist: ${watchlistMovies.size}")
            Log.d(TAG, "   Watched: ${watchedMovies.size}")

            withContext(Dispatchers.Main) {
                _fileCounters.value = watchedMovies.size to watchlistMovies.size
            }

            if (watchlistMovies.isEmpty() && watchedMovies.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _message.value = "❌ nessun film trovato nei file csv"
                    setLoading(false)
                    _importStatus.postValue(ImportStatus.IDLE)
                }
                return@withContext Result.failure(Exception("no movies found"))
            }

            withContext(Dispatchers.Main) {
                _importStatus.postValue(
                    ImportStatus.SENDING_TO_BACKEND(watchlistMovies.size + watchedMovies.size)
                )
            }

            Log.d(TAG, "📤 Invio batch al backend...")

            // ✅ FIX: chiama batchUpload (non batchUploadMovies)
            val batchResult = ApiService.batchUpload(
                watchlist = watchlistMovies,
                watched = watchedMovies
            )

            if (batchResult.isFailure) {
                val errorMessage = batchResult.exceptionOrNull()?.message ?: "errore sconosciuto"
                withContext(Dispatchers.Main) {
                    _message.value = buildString {
                        appendLine("❌ Errore upload")
                        appendLine()
                        appendLine(errorMessage)
                    }
                    setLoading(false)
                    _importStatus.postValue(ImportStatus.ERROR(errorMessage))
                }
                return@withContext Result.failure(Exception(errorMessage))
            }

            val batchResponse = batchResult.getOrNull()!!
            Log.d(TAG, "✅ Batch upload completato!")

            try {
                val result = ApiService.getUserStoredMovies()
                val backendMovies = result.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    movieRepository?.replaceAll(backendMovies)
                    Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")

                    withContext(Dispatchers.Main) {
                        _movies.value = backendMovies
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Sync parzialmente fallito: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                val summary = buildString {
                    appendLine("✅ Importazione completata!")
                    appendLine()
                    appendLine("📥 File importato:")
                    appendLine("   • ${batchResponse.counters.fromFile.watched} film visti")
                    appendLine("   • ${batchResponse.counters.fromFile.watchlist} film da vedere")
                    appendLine()
                    appendLine("📊 Totale nella tua collezione:")
                    appendLine("   • ${batchResponse.counters.afterRefresh.watched} visti")
                    appendLine("   • ${batchResponse.counters.afterRefresh.watchlist} da vedere")
                    appendLine()

                    if (batchResponse.summary.totalEnriched > 0) {
                        appendLine("✨ ${batchResponse.summary.totalEnriched} con dettagli completi")
                    }
                    if (batchResponse.summary.cacheHitsTotal > 0) {
                        appendLine("⚡ ${batchResponse.summary.cacheHitsTotal} già in database")
                    }
                    appendLine()
                    appendLine("💡 Vai su 'Statistiche' per vedere i grafici aggiornati!")
                }

                _message.value = summary
                _importStatus.postValue(ImportStatus.COMPLETED(batchResponse.summary.totalMovies))
                _enrichmentProgress.postValue(
                    batchResponse.summary.totalMovies to batchResponse.summary.totalMovies
                )

                _totalCounters.value = batchResponse.counters.afterRefresh.watched to
                        batchResponse.counters.afterRefresh.watchlist
            }

            updateCountersFromBackend()
            setLoading(false)

            delay(5000)
            withContext(Dispatchers.Main) {
                _enrichmentProgress.value = 0 to 0
                if (_importStatus.value !is ImportStatus.ERROR) {
                    _importStatus.postValue(ImportStatus.IDLE)
                }
            }

            Result.success("Import completato con successo")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRORE UPLOAD", e)

            withContext(Dispatchers.Main) {
                _message.value = buildString {
                    appendLine("❌ Errore importazione")
                    appendLine()
                    appendLine("${e.message}")
                }
                setLoading(false)
                _enrichmentProgress.value = 0 to 0
                _importStatus.postValue(ImportStatus.ERROR(e.message ?: "unknown"))
            }

            Result.failure(e)
        }
    }

    private suspend fun processLetterboxdFile(
        stream: InputStream,
        isWatched: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            setLoading(true)

            if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                withContext(Dispatchers.Main) {
                    _message.value = buildString {
                        appendLine("❌ Errore: utente non autenticato")
                        appendLine()
                        appendLine("Effettua il login per importare film")
                    }
                    setLoading(false)
                }
                return@withContext Result.failure(Exception("Utente non autenticato"))
            }

            val userId = ApiService.getCurrentUserId()!!
            Log.d(TAG, "📦 Upload Letterboxd per utente: $userId")

            withContext(Dispatchers.Main) {
                _importStatus.postValue(ImportStatus.PARSING)
            }

            val result = if (isWatched) {
                csvProcessor.parseLetterboxdWatchedCsv(stream)
            } else {
                csvProcessor.parseLetterboxdWatchlistCsv(stream)
            }

            val movies = result.movies
            Log.d(TAG, "✅ Parsing Letterboxd completato: ${movies.size} film")

            if (movies.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _message.value = "❌ nessun film trovato nel file Letterboxd"
                    setLoading(false)
                    _importStatus.postValue(ImportStatus.IDLE)
                }
                return@withContext Result.failure(Exception("no movies found"))
            }

            val watchlistMovies = if (!isWatched) movies else emptyList()
            val watchedMovies = if (isWatched) movies else emptyList()

            withContext(Dispatchers.Main) {
                _fileCounters.value = watchedMovies.size to watchlistMovies.size
                _importStatus.postValue(ImportStatus.SENDING_TO_BACKEND(movies.size))
            }

            Log.d(TAG, "📤 Invio batch al backend...")

            // ✅ FIX: chiama batchUpload (non batchUploadMovies)
            val batchResult = ApiService.batchUpload(
                watchlist = watchlistMovies,
                watched = watchedMovies
            )

            if (batchResult.isFailure) {
                val errorMessage = batchResult.exceptionOrNull()?.message ?: "errore sconosciuto"
                withContext(Dispatchers.Main) {
                    _message.value = buildString {
                        appendLine("❌ Errore upload Letterboxd")
                        appendLine()
                        appendLine(errorMessage)
                    }
                    setLoading(false)
                    _importStatus.postValue(ImportStatus.ERROR(errorMessage))
                }
                return@withContext Result.failure(Exception(errorMessage))
            }

            val batchResponse = batchResult.getOrNull()!!
            Log.d(TAG, "✅ Batch Letterboxd upload completato!")

            try {
                val result = ApiService.getUserStoredMovies()
                val backendMovies = result.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    movieRepository?.replaceAll(backendMovies)
                    Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")

                    withContext(Dispatchers.Main) {
                        _movies.value = backendMovies
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Sync parzialmente fallito: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                val summary = buildString {
                    appendLine("✅ Importazione Letterboxd completata!")
                    appendLine()
                    appendLine("📥 File importato:")
                    appendLine("   • ${batchResponse.counters.fromFile.watched} film visti")
                    appendLine("   • ${batchResponse.counters.fromFile.watchlist} film da vedere")
                    appendLine()
                    appendLine("📊 Totale nella tua collezione:")
                    appendLine("   • ${batchResponse.counters.afterRefresh.watched} visti")
                    appendLine("   • ${batchResponse.counters.afterRefresh.watchlist} da vedere")
                    appendLine()

                    if (batchResponse.summary.totalEnriched > 0) {
                        appendLine("✨ ${batchResponse.summary.totalEnriched} con dettagli completi")
                    }
                    if (batchResponse.summary.cacheHitsTotal > 0) {
                        appendLine("⚡ ${batchResponse.summary.cacheHitsTotal} già in database")
                    }
                    appendLine()
                    appendLine("💡 Vai su 'Statistiche' per vedere i grafici aggiornati!")
                }

                _message.value = summary
                _importStatus.postValue(ImportStatus.COMPLETED(batchResponse.summary.totalMovies))
                _enrichmentProgress.postValue(
                    batchResponse.summary.totalMovies to batchResponse.summary.totalMovies
                )

                _totalCounters.value = batchResponse.counters.afterRefresh.watched to
                        batchResponse.counters.afterRefresh.watchlist
            }

            updateCountersFromBackend()
            setLoading(false)

            delay(5000)
            withContext(Dispatchers.Main) {
                _enrichmentProgress.value = 0 to 0
                if (_importStatus.value !is ImportStatus.ERROR) {
                    _importStatus.postValue(ImportStatus.IDLE)
                }
            }

            Result.success("Import Letterboxd completato con successo")

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRORE UPLOAD LETTERBOXD", e)

            withContext(Dispatchers.Main) {
                _message.value = buildString {
                    appendLine("❌ Errore importazione Letterboxd")
                    appendLine()
                    appendLine("${e.message}")
                }
                setLoading(false)
                _enrichmentProgress.value = 0 to 0
                _importStatus.postValue(ImportStatus.ERROR(e.message ?: "unknown"))
            }

            Result.failure(e)
        }
    }
}

sealed class ImportStatus {
    object IDLE : ImportStatus()
    object PARSING : ImportStatus()
    data class SENDING_TO_BACKEND(val totalMovies: Int) : ImportStatus()
    data class ENRICHING(val processed: Int, val total: Int, val currentMovie: String) : ImportStatus()
    data class COMPLETED(val totalMovies: Int) : ImportStatus()
    data class ERROR(val message: String) : ImportStatus()
}