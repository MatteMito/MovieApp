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

/**
 * viewmodel per grafici analytics avanzati
 * fix: filtri corretti per watched e rating
 */
class NotificationsViewModel : ViewModel() {
    private val TAG = "NotificationsViewModel"

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _chartsReady = MutableLiveData<Boolean>()
    val chartsReady: LiveData<Boolean> = _chartsReady

    //statistiche testuali
    private val _totalWatchTime = MutableLiveData<String>()
    val totalWatchTime: LiveData<String> = _totalWatchTime

    //dati per grafici principali (solo watched)
    private val _genresData = MutableLiveData<Map<String, Int>>()
    val genresData: LiveData<Map<String, Int>> = _genresData

    private val _yearsData = MutableLiveData<Map<Int, Int>>()
    val yearsData: LiveData<Map<Int, Int>> = _yearsData

    private val _directorsData = MutableLiveData<Map<String, Int>>()
    val directorsData: LiveData<Map<String, Int>> = _directorsData

    private val _countriesData = MutableLiveData<Map<String, Int>>()
    val countriesData: LiveData<Map<String, Int>> = _countriesData

    private val _decadesData = MutableLiveData<Map<String, Int>>()
    val decadesData: LiveData<Map<String, Int>> = _decadesData

    //dati per grafici con rating (solo watched con userRating)
    private val _ratingsData = MutableLiveData<Map<String, Int>>()
    val ratingsData: LiveData<Map<String, Int>> = _ratingsData

    private val _genreRatingsData = MutableLiveData<Map<String, Float>>()
    val genreRatingsData: LiveData<Map<String, Float>> = _genreRatingsData

    private val _decadeRatingsData = MutableLiveData<Map<String, Float>>()
    val decadeRatingsData: LiveData<Map<String, Float>> = _decadeRatingsData

    private val _runtimeVsRatingData = MutableLiveData<List<Pair<Int, Float>>>()
    val runtimeVsRatingData: LiveData<List<Pair<Int, Float>>> = _runtimeVsRatingData

    //statistiche top cliccabili
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

    //map per click sui grafici
    private val genreMoviesMap = mutableMapOf<String, List<Movie>>()
    private val yearMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val directorMoviesMap = mutableMapOf<String, List<Movie>>()
    private val ratingMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val countryMoviesMap = mutableMapOf<String, List<Movie>>()
    private val decadeMoviesMap = mutableMapOf<String, List<Movie>>()

    fun initialize(context: Context) {
        loadMovies(context)
    }

    fun refreshData() {
        _movies.value?.let { movies ->
            if (movies.isNotEmpty()) {
                generateCharts(movies)
            }
        }
    }

