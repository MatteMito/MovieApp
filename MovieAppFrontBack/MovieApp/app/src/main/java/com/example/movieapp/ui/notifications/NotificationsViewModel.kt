package com.example.movieapp.ui.notifications

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.network.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel per grafici e analytics - ✅ FILTRATO PER userId
 */
class NotificationsViewModel : ViewModel() {
    private val TAG = "NotificationsViewModel"

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _chartsReady = MutableLiveData<Boolean>()
    val chartsReady: LiveData<Boolean> = _chartsReady

    // Dati per grafici
    private val _genresData = MutableLiveData<Map<String, Int>>()
    val genresData: LiveData<Map<String, Int>> = _genresData

    private val _yearsData = MutableLiveData<Map<Int, Int>>()
    val yearsData: LiveData<Map<Int, Int>> = _yearsData

    private val _directorsData = MutableLiveData<Map<String, Int>>()
    val directorsData: LiveData<Map<String, Int>> = _directorsData

    private val _ratingsData = MutableLiveData<Map<String, Int>>()
    val ratingsData: LiveData<Map<String, Int>> = _ratingsData

    private val _countriesData = MutableLiveData<Map<String, Int>>()
    val countriesData: LiveData<Map<String, Int>> = _countriesData

    private val _decadesData = MutableLiveData<Map<String, Int>>()
    val decadesData: LiveData<Map<String, Int>> = _decadesData

    private val _runtimeDistributionData = MutableLiveData<Map<String, Int>>()
    val runtimeDistributionData: LiveData<Map<String, Int>> = _runtimeDistributionData

    private val _watchedByMonthData = MutableLiveData<Map<String, Int>>()
    val watchedByMonthData: LiveData<Map<String, Int>> = _watchedByMonthData

    // Statistiche top per ogni grafico
    private val _topGenre = MutableLiveData<Pair<String, Int>>()
    val topGenre: LiveData<Pair<String, Int>> = _topGenre

    private val _topYear = MutableLiveData<Pair<Int, Int>>()
    val topYear: LiveData<Pair<Int, Int>> = _topYear

    private val _topDirector = MutableLiveData<Pair<String, Int>>()
    val topDirector: LiveData<Pair<String, Int>> = _topDirector

    private val _topCountry = MutableLiveData<Pair<String, Int>>()
    val topCountry: LiveData<Pair<String, Int>> = _topCountry

    private val _topDecade = MutableLiveData<Pair<String, Int>>()
    val topDecade: LiveData<Pair<String, Int>> = _topDecade

    // Map per tenere traccia dei film per categoria (per i click)
    private val genreMoviesMap = mutableMapOf<String, List<Movie>>()
    private val yearMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val directorMoviesMap = mutableMapOf<String, List<Movie>>()
    private val ratingMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val countryMoviesMap = mutableMapOf<String, List<Movie>>()
    private val decadeMoviesMap = mutableMapOf<String, List<Movie>>()
    private val runtimeMoviesMap = mutableMapOf<String, List<Movie>>()

    init {
        _chartsReady.value = false
        Log.d(TAG, "NotificationsViewModel inizializzato")
    }

    /**
     * ✅ FIX: Carica film dal backend filtrati per userId
     */
    fun initialize(context: Context) {
        loadMovies()
    }

    /**
     * ✅ FIX: Carica film dal backend per l'utente loggato
     */
    private fun loadMovies() {
        viewModelScope.launch {
            try {
                // ✅ Verifica autenticazione
                if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                    Log.w(TAG, "⚠️ Utente non autenticato - impossibile caricare statistiche")
                    _movies.value = emptyList()
                    _chartsReady.value = false
                    return@launch
                }

                val userId = ApiService.getCurrentUserId()!!
                Log.d(TAG, "📊 Caricamento film per utente: $userId")

                // ✅ Carica film dal backend (già filtrati per userId)
                val result = ApiService.getUserStoredMovies()

                if (result.isSuccess) {
                    val movies = result.getOrNull() ?: emptyList()
                    _movies.value = movies

                    Log.d(TAG, "✅ Caricati ${movies.size} film per statistiche utente $userId")

                    if (movies.isNotEmpty()) {
                        generateAllCharts(movies)
                    } else {
                        Log.d(TAG, "ℹ️ Nessun film trovato per questo utente")
                        _chartsReady.value = false
                    }
                } else {
                    Log.w(TAG, "⚠️ Errore caricamento film: ${result.exceptionOrNull()?.message}")
                    _movies.value = emptyList()
                    _chartsReady.value = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore caricamento film per statistiche", e)
                _movies.value = emptyList()
                _chartsReady.value = false
            }
        }
    }

