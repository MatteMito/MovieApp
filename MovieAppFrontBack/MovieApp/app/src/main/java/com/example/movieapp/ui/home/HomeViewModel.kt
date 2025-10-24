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
        _fileCounters.value = 0 to 0
        _totalCounters.value = 0 to 0

        viewModelScope.launch {
            webSocketService.enrichmentUpdates.collect { update ->
                update?.let {
                    Log.d(TAG, "📩 WebSocket update: ${it.type} - ${it.processed}/${it.total}")

                    when (it.type) {
                        "progress" -> {
                            _enrichmentProgress.postValue(it.processed to it.total)
                            _importStatus.postValue(ImportStatus.ENRICHING(it.processed, it.total, it.currentMovie))
                            Log.d(TAG, "📊 Progress aggiornato: ${it.processed}/${it.total} (${it.percentage}%)")
                        }
                        "completed" -> {
                            _enrichmentProgress.postValue(it.total to it.total)
                            _importStatus.postValue(ImportStatus.COMPLETED(it.total))
                            Log.d(TAG, "✅ Enrichment completato: ${it.total} film")

                            viewModelScope.launch {
                                delay(3000)
                                _importStatus.postValue(ImportStatus.IDLE)
                                _enrichmentProgress.postValue(0 to 0)
                            }
                        }
                        "error" -> {
                            Log.e(TAG, "❌ Errore enrichment: ${it.message}")
                            _enrichmentProgress.postValue(0 to 0)
                            _importStatus.postValue(ImportStatus.ERROR(it.message))
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            webSocketService.connectionStatus.collect { status ->
                Log.d(TAG, "🔌 WebSocket status: $status")
            }
        }

        Log.d(TAG, "✅ HomeViewModel inizializzato con WebSocket support")
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        movieRepository = MovieRepository.getInstance(applicationContext)

        ApiService.initialize(applicationContext!!)

        movieRepository?.movies?.observeForever { movies ->
            _movies.value = movies ?: emptyList()
            Log.d(TAG, "📊 Film dal database: ${movies?.size ?: 0}")
        }

        loadSavedMovies()
        testBackendConnectivity()
        connectWebSocket()

        Log.d(TAG, "✅ ViewModel inizializzato")
    }

    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Tentativo connessione WebSocket...")
                webSocketService.connect()

                delay(2000)

                if (webSocketService.isConnected()) {
                    Log.d(TAG, "✅ WebSocket CONNESSO con successo!")
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

    fun refreshFromBackend() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Refresh da backend...")
                val result = ApiService.getUserStoredMovies()

                val backendMovies = result.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    movieRepository?.replaceAll(backendMovies)
                    Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")

                    val watched = backendMovies.count { it.isWatched }
                    val watchlist = backendMovies.count { !it.isWatched }

                    withContext(Dispatchers.Main) {
                        _totalCounters.value = watched to watchlist
                        _movies.value = backendMovies  // ✅ AGGIORNAMENTO CRITICO
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Sync parzialmente fallito: ${e.message}")
            }
        }
    }

    // ✅ METODI IMDB
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

    // ✅ METODI LETTERBOXD
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

    /**
     * METODO PER IMDB
     */
    suspend fun processAndUploadMovies(
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
                val result = csvProcessor.parseImdbWatchlistCsv(stream)
                result.movies
            } ?: emptyList()

            val watchedMovies = watchedStream?.let { stream ->
                Log.d(TAG, "📄 parsing watched csv...")
                val result = csvProcessor.parseImdbWatchedCsv(stream)
                result.movies
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

            val batchResult = ApiService.batchUpload(watchlistMovies, watchedMovies)

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

            // ✅ SYNC CON AGGIORNAMENTO FORZATO
            try {
                val result = ApiService.getUserStoredMovies()
                val backendMovies = result.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    movieRepository?.replaceAll(backendMovies)
                    Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")

                    val watched = backendMovies.count { it.isWatched }
                    val watchlist = backendMovies.count { !it.isWatched }

                    withContext(Dispatchers.Main) {
                        _totalCounters.value = watched to watchlist
                        _movies.value = backendMovies  // ✅ AGGIORNAMENTO CRITICO
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

                    val totalEnriched = batchResponse.summary.totalEnriched
                    if (totalEnriched > 0) {
                        appendLine("✨ $totalEnriched con dettagli completi")
                    }
                    if (batchResponse.summary.cacheHitsTotal > 0) {
                        appendLine("⚡ ${batchResponse.summary.cacheHitsTotal} già in database")
                    }
                    appendLine()
                    appendLine("💡 Vai su 'Statistiche' per vedere i grafici aggiornati!")
                }

                _message.value = summary
                _importStatus.postValue(
                    ImportStatus.COMPLETED(batchResponse.summary.totalMovies)
                )
            }

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
            e.printStackTrace()

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

    /**
     * METODO PER LETTERBOXD
     */
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

            Log.d(TAG, "📄 parsing Letterboxd ${if (isWatched) "watched" else "watchlist"} csv...")

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
                _importStatus.postValue(
                    ImportStatus.SENDING_TO_BACKEND(movies.size)
                )
            }

            Log.d(TAG, "📤 Invio batch al backend...")

            val batchResult = ApiService.batchUpload(watchlistMovies, watchedMovies)

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

            // ✅ SYNC CON AGGIORNAMENTO FORZATO
            try {
                val result = ApiService.getUserStoredMovies()
                val backendMovies = result.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    movieRepository?.replaceAll(backendMovies)
                    Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")

                    val watched = backendMovies.count { it.isWatched }
                    val watchlist = backendMovies.count { !it.isWatched }

                    withContext(Dispatchers.Main) {
                        _totalCounters.value = watched to watchlist
                        _movies.value = backendMovies  // ✅ AGGIORNAMENTO CRITICO
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

                    val totalEnriched = batchResponse.summary.totalEnriched
                    if (totalEnriched > 0) {
                        appendLine("✨ $totalEnriched con dettagli completi")
                    }
                    if (batchResponse.summary.cacheHitsTotal > 0) {
                        appendLine("⚡ ${batchResponse.summary.cacheHitsTotal} già in database")
                    }
                    appendLine()
                    appendLine("💡 Vai su 'Statistiche' per vedere i grafici aggiornati!")
                }

                _message.value = summary
                _importStatus.postValue(
                    ImportStatus.COMPLETED(batchResponse.summary.totalMovies)
                )
            }

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
            e.printStackTrace()

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