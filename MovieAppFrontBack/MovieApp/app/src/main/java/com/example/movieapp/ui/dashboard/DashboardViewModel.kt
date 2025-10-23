package com.example.movieapp.ui.dashboard

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.config.AppConfig
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.ApiService
import android.util.Log
import android.content.Context
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

    fun initialize(context: Context) {
        if (isInitialized) return

        try {
            if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                Log.w(TAG, "⚠️ Utente non autenticato")
                _statsText.value = buildString {
                    appendLine("⚠️ Non autenticato")
                    appendLine()
                    appendLine("Effettua il login per vedere")
                    appendLine("le tue statistiche")
                }
                return
            }

            movieRepository = MovieRepository.getInstance(context)

            movieRepository?.movies?.observeForever { movies ->
                _movies.value = movies ?: emptyList()
                viewModelScope.launch {
                    updateStats()
                }
            }

            viewModelScope.launch {
                loadUserMovies()
                updateStats()
            }

            isInitialized = true
            Log.d(TAG, "✅ dashboard inizializzata per utente ${ApiService.getCurrentUserId()}")

        } catch (e: Exception) {
            Log.e(TAG, "errore inizializzazione", e)
            _statsText.value = "errore backend ${AppConfig.BACKEND_HOST}: ${e.message}"
        }
    }

    private suspend fun loadUserMovies() {
        try {
            if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                Log.w(TAG, "⚠️ Utente non autenticato, skip load")
                return
            }

            Log.d(TAG, "📚 Caricamento film utente...")

            movieRepository?.loadMoviesFromDatabase()

            delay(500)

            val result = ApiService.getUserStoredMovies()

            if (result.isSuccess) {
                val movies = result.getOrNull() ?: emptyList()

                if (movies.isNotEmpty()) {
                    movieRepository?.replaceAll(movies)

                    Log.d(TAG, "✅ Caricati ${movies.size} film per utente")

                    val watched = movies.count { it.isWatched }
                    val watchlist = movies.count { !it.isWatched }
                    Log.d(TAG, "   • $watched visti")
                    Log.d(TAG, "   • $watchlist da vedere")
                } else {
                    Log.d(TAG, "ℹ️ Nessun film per questo utente")
                }
            } else {
                Log.w(TAG, "⚠️ Errore caricamento: ${result.exceptionOrNull()?.message}")
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ Errore loadUserMovies: ${e.message}")
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
            val userId = ApiService.getCurrentUserId()
            _statsText.value = buildString {
                appendLine("nessun film nella tua collezione")
                appendLine()
                if (userId != null) {
                    appendLine("📊 Account: ${ApiService.getCurrentUser()?.email ?: "..."}")
                    appendLine()
                }
                appendLine("per iniziare:")
                appendLine("• vai alla scheda home")
                appendLine("• importa i tuoi film da imdb o letterboxd")
                appendLine("• torna qui per vedere le statistiche!")
            }
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

        val userEmail = ApiService.getCurrentUser()?.email
        if (userEmail != null) {
            statsBuilder.append("le tue statistiche\n")
            statsBuilder.append("📊 $userEmail\n\n")
        } else {
            statsBuilder.append("le tue statistiche\n\n")
        }

        statsBuilder.append("panoramica\n\n")
        statsBuilder.append("film totali: ${movies.size}\n")
        statsBuilder.append("• visti: ${watchedMovies.size}\n")
        statsBuilder.append("• da vedere: ${watchlistMovies.size}\n")
        if (enrichedCount > 0) {
            statsBuilder.append("• con dettagli: $enrichedCount\n")
        }
        statsBuilder.append("• tempo visione: ${totalHours}h\n\n")

        val averageRating = watchedMovies.mapNotNull { it.userRating }.takeIf { it.isNotEmpty() }?.average()

        if (averageRating != null) {
            statsBuilder.append("valutazioni\n\n")
            statsBuilder.append("voto medio: ${String.format("%.1f", averageRating)}/10\n\n")
        }

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

    fun refreshStats() {
        viewModelScope.launch {
            try {
                if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                    Log.w(TAG, "⚠️ Utente non autenticato, skip refresh")
                    return@launch
                }

                Log.d(TAG, "🔄 refresh stats per utente ${ApiService.getCurrentUserId()}")

                loadUserMovies()

                updateStats()

                Log.d(TAG, "✅ refresh stats completato")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore refresh stats: ${e.message}")
            }
        }
    }

    fun hasData(): Boolean {
        return movieRepository?.getAllMovies()?.isNotEmpty() ?: false
    }

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

        if (ApiService.isAuthenticated()) {
            val userId = ApiService.getCurrentUserId()
            Log.d(TAG, "📊 Content counts per utente: $userId")
        }

        return repository.getStats()
    }

    fun isUserAuthenticated(): Boolean {
        return ApiService.isAuthenticated() && ApiService.hasUserId()
    }

    fun getCurrentUserInfo(): String? {
        return ApiService.getCurrentUser()?.let { user ->
            "${user.email} (${user.id.take(8)}...)"
        }
    }
}