    /**
     * Ricarica i dati (da chiamare dopo import/modifica)
     */
    fun refreshData() {
        Log.d(TAG, "🔄 Refresh dati statistiche")
        loadMovies()
    }

    /**
     * Genera tutti i grafici in background
     */
    private fun generateAllCharts(movies: List<Movie>) {
        viewModelScope.launch {
            try {
                _chartsReady.value = false

                Log.d(TAG, "📊 Generazione grafici per ${movies.size} film")

                withContext(Dispatchers.Default) {
                    // Grafici esistenti
                    val genres = analyzeGenres(movies)
                    val years = analyzeYears(movies)
                    val directors = analyzeDirectors(movies)
                    val ratings = analyzeRatings(movies)
                    val countries = analyzeCountries(movies)

                    // Nuovi grafici
                    val decades = analyzeDecades(movies)
                    val runtimeDist = analyzeRuntimeDistribution(movies)
                    val watchedByMonth = analyzeWatchedByMonth(movies)

                    // Pubblica risultati
                    _genresData.postValue(genres)
                    _yearsData.postValue(years)
                    _directorsData.postValue(directors)
                    _ratingsData.postValue(ratings)
                    _countriesData.postValue(countries)
                    _decadesData.postValue(decades)
                    _runtimeDistributionData.postValue(runtimeDist)
                    _watchedByMonthData.postValue(watchedByMonth)

                    // Calcola top per ogni categoria
                    genres.maxByOrNull { it.value }?.let { _topGenre.postValue(it.key to it.value) }
                    years.maxByOrNull { it.value }?.let { _topYear.postValue(it.key to it.value) }
                    directors.maxByOrNull { it.value }?.let { _topDirector.postValue(it.key to it.value) }
                    countries.maxByOrNull { it.value }?.let { _topCountry.postValue(it.key to it.value) }
                    decades.maxByOrNull { it.value }?.let { _topDecade.postValue(it.key to it.value) }
                }

                _chartsReady.value = true
                Log.d(TAG, "✅ Grafici generati con successo")

            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore generazione grafici", e)
                _chartsReady.value = false
            }
        }
    }

    private fun analyzeGenres(movies: List<Movie>): Map<String, Int> {
        val genreCount = mutableMapOf<String, Int>()
        genreMoviesMap.clear()

        movies.forEach { movie ->
            movie.genres.forEach { genre ->
                if (genre.isNotBlank()) {
                    genreCount[genre] = genreCount.getOrDefault(genre, 0) + 1

                    val currentList = genreMoviesMap[genre] ?: emptyList()
                    genreMoviesMap[genre] = currentList + movie
                }
            }
        }

        return genreCount
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
    }

