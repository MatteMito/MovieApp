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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.InputStream
import android.content.Context

/**
 * HomeViewModel OTTIMIZZATO
 *
 * ✅ FIX: WebSocket progress bar funzionante
 * ✅ FIX: Conteggio film sincronizzato (solo film salvati nel database)
 * ✅ UX: Stato import visibile sempre
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

    // ✅ NUOVO: Stato import dettagliato
    private val _importStatus = MutableLiveData<ImportStatus>()
    val importStatus: LiveData<ImportStatus> = _importStatus

    init {
        _isLoading.value = false
        _message.value = ""
        _enrichmentProgress.value = 0 to 0
        _importStatus.value = ImportStatus.IDLE

        // ✅ Ascolta aggiornamenti WebSocket
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

                            // Reset dopo 3 secondi
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

        // ✅ Ascolta stato connessione WebSocket
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

        // ✅ Observer: mostra SOLO film confermati dal database
        movieRepository?.movies?.observeForever { movies ->
            _movies.value = movies ?: emptyList()
            Log.d(TAG, "📊 Film dal database: ${movies?.size ?: 0}")
        }

        loadSavedMovies()
        testBackendConnectivity()
        connectWebSocket()

        Log.d(TAG, "✅ ViewModel inizializzato")
    }

    /**
     * 🔌 Connette WebSocket con retry
     */
    private fun connectWebSocket() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "🔌 Tentativo connessione WebSocket...")
                webSocketService.connect()

                // Attendi un po' per verificare connessione
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
                Log.d(TAG, "🔄 Sync manuale...")
                setLoading(true)

                val repository = movieRepository ?: run {
                    setMessage("❌ Errore inizializzazione")
                    setLoading(false)
                    return@launch
                }

                // Sincronizza dal database
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
                        })

                        Log.d(TAG, "✅ Sync completato")
                    } else {
                        setMessage("💡 Importa i tuoi film per iniziare")
                    }
                } else {
                    setMessage("⚠️ Impossibile sincronizzare")
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

                repository.loadMoviesFromDatabase()
                val localMovies = repository.getAllMovies()

                if (localMovies.isNotEmpty()) {
                    val enrichedCount = localMovies.count { it.tmdbId != null }
                    Log.d(TAG, "📚 Caricati ${localMovies.size} film ($enrichedCount arricchiti)")
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
                        Log.d(TAG, "✅ Auto-sync: ${backendMovies.size} film")
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
     *
     * ✅ FIX: NON aggiunge film localmente prima dell'enrichment
     * ✅ Film vengono aggiunti SOLO dopo essere stati salvati nel database
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
                _importStatus.postValue(ImportStatus.PARSING)

                val result = withContext(Dispatchers.IO) {
                    parser(inputStream)
                }

                if (result.successfulRows > 0) {
                    // ✅ NON aggiungere film localmente!
                    // repository.addMovies(result.movies)  ← RIMOSSO!

                    _importStatus.postValue(ImportStatus.SENDING_TO_BACKEND(result.movies.size))

                    setMessage(buildString {
                        appendLine("📤 Invio al backend...")
                        appendLine("${result.movies.size} film da processare")
                    })

                    performEnrichmentWithBackend(result.movies)
                } else {
                    setLoading(false)
                    _importStatus.postValue(ImportStatus.ERROR("File non valido"))
                    setMessage("❌ File non valido")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Eccezione import", e)
                setLoading(false)
                _importStatus.postValue(ImportStatus.ERROR(e.message ?: "Errore sconosciuto"))
                setMessage("❌ Errore durante l'importazione")
            }
        }
    }

    /**
     * ENRICHMENT con backend - FIXED VERSION
     *
     * ✅ Backend controlla database e arricchisce solo film nuovi
     * ✅ WebSocket invia progress real-time
     * ✅ Film aggiunti SOLO dopo conferma dal database
     * ✅ NUOVO: Force sync immediato dopo import per aggiornare stats
     */
    private suspend fun performEnrichmentWithBackend(movies: List<Movie>) {
        try {
            val repository = movieRepository ?: run {
                setMessage("❌ Errore inizializzazione")
                setLoading(false)
                return
            }

            Log.d(TAG, "╔═══════════════════════════════════╗")
            Log.d(TAG, "🎬 INIZIO ENRICHMENT")
            Log.d(TAG, "Film da processare: ${movies.size}")
            Log.d(TAG, "WebSocket: ${if (webSocketService.isConnected()) "CONNESSO" else "DISCONNESSO"}")
            Log.d(TAG, "╚═══════════════════════════════════╝")

            withContext(Dispatchers.Main) {
                _enrichmentProgress.value = 0 to movies.size
                _importStatus.postValue(ImportStatus.ENRICHING(0, movies.size, ""))
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

                    // ✅ Aggiorna local repository con film dal backend
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
                            isWatched = dto.isWatched,  // ← Preservato correttamente
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

                    // ✅ SOLO ADESSO aggiungiamo i film (già salvati nel database)
                    repository.replaceAll(enrichedMovies)

                    Log.d(TAG, "💾 Sincronizzati ${enrichedMovies.size} film dal database")

                    // ✅ NUOVO: Force sync immediato per aggiornare stats correttamente
                    Log.d(TAG, "🔄 Force sync dal database per stats aggiornate...")
                    delay(800)  // Piccolo delay per permettere al backend di completare il save

                    try {
                        syncWithBackend()  // ← Force sync immediato!
                        Log.d(TAG, "✅ Sync completato - stats aggiornate")
                    } catch (e: Exception) {
                        Log.w(TAG, "⚠️ Sync fallito (non critico): ${e.message}")
                    }

                    val summary = buildString {
                        appendLine("✅ Importazione completata!")
                        appendLine()
                        appendLine("📊 ${enrichmentResult.successfulMovies.size} film importati")
                        if (actuallyEnriched > 0) {
                            appendLine("✨ $actuallyEnriched con dettagli completi")
                        }
                        if (enrichmentResult.cacheHits > 0) {
                            appendLine("⚡ ${enrichmentResult.cacheHits} già in database")
                        }
                        appendLine()

                        // ✅ NUOVO: Mostra stats aggiornate nel messaggio
                        val stats = repository.getStats()
                        val total = stats["total"] ?: 0
                        val watched = stats["watched"] ?: 0
                        val watchlist = stats["watchlist"] ?: 0

                        appendLine("🎬 Totale collezione: $total film")
                        appendLine("   • $watched visti")
                        appendLine("   • $watchlist da vedere")
                        appendLine()
                        appendLine("💡 Vai su 'Statistiche' per i grafici!")
                    }

                    _message.value = summary
                    _importStatus.postValue(ImportStatus.COMPLETED(enrichedMovies.size))

                    Log.d(TAG, "╚═══════════════════════════════════╝")
                } else {
                    Log.e(TAG, "❌ Enrichment fallito")
                    _message.value = "⚠️ Alcuni dettagli non disponibili"
                    _importStatus.postValue(ImportStatus.ERROR("Enrichment parzialmente fallito"))
                }

                setLoading(false)

                // Reset progress dopo 5 secondi
                delay(5000)
                _enrichmentProgress.value = 0 to 0
                if (_importStatus.value !is ImportStatus.ERROR) {
                    _importStatus.postValue(ImportStatus.IDLE)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ ERRORE ENRICHMENT", e)
            e.printStackTrace()

            withContext(Dispatchers.Main) {
                _message.value = buildString {
                    appendLine("❌ Errore enrichment")
                    appendLine()
                    appendLine("${e.message}")
                }

                setLoading(false)
                _enrichmentProgress.value = 0 to 0
                _importStatus.postValue(ImportStatus.ERROR(e.message ?: "Errore sconosciuto"))
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

// === IMPORT STATUS ===

sealed class ImportStatus {
    object IDLE : ImportStatus()
    object PARSING : ImportStatus()
    data class SENDING_TO_BACKEND(val count: Int) : ImportStatus()
    data class ENRICHING(val processed: Int, val total: Int, val currentMovie: String) : ImportStatus()
    data class COMPLETED(val total: Int) : ImportStatus()
    data class ERROR(val message: String) : ImportStatus()
}