    private fun loadMovies(context: Context) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "caricamento film per analytics")

                withContext(Dispatchers.IO) {
                    val result = ApiService.getUserStoredMovies()

                    withContext(Dispatchers.Main) {
                        if (result.isSuccess) {
                            val movies = result.getOrNull() ?: emptyList()
                            _movies.value = movies
                            generateCharts(movies)
                            Log.d(TAG, "caricati ${movies.size} film")
                        } else {
                            Log.e(TAG, "errore caricamento film")
                            _chartsReady.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore: ${e.message}", e)
                _chartsReady.value = false
            }
        }
    }

    private fun generateCharts(movies: List<Movie>) {
        viewModelScope.launch {
            try {
                _chartsReady.value = false

                Log.d(TAG, "generazione grafici per ${movies.size} film totali")

                withContext(Dispatchers.Default) {
                    //fix: separa watched da watchlist
                    val watchedMovies = movies.filter { it.isWatched }
                    val watchedWithRating = watchedMovies.filter { it.userRating != null && it.userRating!! > 0 }

                    Log.d(TAG, "film watched: ${watchedMovies.size}")
                    Log.d(TAG, "film watched con rating: ${watchedWithRating.size}")

                    if (watchedMovies.isEmpty()) {
                        Log.w(TAG, "nessun film watched, grafici non disponibili")
                        withContext(Dispatchers.Main) {
                            _chartsReady.value = false
                        }
                        return@withContext
                    }

                    //calcola tempo totale visione (solo watched)
                    calculateTotalWatchTime(watchedMovies)

                    //grafici base (solo watched, no rating required)
                    val genres = analyzeGenres(watchedMovies)
                    val years = analyzeYears(watchedMovies)
                    val directors = analyzeDirectors(watchedMovies)
                    val countries = analyzeCountries(watchedMovies)
                    val decades = analyzeDecades(watchedMovies)

                    //grafici con rating (solo watched con rating)
                    val ratings = if (watchedWithRating.isNotEmpty()) {
                        analyzeRatings(watchedWithRating)
                    } else {
                        emptyMap()
                    }

                    val genreRatings = if (watchedWithRating.isNotEmpty()) {
                        analyzeGenreRatings(watchedWithRating)
                    } else {
                        emptyMap()
                    }

                    val decadeRatings = if (watchedWithRating.isNotEmpty()) {
                        analyzeDecadeRatings(watchedWithRating)
                    } else {
                        emptyMap()
                    }

                    val runtimeVsRating = if (watchedWithRating.isNotEmpty()) {
                        analyzeRuntimeVsRating(watchedWithRating)
                    } else {
                        emptyList()
                    }

                    withContext(Dispatchers.Main) {
                        //grafici base
                        _genresData.value = genres
                        _yearsData.value = years
                        _directorsData.value = directors
                        _countriesData.value = countries
                        _decadesData.value = decades

                        //grafici con rating
                        _ratingsData.value = ratings
                        _genreRatingsData.value = genreRatings
                        _decadeRatingsData.value = decadeRatings
                        _runtimeVsRatingData.value = runtimeVsRating

                        _chartsReady.value = true

                        Log.d(TAG, "grafici generati con successo")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "errore generazione grafici: ${e.message}", e)
                _chartsReady.value = false
            }
        }
    }

    /**
     * calcola tempo totale visione (solo watched)
     */
    private fun calculateTotalWatchTime(watchedMovies: List<Movie>) {
        val totalMinutes = watchedMovies.sumOf { it.runtime ?: 0 }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        val timeText = when {
            hours == 0 -> "$minutes minuti di film visti"
            minutes == 0 -> "$hours ore di film visti"
            else -> "$hours ore e $minutes minuti di film visti"
        }

        _totalWatchTime.postValue("⏱️ Hai visto $timeText!")
        Log.d(TAG, "tempo totale: $hours ore, $minutes minuti")
    }

    /**
     * analizza generi (solo watched)
     */
    private fun analyzeGenres(watchedMovies: List<Movie>): Map<String, Int> {
        genreMoviesMap.clear()

        val genreCounts = mutableMapOf<String, Int>()

        watchedMovies.forEach { movie ->
            movie.genres?.forEach { genre ->
                genreCounts[genre] = (genreCounts[genre] ?: 0) + 1

                if (!genreMoviesMap.containsKey(genre)) {
                    genreMoviesMap[genre] = mutableListOf()
                }
                (genreMoviesMap[genre] as MutableList).add(movie)
            }
        }

        val sortedGenres = genreCounts.entries.sortedByDescending { it.value }.take(10)
        if (sortedGenres.isNotEmpty()) {
            _topGenre.postValue(sortedGenres.first().key to sortedGenres.first().value)
        }

        return sortedGenres.associate { it.key to it.value }
    }

    /**
     * analizza anni (solo watched)
     */
    private fun analyzeYears(watchedMovies: List<Movie>): Map<Int, Int> {
        yearMoviesMap.clear()

        val yearCounts = mutableMapOf<Int, Int>()

        watchedMovies.forEach { movie ->
            movie.year?.let { year ->
                yearCounts[year] = (yearCounts[year] ?: 0) + 1

                if (!yearMoviesMap.containsKey(year)) {
                    yearMoviesMap[year] = mutableListOf()
                }
                (yearMoviesMap[year] as MutableList).add(movie)
            }
        }

        val sortedYears = yearCounts.entries.sortedByDescending { it.value }.take(15)
        if (sortedYears.isNotEmpty()) {
            _topYear.postValue(sortedYears.first().key to sortedYears.first().value)
        }

        return sortedYears.associate { it.key to it.value }
    }

    /**
     * analizza registi (solo watched)
     */
    private fun analyzeDirectors(watchedMovies: List<Movie>): Map<String, Int> {
        directorMoviesMap.clear()

        val directorCounts = mutableMapOf<String, Int>()

        watchedMovies.forEach { movie ->
            movie.director?.let { director ->
                directorCounts[director] = (directorCounts[director] ?: 0) + 1

                if (!directorMoviesMap.containsKey(director)) {
                    directorMoviesMap[director] = mutableListOf()
                }
                (directorMoviesMap[director] as MutableList).add(movie)
            }
        }

        val sortedDirectors = directorCounts.entries.sortedByDescending { it.value }.take(10)
        if (sortedDirectors.isNotEmpty()) {
            _topDirector.postValue(sortedDirectors.first().key to sortedDirectors.first().value)
        }

        return sortedDirectors.associate { it.key to it.value }
    }

    /**
     * analizza paesi (solo watched)
     */
    private fun analyzeCountries(watchedMovies: List<Movie>): Map<String, Int> {
        countryMoviesMap.clear()

        val countryCounts = mutableMapOf<String, Int>()

        watchedMovies.forEach { movie ->
            movie.production_countries?.forEach { country ->
                countryCounts[country] = (countryCounts[country] ?: 0) + 1

                if (!countryMoviesMap.containsKey(country)) {
                    countryMoviesMap[country] = mutableListOf()
                }
                (countryMoviesMap[country] as MutableList).add(movie)
            }
        }

        val sortedCountries = countryCounts.entries.sortedByDescending { it.value }.take(10)
        if (sortedCountries.isNotEmpty()) {
            _topCountry.postValue(sortedCountries.first().key to sortedCountries.first().value)
        }

        return sortedCountries.associate { it.key to it.value }
    }

    /**
     * analizza decenni (solo watched)
     */
    private fun analyzeDecades(watchedMovies: List<Movie>): Map<String, Int> {
        decadeMoviesMap.clear()

        val decadeCounts = mutableMapOf<String, Int>()

        watchedMovies.forEach { movie ->
            movie.year?.let { year ->
                val decade = "${(year / 10) * 10}s"
                decadeCounts[decade] = (decadeCounts[decade] ?: 0) + 1

                if (!decadeMoviesMap.containsKey(decade)) {
                    decadeMoviesMap[decade] = mutableListOf()
                }
                (decadeMoviesMap[decade] as MutableList).add(movie)
            }
        }

        val sortedDecades = decadeCounts.entries.sortedByDescending { it.value }
        if (sortedDecades.isNotEmpty()) {
            _topDecade.postValue(sortedDecades.first().key to sortedDecades.first().value)
        }

        return sortedDecades.associate { it.key to it.value }
    }

    /**
     * fix: analizza ratings (solo watched con rating)
     */
    private fun analyzeRatings(watchedWithRating: List<Movie>): Map<String, Int> {
        ratingMoviesMap.clear()

        val ratingCounts = mutableMapOf<Int, Int>()

        watchedWithRating.forEach { movie ->
            movie.userRating?.let { rating ->
                val roundedRating = rating.toInt()
                ratingCounts[roundedRating] = (ratingCounts[roundedRating] ?: 0) + 1

                if (!ratingMoviesMap.containsKey(roundedRating)) {
                    ratingMoviesMap[roundedRating] = mutableListOf()
                }
                (ratingMoviesMap[roundedRating] as MutableList).add(movie)
            }
        }

        return ratingCounts.entries.sortedBy { it.key }
            .associate { "${it.key} stelle" to it.value }
    }

    /**
     * fix: analizza rating medio per genere (solo watched con rating)
     */
    private fun analyzeGenreRatings(watchedWithRating: List<Movie>): Map<String, Float> {
        val genreRatings = mutableMapOf<String, MutableList<Float>>()

        watchedWithRating.forEach { movie ->
            movie.userRating?.let { rating ->
                movie.genres?.forEach { genre ->
                    if (!genreRatings.containsKey(genre)) {
                        genreRatings[genre] = mutableListOf()
                    }
                    genreRatings[genre]?.add(rating)
                }
            }
        }

        return genreRatings.entries
            .map { it.key to (it.value.sum() / it.value.size) }
            .sortedByDescending { it.second }
            .take(10)
            .associate { it.first to it.second }
    }

    /**
     * fix: analizza rating medio per decennio (solo watched con rating)
     */
    private fun analyzeDecadeRatings(watchedWithRating: List<Movie>): Map<String, Float> {
        val decadeRatings = mutableMapOf<String, MutableList<Float>>()

        watchedWithRating.forEach { movie ->
            movie.userRating?.let { rating ->
                movie.year?.let { year ->
                    val decade = "${(year / 10) * 10}s"
                    if (!decadeRatings.containsKey(decade)) {
                        decadeRatings[decade] = mutableListOf()
                    }
                    decadeRatings[decade]?.add(rating)
                }
            }
        }

        return decadeRatings.entries
            .map { it.key to (it.value.sum() / it.value.size) }
            .sortedBy { it.first }
            .associate { it.first to it.second }
    }

    /**
     * fix: analizza durata vs rating (solo watched con rating)
     */
    private fun analyzeRuntimeVsRating(watchedWithRating: List<Movie>): List<Pair<Int, Float>> {
        return watchedWithRating
            .filter { it.runtime != null && it.userRating != null }
            .map { it.runtime!! to it.userRating!! }
    }

    //funzioni per recuperare film per categoria (per i click)
    fun getMoviesByGenre(genre: String): List<Movie> = genreMoviesMap[genre] ?: emptyList()
    fun getMoviesByYear(year: Int): List<Movie> = yearMoviesMap[year] ?: emptyList()
    fun getMoviesByDirector(director: String): List<Movie> = directorMoviesMap[director] ?: emptyList()
    fun getMoviesByRating(rating: Int): List<Movie> = ratingMoviesMap[rating] ?: emptyList()
    fun getMoviesByCountry(country: String): List<Movie> = countryMoviesMap[country] ?: emptyList()
    fun getMoviesByDecade(decade: String): List<Movie> = decadeMoviesMap[decade] ?: emptyList()
}