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
            _movies.postValue(movies)
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

    // FILE: app/src/main/java/com/example/movieapp/ui/home/HomeViewModel.kt

// Trova il metodo uploadToBackend e modifica:

    private suspend fun uploadToBackend(watched: List<Movie>, watchlist: List<Movie>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val totalMovies = watched.size + watchlist.size
                Log.d(TAG, "📤 Upload ${totalMovies} film al backend...")

                _importStatus.postValue(ImportStatus.SENDING_TO_BACKEND(totalMovies))

                // 🔥 FIX: Aumenta timeout per file grandi
                val estimatedTime = totalMovies * 3 // 3 secondi per film
                val maxWaitTime = maxOf(estimatedTime * 1000L, 15 * 60 * 1000L) // Min 15 minuti

                Log.d(TAG, "⏱️ Timeout stimato: ${maxWaitTime / 1000 / 60} minuti per $totalMovies film")

                // Step 1: Avvia upload asincrono
                val uploadJob = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        Log.d(TAG, "🚀 Invio richiesta HTTP upload...")
                        ApiService.batchUpload(watched, watchlist)
                        Log.d(TAG, "✅ HTTP upload completato")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ HTTP upload error: ${e.message}")
                        // Non fallire - il WebSocket continua
                    }
                }

                // Step 2: Monitora WebSocket
                Log.d(TAG, "👀 Monitoring enrichment via WebSocket...")
                var lastProcessed = 0
                var lastUpdateTime = System.currentTimeMillis()
                val startTime = System.currentTimeMillis()
                var completed = false
                var noUpdateTimeout = 60 * 1000L // 60 secondi senza update = problema

                while (!completed) {
                    val update = webSocketService.enrichmentUpdates.value
                    val currentTime = System.currentTimeMillis()

                    if (update != null) {
                        // Update ricevuto
                        if (update.processed != lastProcessed) {
                            lastProcessed = update.processed
                            lastUpdateTime = currentTime

                            _enrichmentProgress.postValue(update.processed to update.total)
                            _importStatus.postValue(
                                ImportStatus.ENRICHING(update.processed, update.total, update.currentMovie)
                            )

                            Log.d(TAG, "📊 Progress: ${update.processed}/${update.total} (${update.percentage}%)")
                        }

                        // Check completion
                        if (update.type == "completed") {
                            Log.d(TAG, "✅ Enrichment completato via WebSocket!")
                            completed = true
                            break
                        }

                        // Check error
                        if (update.type == "error") {
                            Log.e(TAG, "❌ Errore enrichment: ${update.message}")
                            _importStatus.postValue(ImportStatus.ERROR(update.message))
                            return@withContext false
                        }
                    }

                    // 🔥 Check timeout senza update (possibile freeze)
                    if (currentTime - lastUpdateTime > noUpdateTimeout && lastProcessed > 0) {
                        Log.w(TAG, "⚠️ Nessun update da 60 secondi, ma proseguo...")
                        // Non interrompiamo, solo logghiamo
                    }

                    // 🔥 Timeout totale
                    if (currentTime - startTime > maxWaitTime) {
                        Log.e(TAG, "⏱️ Timeout totale (${maxWaitTime/1000/60} min)")

                        // 🔥 NUOVO: Anche in caso di timeout, prova a recuperare i dati
                        if (lastProcessed > 0) {
                            Log.w(TAG, "⚠️ Timeout ma ${lastProcessed} film processati - recupero dati...")
                            // Continua al refresh per salvare quello che è stato fatto
                            completed = true
                            break
                        } else {
                            _importStatus.postValue(ImportStatus.ERROR("Timeout: operazione troppo lunga"))
                            return@withContext false
                        }
                    }

                    delay(500) // Check ogni 500ms
                }

                // Step 3: Refresh dal backend
                Log.d(TAG, "🔄 Download dati aggiornati dal backend...")
                delay(2000) // Delay per sicurezza

                val refreshSuccess = movieRepository?.refreshFromBackend() ?: false

                if (refreshSuccess) {
                    val updatedMovies = movieRepository?.movies?.value ?: emptyList()
                    _movies.postValue(updatedMovies)

                    val watchedCount = updatedMovies.count { it.isWatched }
                    val watchlistCount = updatedMovies.count { !it.isWatched }
                    _totalCounters.postValue(watchedCount to watchlistCount)

                    Log.d(TAG, "✅ Dati aggiornati: ${updatedMovies.size} film")
                    Log.d(TAG, "   Watched: $watchedCount, Watchlist: $watchlistCount")

                    _importStatus.postValue(ImportStatus.COMPLETED(updatedMovies.size))
                    _message.postValue("✅ Import completato: ${updatedMovies.size} film")
                } else {
                    Log.e(TAG, "❌ Refresh backend fallito!")
                    _importStatus.postValue(ImportStatus.ERROR("Dati salvati ma refresh fallito"))
                    return@withContext false
                }

                uploadJob.cancel()
                true

            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore upload", e)
                _importStatus.postValue(ImportStatus.ERROR(e.message ?: "Errore sconosciuto"))
                _message.postValue("❌ Errore: ${e.message}")
                false
            }
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

            val total = watchlistMovies.size + watchedMovies.size

            withContext(Dispatchers.Main) {
                _importStatus.postValue(ImportStatus.SENDING_TO_BACKEND(total))
            }

            // ✅ Step 1: Connetti WebSocket PRIMA dell'upload
            webSocketService.connect()
            delay(1000)

            if (!webSocketService.isConnected()) {
                Log.e(TAG, "❌ WebSocket non connesso!")
            }

            Log.d(TAG, "📤 Invio batch al backend...")

            // ✅ Step 2: Avvia upload asincrono (non aspettare)
            val uploadJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    Log.d(TAG, "🚀 Invio HTTP...")
                    ApiService.batchUpload(
                        watchlist = watchlistMovies,
                        watched = watchedMovies
                    )
                    Log.d(TAG, "✅ HTTP completato")
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ HTTP terminato: ${e.message}")
                }
            }

            // ✅ Step 3: Monitora WebSocket
            Log.d(TAG, "👀 Monitoring WebSocket...")
            var lastProcessed = 0
            val startTime = System.currentTimeMillis()
            val maxWaitTime = 10 * 60 * 1000L
            var completed = false

            while (!completed) {
                val update = webSocketService.enrichmentUpdates.value

                if (update != null) {
                    if (update.processed != lastProcessed) {
                        lastProcessed = update.processed
                        _enrichmentProgress.postValue(update.processed to update.total)
                        Log.d(TAG, "📊 Progress: ${update.processed}/${update.total}")
                    }

                    if (update.type == "completed") {
                        Log.d(TAG, "✅ Enrichment completato!")
                        completed = true
                        break
                    }

                    if (update.type == "error") {
                        Log.e(TAG, "❌ Errore: ${update.message}")
                        _importStatus.postValue(ImportStatus.ERROR(update.message))
                        return@withContext Result.failure(Exception(update.message))
                    }
                }

                if (System.currentTimeMillis() - startTime > maxWaitTime) {
                    Log.e(TAG, "⏱️ Timeout")
                    _importStatus.postValue(ImportStatus.ERROR("Timeout"))
                    return@withContext Result.failure(Exception("Timeout"))
                }

                delay(500)
            }

            // ✅ Step 4: Refresh dal backend
            Log.d(TAG, "🔄 Downloading dal backend...")
            delay(2000)

            val refreshSuccess = movieRepository?.refreshFromBackend() ?: false

            if (refreshSuccess) {
                val updatedMovies = movieRepository?.movies?.value ?: emptyList()
                _movies.postValue(updatedMovies)

                val watched = updatedMovies.count { it.isWatched }
                val watchlist = updatedMovies.count { !it.isWatched }
                _totalCounters.postValue(watched to watchlist)

                Log.d(TAG, "✅ Dati aggiornati: ${updatedMovies.size} film")

                val summary = buildString {
                    appendLine("✅ Import completato!")
                    appendLine()
                    appendLine("📊 Totale film: ${updatedMovies.size}")
                    appendLine("✓ Visti: $watched")
                    appendLine("✓ Da vedere: $watchlist")
                }

                _message.value = summary
                _importStatus.postValue(ImportStatus.COMPLETED(updatedMovies.size))
                _enrichmentProgress.postValue(updatedMovies.size to updatedMovies.size)
            } else {
                Log.e(TAG, "❌ Refresh fallito!")
                _importStatus.postValue(ImportStatus.ERROR("Refresh fallito"))
                return@withContext Result.failure(Exception("Refresh fallito"))
            }

            uploadJob.cancel()

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