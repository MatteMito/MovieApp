package com.example.movieapp.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.parser.CsvProcessor
import com.example.movieapp.data.parser.CsvParseResult
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.ApiService
import com.example.movieapp.data.network.WebSocketService
// ❌ RIMOSSO: import com.example.movieapp.data.cache.CacheService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.InputStream
import android.content.Context

/**
 * HomeViewModel SEMPLIFICATO - senza CacheService ridondante
 *
 * Il database PostgreSQL È GIÀ la cache:
 * - Film nuovo → Query TMDB → Salva in DB
 * - Film esistente → Recupera da DB (già arricchito)
 * - Nessuna duplicazione di dati!
 */
class HomeViewModel : ViewModel() {
    private val TAG = "HomeViewModel"

    private var movieRepository: MovieRepository? = null
    private val csvProcessor = CsvProcessor()
    private var applicationContext: Context? = null

    // 🔌 WebSocket per progress real-time
    private val webSocketService = WebSocketService.getInstance()

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _message = MutableLiveData<String>()
    val message: LiveData<String> = _message

    private val _enrichmentProgress = MutableLiveData<Pair<Int, Int>>()
    val enrichmentProgress: LiveData<Pair<Int, Int>> = _enrichmentProgress

    init {
        _isLoading.value = false
        _message.value = ""
        _enrichmentProgress.value = 0 to 0

        // ✅ Ascolta aggiornamenti WebSocket per progress bar
        viewModelScope.launch {
            webSocketService.enrichmentUpdates.collect { update ->
                update?.let {
                    when (it.type) {
                        "progress" -> {
                            _enrichmentProgress.postValue(it.processed to it.total)
                            Log.d(TAG, "📊 Progress: ${it.processed}/${it.total} (${it.percentage}%)")
                        }
                        "completed" -> {
                            _enrichmentProgress.postValue(it.total to it.total)
                            Log.d(TAG, "✅ Enrichment completato")
                        }
                        "error" -> {
                            Log.e(TAG, "❌ Errore: ${it.message}")
                            _enrichmentProgress.postValue(0 to 0)
                        }
                    }
                }
            }
        }

        Log.d(TAG, "✅ HomeViewModel inizializzato (SENZA CacheService)")
    }

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        movieRepository = MovieRepository.getInstance(applicationContext)

        ApiService.initialize(applicationContext!!)

        // ❌ RIMOSSO: CacheService.getInstance(applicationContext!!).init(applicationContext!!)
        // Il database PostgreSQL è già la nostra cache!

        movieRepository?.movies?.observeForever { movies ->
            _movies.value = movies ?: emptyList()
        }

        loadSavedMovies()
        testBackendConnectivity()
        connectWebSocket()

