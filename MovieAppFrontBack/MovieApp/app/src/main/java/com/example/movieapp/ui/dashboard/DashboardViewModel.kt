package com.example.movieapp.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.MovieRepository
import android.util.Log
import android.content.Context
import kotlinx.coroutines.launch

/**
 * dashboardviewmodel ottimizzatoc che utilizza appconfig per info backend
 */
class DashboardViewModel : ViewModel() {

    private var movieRepository: MovieRepository? = null
    private var isInitialized = false

    private val _statsText = MutableLiveData<String>()
    val statsText: LiveData<String> = _statsText

    private val _movieCount = MutableLiveData<String>()
    val movieCount: LiveData<String> = _movieCount

    private val _watchHours = MutableLiveData<String>()
    val watchHours: LiveData<String> = _watchHours

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val TAG = "DashboardViewModel"

    init {
        _statsText.value = "inizializzazione backend ${AppConfig.BACKEND_HOST}..."
        _movieCount.value = "0"
        _watchHours.value = "0h"
        _movies.value = emptyList()

        Log.d(TAG, "dashboardviewmodel inizializzato")
        Log.d(TAG, "backend: ${AppConfig.BACKEND_HOST}:${AppConfig.BACKEND_PORT}")
    }

    /**
     * inizializza con context
     */
    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            movieRepository = MovieRepository.getInstance(context)

            movieRepository?.movies?.observeForever { movies ->
                _movies.value = movies ?: emptyList()
                viewModelScope.launch {
                    updateStats()
                }
            }

            //carica da database
            viewModelScope.launch {
                movieRepository?.loadMoviesFromDatabase()
                updateStats()
            }

            isInitialized = true
            Log.d(TAG, "dashboard inizializzata")

        } catch (e: Exception) {
            Log.e(TAG, "errore inizializzazione", e)
            _statsText.value = "errore backend ${AppConfig.BACKEND_HOST}: ${e.message}"
        }
    }

    private suspend fun updateStats() {
        val repository = movieRepository
        if (repository == null) {
            _statsText.value = "inizializzazione in corso..."
            _movieCount.value = "0"
            _watchHours.value = "0h"
            return
        }

        val movies = repository.getAllMovies()

        if (movies.isEmpty()) {
            _statsText.value = """
            nessun film nella tua collezione
            
            per iniziare:
            • vai alla scheda home
            • importa i tuoi film da imdb o letterboxd
            • torna qui per vedere le statistiche!
        """.trimIndent()
            _movieCount.value = "0"
            _watchHours.value = "0h"
            return
        }

        val watchedMovies = movies.filter { it.isWatched }
        val watchlistMovies = movies.filter { !it.isWatched }

        _movieCount.value = movies.size.toString()

        val totalMinutes = watchedMovies.mapNotNull { it.runtime }.sum()
        val totalHours = totalMinutes / 60
        _watchHours.value = "${totalHours}h"

        val stats = repository.getStats()
        val enrichedCount = stats["enriched"] ?: 0

        val statsBuilder = StringBuilder()

        //header semplificato
        statsBuilder.append("le tue statistiche\n\n")

        statsBuilder.append("panoramica\n\n")
        statsBuilder.append("film totali: ${movies.size}\n")
        statsBuilder.append("• visti: ${watchedMovies.size}\n")
        statsBuilder.append("• da vedere: ${watchlistMovies.size}\n")
        if (enrichedCount > 0) {
            statsBuilder.append("• con dettagli: $enrichedCount\n")
        }
        statsBuilder.append("• tempo visione: ${totalHours}h\n\n")

        //valutazioni
        val averageRating = watchedMovies.mapNotNull { it.userRating }.takeIf { it.isNotEmpty() }?.average()

        if (averageRating != null) {
            statsBuilder.append("valutazioni\n\n")
            statsBuilder.append("voto medio: ${String.format("%.1f", averageRating)}/10\n\n")
        }

        //top generi
        val topGenres = watchedMovies
            .flatMap { it.genres }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        if (topGenres.isNotEmpty()) {
            statsBuilder.append("generi preferiti\n\n")
            topGenres.forEach { (genre, count) ->
                statsBuilder.append("$genre: $count film\n")
            }
            statsBuilder.append("\n")
        }

        //top registi
        val topDirectors = watchedMovies
            .mapNotNull { it.director }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(3)

        if (topDirectors.isNotEmpty()) {
            statsBuilder.append("registi più visti\n\n")
            topDirectors.forEach { (director, count) ->
                statsBuilder.append("$director: $count film\n")
            }
        }

        _statsText.value = statsBuilder.toString().trim()
    }

    /**
     * statistiche avanzate
     */
    private fun addAdvancedStats(builder: StringBuilder, watchedMovies: List<Movie>) {
        builder.append("\nanalisi avanzate\n\n")

        val longestMovies = watchedMovies
            .filter { it.runtime != null && it.runtime > 0 }  // ✅ FIXED: Rimosso !!
            .sortedByDescending { it.runtime }
            .take(3)

        if (longestMovies.isNotEmpty()) {
            builder.append("film più lunghi:\n")
            longestMovies.forEach { movie ->
                val runtime = movie.runtime ?: 0
                val hours = runtime / 60
                val minutes = runtime % 60
                builder.append("${movie.title}: ${hours}h ${minutes}m\n")
            }
            builder.append("\n")
        }

        val ratingDiscrepancies = watchedMovies
            .filter { it.userRating != null && it.tmdbRating != null }
            .mapNotNull { movie ->
                val userRating = movie.userRating ?: return@mapNotNull null
                val tmdbRating = movie.tmdbRating ?: return@mapNotNull null
                val difference = kotlin.math.abs(userRating - tmdbRating)
                Triple(movie, userRating, difference)
            }
            .sortedByDescending { it.third }
            .take(3)

        if (ratingDiscrepancies.isNotEmpty()) {
            builder.append("rating più diversi da tmdb:\n")
            ratingDiscrepancies.forEach { (movie, userRating, difference) ->
                val diff = String.format("%.1f", difference)
                val userStr = String.format("%.1f", userRating)
                val tmdbStr = String.format("%.1f", movie.tmdbRating)
                builder.append("${movie.title}: tuo $userStr vs tmdb $tmdbStr (diff $diff)\n")
            }
            builder.append("\n")
        }

        val topCountries = watchedMovies
            .flatMap { it.productionCountries }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        if (topCountries.isNotEmpty()) {
            builder.append("paesi produzione:\n")
            topCountries.forEach { (country, count) ->
                builder.append("$country: $count film\n")
            }
        }
    }

    /**
     * refresh stats
     */
    fun refreshStats() {
        viewModelScope.launch {
            updateStats()
            Log.d(TAG, "refresh stats forzato")
        }
    }

    /**
     * ha dati?
     */
    fun hasData(): Boolean {
        return movieRepository?.getAllMovies()?.isNotEmpty() ?: false
    }

    /**
     * content counts
     */
    suspend fun getContentCounts(): Map<String, Int> {
        val repository = movieRepository
        if (repository == null) {
            return mapOf(
                "total" to 0,
                "watched" to 0,
                "watchlist" to 0,
                "enriched" to 0,
                "imdb" to 0,
                "letterboxd" to 0
            )
        }

        return repository.getStats()
    }
}