    private fun analyzeYears(movies: List<Movie>): Map<Int, Int> {
        val yearCount = mutableMapOf<Int, Int>()
        yearMoviesMap.clear()

        movies.forEach { movie ->
            movie.year?.let { year ->
                yearCount[year] = yearCount.getOrDefault(year, 0) + 1

                val currentList = yearMoviesMap[year] ?: emptyList()
                yearMoviesMap[year] = currentList + movie
            }
        }

        return yearCount
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    private fun analyzeDirectors(movies: List<Movie>): Map<String, Int> {
        val directorCount = mutableMapOf<String, Int>()
        directorMoviesMap.clear()

        movies.forEach { movie ->
            movie.director?.let { director ->
                if (director.isNotBlank()) {
                    directorCount[director] = directorCount.getOrDefault(director, 0) + 1

                    val currentList = directorMoviesMap[director] ?: emptyList()
                    directorMoviesMap[director] = currentList + movie
                }
            }
        }

        return directorCount
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
    }

    private fun analyzeRatings(movies: List<Movie>): Map<String, Int> {
        val ratingCount = mutableMapOf<String, Int>()
        ratingMoviesMap.clear()

        movies.forEach { movie ->
            movie.userRating?.let { rating ->
                val ratingInt = rating.toInt()
                val ratingKey = "$ratingInt★"
                ratingCount[ratingKey] = ratingCount.getOrDefault(ratingKey, 0) + 1

                val currentList = ratingMoviesMap[ratingInt] ?: emptyList()
                ratingMoviesMap[ratingInt] = currentList + movie
            }
        }

        return ratingCount
            .entries
            .sortedByDescending { it.key }
            .associate { it.key to it.value }
    }

    private fun analyzeCountries(movies: List<Movie>): Map<String, Int> {
        val countryCount = mutableMapOf<String, Int>()
        countryMoviesMap.clear()

        movies.forEach { movie ->
            movie.productionCountries.forEach { country ->
                if (country.isNotBlank()) {
                    countryCount[country] = countryCount.getOrDefault(country, 0) + 1

                    val currentList = countryMoviesMap[country] ?: emptyList()
                    countryMoviesMap[country] = currentList + movie
                }
            }
        }

        return countryCount
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    private fun analyzeDecades(movies: List<Movie>): Map<String, Int> {
        val decadeCount = mutableMapOf<String, Int>()
        decadeMoviesMap.clear()

        movies.forEach { movie ->
            movie.year?.let { year ->
                val decade = (year / 10) * 10
                val decadeKey = "${decade}s"
                decadeCount[decadeKey] = decadeCount.getOrDefault(decadeKey, 0) + 1

                val currentList = decadeMoviesMap[decadeKey] ?: emptyList()
                decadeMoviesMap[decadeKey] = currentList + movie
            }
        }

        return decadeCount
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    private fun analyzeRuntimeDistribution(movies: List<Movie>): Map<String, Int> {
        val runtimeCount = mutableMapOf<String, Int>()
        runtimeMoviesMap.clear()

        movies.forEach { movie ->
            movie.runtime?.let { runtime ->
                val bucket = when {
                    runtime < 90 -> "< 90min"
                    runtime < 120 -> "90-120min"
                    runtime < 150 -> "120-150min"
                    else -> "> 150min"
                }
                runtimeCount[bucket] = runtimeCount.getOrDefault(bucket, 0) + 1

                val currentList = runtimeMoviesMap[bucket] ?: emptyList()
                runtimeMoviesMap[bucket] = currentList + movie
            }
        }

        return runtimeCount
    }

    private fun analyzeWatchedByMonth(movies: List<Movie>): Map<String, Int> {
        val monthCount = mutableMapOf<String, Int>()
        val dateFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())

        movies.filter { it.isWatched && it.dateRated != null }.forEach { movie ->
            try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(movie.dateRated!!)
                date?.let {
                    val monthKey = dateFormat.format(it)
                    monthCount[monthKey] = monthCount.getOrDefault(monthKey, 0) + 1
                }
            } catch (e: Exception) {
                Log.w(TAG, "Errore parsing data: ${movie.dateRated}")
            }
        }

        return monthCount
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    // Funzioni per recuperare film per categoria (per i click)
    fun getMoviesByGenre(genre: String): List<Movie> = genreMoviesMap[genre] ?: emptyList()
    fun getMoviesByYear(year: Int): List<Movie> = yearMoviesMap[year] ?: emptyList()
    fun getMoviesByDirector(director: String): List<Movie> = directorMoviesMap[director] ?: emptyList()
    fun getMoviesByRating(rating: Int): List<Movie> = ratingMoviesMap[rating] ?: emptyList()
    fun getMoviesByCountry(country: String): List<Movie> = countryMoviesMap[country] ?: emptyList()
    fun getMoviesByDecade(decade: String): List<Movie> = decadeMoviesMap[decade] ?: emptyList()
    fun getMoviesByRuntime(runtime: String): List<Movie> = runtimeMoviesMap[runtime] ?: emptyList()
}