        Log.d(TAG, "✅ ViewModel inizializzato - database è la cache")
    }

    /**
     * 🔌 Connette WebSocket per progress real-time
     */
    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Connessione WebSocket...")
                webSocketService.connect()
                delay(2000)

                if (webSocketService.isConnected()) {
                    Log.d(TAG, "✅ WebSocket connesso!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore WebSocket", e)
            }
        }
    }

    private fun testBackendConnectivity() {
        viewModelScope.launch {
            try {
                val isConnected = ApiService.testConnection()

                if (isConnected) {
                    Log.i(TAG, "✅ Backend raggiungibile")
                    setMessage("✅ Connesso")
                } else {
                    Log.w(TAG, "⚠️ Backend non raggiungibile")
                    setMessage("")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Errore test connettività", e)
            }
        }
    }

    fun forceBackendSync() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔄 Sync manuale dal database...")
                setLoading(true)

                val repository = movieRepository
                if (repository == null) {
                    setMessage("❌ Errore inizializzazione")
                    setLoading(false)
                    return@launch
                }

                // Recupera DIRETTAMENTE dal database (che È la cache!)
                val result = ApiService.getAllStoredMovies()

                if (result.isSuccess) {
                    val backendMovies = result.getOrNull() ?: emptyList()

                    if (backendMovies.isNotEmpty()) {
                        val enrichedCount = backendMovies.count { it.tmdbId != null }

                        repository.replaceAll(backendMovies)

                        setMessage(buildString {
                            appendLine("✅ Sincronizzazione completata!")
                            appendLine()
                            appendLine("📊 ${backendMovies.size} film")
                            if (enrichedCount > 0) {
                                appendLine("✨ $enrichedCount arricchiti")
                            }
                            appendLine()
                            appendLine("💾 Database PostgreSQL = Cache intelligente")
                        })

                        Log.d(TAG, "✅ Sync completato (da database)")
                    } else {
                        setMessage("💡 Importa i tuoi film per iniziare")
                    }
                } else {
                    setMessage("⚠️ Impossibile sincronizzare.\nRiprova più tardi.")
                }

                setLoading(false)

            } catch (e: Exception) {
                Log.e(TAG, "Errore sync", e)
                setMessage("❌ Errore di connessione")
                setLoading(false)
            }
        }
    }

    private fun loadSavedMovies() {
        viewModelScope.launch {
            try {
                val repository = movieRepository ?: return@launch

                // Carica dal database (che È la cache!)
                repository.loadMoviesFromDatabase()
                val localMovies = repository.getAllMovies()

                if (localMovies.isNotEmpty()) {
                    val enrichedCount = localMovies.count { it.tmdbId != null }
                    Log.d(TAG, "📚 Caricati ${localMovies.size} film ($enrichedCount arricchiti)")

                    if (enrichedCount > 0) {
                        setMessage("✅ ${localMovies.size} film caricati dal database")
                    }
                }

                if (applicationContext != null) {
                    syncWithBackend()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Errore caricamento", e)
            }
        }
    }

    private suspend fun syncWithBackend() {
        try {
            val repository = movieRepository ?: return

            val backendResult = ApiService.getAllStoredMovies()

            if (backendResult.isSuccess) {
                val backendMovies = backendResult.getOrNull() ?: emptyList()

                if (backendMovies.isNotEmpty()) {
                    val localMovies = repository.getAllMovies()

                    if (backendMovies.size > localMovies.size) {
                        repository.replaceAll(backendMovies)
                        setMessage("✅ Sincronizzazione completata")
                    }
                }
            }

        } catch (e: Exception) {
            Log.w(TAG, "Sync automatico fallito: ${e.message}")
        }
    }

    // ===== FUNZIONI IMPORT CSV =====

    fun processImdbWatchedCsv(inputStream: InputStream) {
        processCsv(inputStream) { csvProcessor.parseImdbWatchedCsv(it) }
    }

    fun processImdbWatchlistCsv(inputStream: InputStream) {
        processCsv(inputStream) { csvProcessor.parseImdbWatchlistCsv(it) }
    }

    fun processLetterboxdWatchedCsv(inputStream: InputStream) {
        processCsv(inputStream) { csvProcessor.parseLetterboxdWatchedCsv(it) }
    }

    fun processLetterboxdWatchlistCsv(inputStream: InputStream) {
        processCsv(inputStream) { csvProcessor.parseLetterboxdWatchlistCsv(it) }
    }

    /**
     * Funzione generica per processare CSV
     */
    private fun processCsv(
        inputStream: InputStream,
        parser: suspend (InputStream) -> CsvParseResult
    ) {
        val repository = movieRepository
        if (repository == null) {
            setMessage("❌ Errore inizializzazione")
            return
        }

        viewModelScope.launch {
            try {
                setLoading(true)

                val result = withContext(Dispatchers.IO) {
                    parser(inputStream)
                }

                if (result.successfulRows > 0) {
                    repository.addMovies(result.movies)
                    setMessage("⏳ Importazione in background...\n${result.movies.size} film")
                    performEnrichmentWithBackend(result.movies)
                } else {
                    setLoading(false)
                    setMessage("❌ File non valido")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione import", e)
                setLoading(false)
                setMessage("❌ Errore durante l'importazione")
            }
        }
    }

    /**
     * ENRICHMENT con backend
     *
     * Il backend controlla automaticamente se il film è già nel database:
     * - Film esiste → Restituisce dati dal DB (CACHE HIT!)
     * - Film nuovo → Query TMDB → Salva in DB → Restituisce dati
     *
     * Nessuna duplicazione! Il database È la cache.
     */
    private suspend fun performEnrichmentWithBackend(movies: List<Movie>) {
        try {
            val repository = movieRepository
            if (repository == null) {
                setMessage("❌ Errore inizializzazione")
                setLoading(false)
                return
            }

            Log.d(TAG, "╔═══════════════════════════════════╗")
            Log.d(TAG, "🎬 INIZIO ENRICHMENT")
            Log.d(TAG, "Film da processare: ${movies.size}")
            Log.d(TAG, "💾 Database = Cache intelligente")
            Log.d(TAG, "╚═══════════════════════════════════╝")

            withContext(Dispatchers.Main) {
                _enrichmentProgress.value = 0 to movies.size
            }

            Log.d(TAG, "📤 Chiamata API enrichment...")
            val result = withContext(Dispatchers.IO) {
                ApiService.enrichMoviesAutomatic(movies)
            }

            Log.d(TAG, "📥 Risposta ricevuta!")

            withContext(Dispatchers.Main) {
                if (result.isSuccess) {
                    val enrichmentResult = result.getOrNull()!!

                    val actuallyEnriched = enrichmentResult.successfulMovies.count { it.tmdbId != null }

                    Log.d(TAG, "✅ ENRICHMENT COMPLETATO:")
                    Log.d(TAG, "   Totali: ${enrichmentResult.totalProcessed}")
                    Log.d(TAG, "   Arricchiti: $actuallyEnriched")
                    Log.d(TAG, "   Cache hits: ${enrichmentResult.cacheHits}")
                    Log.d(TAG, "   Nuovi TMDB: ${actuallyEnriched - enrichmentResult.cacheHits}")
                    Log.d(TAG, "   Falliti: ${enrichmentResult.failedMovies.size}")

                    _enrichmentProgress.value = enrichmentResult.successfulMovies.size to enrichmentResult.successfulMovies.size

                    val enrichedMovies = enrichmentResult.successfulMovies.map { dto ->
                        Movie(
                            id = dto.id,
                            title = dto.title,
                            year = dto.year,
                            director = dto.director,
                            genres = dto.genres,
                            cast = dto.cast,
                            overview = dto.overview,
                            runtime = dto.runtime,
                            userRating = dto.userRating,
                            dateRated = dto.watchedDate,
                            isWatched = dto.isWatched,
                            source = try {
                                com.example.movieapp.data.models.DataSource.valueOf(dto.source)
                            } catch (_: Exception) {
                                com.example.movieapp.data.models.DataSource.UNKNOWN
                            },
                            tmdbId = dto.tmdbId,
                            posterUrl = dto.posterUrl,
                            backdropUrl = dto.backdropUrl,
                            tmdbRating = dto.tmdbRating,
                            voteCount = dto.voteCount
                        )
                    }

                    repository.replaceAll(enrichedMovies)

                    Log.d(TAG, "💾 Salvati ${enrichedMovies.size} film (ora nel database = cache)")

                    Log.d(TAG, "🔄 Auto-sync backend...")
                    delay(1000)

                    val syncResult = withContext(Dispatchers.IO) {
                        ApiService.getAllStoredMovies()
                    }

                    if (syncResult.isSuccess) {
                        val backendMovies = syncResult.getOrNull() ?: emptyList()

                        if (backendMovies.isNotEmpty()) {
                            Log.d(TAG, "✅ Sincronizzati ${backendMovies.size} film dal database")
                            repository.replaceAll(backendMovies)

                            val watchedCount = backendMovies.count { it.isWatched }
                            Log.d(TAG, "📊 Film watched: $watchedCount")
                        }
                    }

                    val summary = buildString {
                        appendLine("✅ Importazione completata!")
                        appendLine()
                        appendLine("📊 ${enrichmentResult.successfulMovies.size} film importati")
                        if (actuallyEnriched > 0) {
                            appendLine("✨ $actuallyEnriched con dettagli completi")
                        }
                        if (enrichmentResult.cacheHits > 0) {
                            appendLine("⚡ ${enrichmentResult.cacheHits} già in database (cache hit!)")
                        }
                        appendLine()
                        appendLine("💾 Database PostgreSQL = Cache intelligente")
                        appendLine("💡 Vai su 'Statistiche' per i grafici!")
                    }

                    _message.value = summary
                    Log.d(TAG, "╚═══════════════════════════════════╝")
                } else {
                    Log.e(TAG, "❌ Enrichment fallito")
                    _message.value = buildString {
                        appendLine("⚠️ Alcuni dettagli non disponibili")
                        appendLine()
                        appendLine("I film sono stati importati,")
                        appendLine("ma non è stato possibile")
                        appendLine("aggiungere tutti i dettagli.")
                    }
                }

                setLoading(false)

                delay(5000)
                _enrichmentProgress.value = 0 to 0
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRORE ENRICHMENT", e)
            e.printStackTrace()

            withContext(Dispatchers.Main) {
                _message.value = buildString {
                    appendLine("❌ Errore enrichment")
                    appendLine()
                    appendLine("${e.message}")
                    appendLine()
                    appendLine("Verifica connessione backend")
                }

                setLoading(false)
                _enrichmentProgress.value = 0 to 0
            }
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

            // Pulisce il database (che È la cache!)
            repository.clearAll()

            // ❌ RIMOSSO: CacheService.getInstance(applicationContext!!).clearCache()
            // Non serve più! Il database è già stato pulito.

            setMessage(buildString {
                appendLine("✅ Pulizia completata")
                appendLine()
                appendLine("$previousCount film rimossi dal database")
                appendLine()
                appendLine("💾 Cache = Database, tutto pulito!")
            })
        }
    }

    override fun onCleared() {
        super.onCleared()
        movieRepository?.cleanup()
        webSocketService.disconnect()

        Log.d(TAG, "🧹 HomeViewModel pulito (architettura semplificata!)")
    }
}