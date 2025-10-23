package com.example.movieapp.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.parser.CsvProcessor
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.WebSocketService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.InputStream
import android.content.Context

class HomeViewModel : ViewModel() {
    private val TAG = "HomeViewModel"

    private var movieRepository: MovieRepository? = null
    private val csvProcessor = CsvProcessor()
    private var applicationContext: Context? = null

    private val webSocketService = WebSocketService.getInstance()

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _enrichmentProgress = MutableLiveData<Pair<Int, Int>>()
    val enrichmentProgress: LiveData<Pair<Int, Int>> = _enrichmentProgress

    private val _importStatus = MutableLiveData<ImportStatus>()
    val importStatus: LiveData<ImportStatus> = _importStatus

    private val _fileCounters = MutableLiveData<Pair<Int, Int>>()
    val fileCounters: LiveData<Pair<Int, Int>> = _fileCounters

    private val _totalCounters = MutableLiveData<Pair<Int, Int>>()
    val totalCounters: LiveData<Pair<Int, Int>> = _totalCounters

    init {
        _isLoading.value = false
        _message.value = ""
        _enrichmentProgress.value = 0 to 0
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
                    setMessage("✅ Connesso (WebSocket attivo)")
                } else {
                    Log.w(TAG, "⚠️ WebSocket non connesso, riprovo...")
                    delay(3000)
                    webSocketService.connect()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore connessione WebSocket", e)
            }
        }
    }

    private fun testBackendConnectivity() {
        viewModelScope.launch {
            try {
                val isConnected = ApiService.testConnection()
                if (isConnected) {
                    Log.i(TAG, "✅ Backend raggiungibile")
                } else {
                    Log.w(TAG, "⚠️ Backend non raggiungibile")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Errore test connettività", e)
            }
        }
    }

    fun forceBackendSync() {
        viewModelScope.launch {
            try {
                if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                    setMessage("❌ Utente non autenticato")
                    return@launch
                }

                Log.d(TAG, "🔄 Sync manuale...")
                setLoading(true)

                val repository = movieRepository ?: run {
                    setMessage("❌ Errore inizializzazione")
                    setLoading(false)
                    return@launch
                }

                val result = ApiService.getUserStoredMovies()

                if (result.isSuccess) {
                    val backendMovies = result.getOrNull() ?: emptyList()

                    if (backendMovies.isNotEmpty()) {
                        val enrichedCount = backendMovies.count { it.tmdbId != null }
                        repository.replaceAll(backendMovies)

                        val watched = backendMovies.count { it.isWatched }
                        val watchlist = backendMovies.count { !it.isWatched }
                        _totalCounters.value = watched to watchlist

                        setMessage(buildString {
                            appendLine("✅ Sincronizzazione completata!")
                            appendLine()
                            appendLine("📊 ${backendMovies.size} film")
                            appendLine("   • $watched visti")
                            appendLine("   • $watchlist da vedere")
                            if (enrichedCount > 0) {
                                appendLine("✨ $enrichedCount arricchiti")
                            }
                        })
                    } else {
                        setMessage("ℹ️ nessun film salvato nel backend")
                    }
                } else {
                    setMessage("❌ ${result.exceptionOrNull()?.message ?: "errore sync"}")
                }

                setLoading(false)
            } catch (e: Exception) {
                Log.e(TAG, "Errore sync", e)
                setMessage("❌ ${e.message}")
                setLoading(false)
            }
        }
    }

    private fun loadSavedMovies() {
        viewModelScope.launch {
            try {
                if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                    Log.d(TAG, "⚠️ Utente non autenticato, skip load")
                    return@launch
                }

                Log.d(TAG, "📚 Caricamento film salvati...")
                movieRepository?.loadMoviesFromDatabase()
                delay(500)
                syncWithBackend()
            } catch (e: Exception) {
                Log.e(TAG, "Errore caricamento film", e)
            }
        }
    }

    private suspend fun syncWithBackend() {
        try {
            Log.d(TAG, "🔄 Sync con backend...")
            val result = ApiService.getUserStoredMovies()

            if (result.isSuccess) {
                val backendMovies = result.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    movieRepository?.replaceAll(backendMovies)
                    Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal backend")

                    val watched = backendMovies.count { it.isWatched }
                    val watchlist = backendMovies.count { !it.isWatched }

                    withContext(Dispatchers.Main) {
                        _totalCounters.value = watched to watchlist
                    }
                }
            } else {
                Log.w(TAG, "⚠️ Sync fallito: ${result.exceptionOrNull()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore sync: ${e.message}")
        }
    }

    // Metodi per processare CSV individuali (chiamati da HomeFragment)
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
            processAndUploadMovies(null, stream)
        }
    }

    fun processLetterboxdWatchlistCsv(stream: InputStream) {
        viewModelScope.launch {
            processAndUploadMovies(stream, null)
        }
    }

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

            // Parse watchlist
            val watchlistMovies = watchlistStream?.let { stream ->
                Log.d(TAG, "📄 parsing watchlist csv...")
                val result = csvProcessor.parseImdbWatchlistCsv(stream)
                result.movies
            } ?: emptyList()

            // Parse watched
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

            Log.d(TAG, "✅ BATCH UPLOAD COMPLETATO")
            Log.d(TAG, "═══════════════════════════════════════")
            Log.d(TAG, "📊 CONTATORI DAL FILE:")
            Log.d(TAG, "   Watched: ${batchResponse.counters.fromFile.watched}")
            Log.d(TAG, "   Watchlist: ${batchResponse.counters.fromFile.watchlist}")
            Log.d(TAG, "")
            Log.d(TAG, "📊 CONTATORI TOTALI:")
            Log.d(TAG, "   Watched: ${batchResponse.counters.afterRefresh.watched}")
            Log.d(TAG, "   Watchlist: ${batchResponse.counters.afterRefresh.watchlist}")

            withContext(Dispatchers.Main) {
                _totalCounters.value = batchResponse.counters.afterRefresh.watched to
                        batchResponse.counters.afterRefresh.watchlist
            }

            Log.d(TAG, "🔄 Sincronizzazione film dal backend...")
            delay(1000)

            try {
                val syncResult = ApiService.getUserStoredMovies()
                if (syncResult.isSuccess) {
                    val movies = syncResult.getOrNull()!!
                    movieRepository?.replaceAll(movies)
                    Log.d(TAG, "✅ Sincronizzati ${movies.size} film")
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
                _importStatus.postValue(ImportStatus.ERROR(e.message ?: "Errore sconosciuto"))
            }

            Result.failure(e)
        }
    }

    fun clearMessage() {
        _message.value = ""
    }

    private fun setLoading(loading: Boolean) {
        if (_isLoading.value != loading) {
            _isLoading.postValue(loading)
        }
    }

    private fun setMessage(message: String) {
        _message.postValue(message)
    }

    fun getStats(): Map<String, Int> {
        return movieRepository?.getStats() ?: emptyMap()
    }

    fun getCurrentBackendInfo(): Map<String, String> = AppConfig.getBackendInfo()

    fun clearAllData() {
        viewModelScope.launch {
            val repository = movieRepository ?: return@launch
            val previousCount = repository.getAllMovies().size
            repository.clearAll()

            setMessage(buildString {
                appendLine("✅ Pulizia completata")
                appendLine()
                appendLine("$previousCount film rimossi")
            })

            _importStatus.postValue(ImportStatus.IDLE)
        }
    }

    override fun onCleared() {
        super.onCleared()
        movieRepository?.cleanup()
        webSocketService.disconnect()
        Log.d(TAG, "🧹 HomeViewModel pulito")
    }
}

sealed class ImportStatus {
    object IDLE : ImportStatus()
    object PARSING : ImportStatus()
    data class SENDING_TO_BACKEND(val count: Int) : ImportStatus()
    data class ENRICHING(val processed: Int, val total: Int, val currentMovie: String) : ImportStatus()
    data class COMPLETED(val total: Int) : ImportStatus()
    data class ERROR(val message: String) : ImportStatus()
}