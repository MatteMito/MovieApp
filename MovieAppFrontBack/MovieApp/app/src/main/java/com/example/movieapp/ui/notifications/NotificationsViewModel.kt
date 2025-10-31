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

    //dati per grafici principali
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

    //nuovi grafici
    private val _decadesData = MutableLiveData<Map<String, Int>>()
    val decadesData: LiveData<Map<String, Int>> = _decadesData

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

                Log.d(TAG, "generazione grafici per ${movies.size} film")

                withContext(Dispatchers.Default) {
                    //calcola tempo totale visione
                    calculateTotalWatchTime(movies)

                    //grafici principali
                    val genres = analyzeGenres(movies)
                    val years = analyzeYears(movies)
                    val directors = analyzeDirectors(movies)
                    val ratings = analyzeRatings(movies)
                    val countries = analyzeCountries(movies)

                    //nuovi grafici
                    val decades = analyzeDecades(movies)
                    val genreRatings = analyzeGenreRatings(movies)
                    val decadeRatings = analyzeDecadeRatings(movies)
                    val runtimeVsRating = analyzeRuntimeVsRating(movies)

                    //pubblica risultati
                    _genresData.postValue(genres)
                    _yearsData.postValue(years)
                    _directorsData.postValue(directors)
                    _ratingsData.postValue(ratings)
                    _countriesData.postValue(countries)
                    _decadesData.postValue(decades)
                    _genreRatingsData.postValue(genreRatings)
                    _decadeRatingsData.postValue(decadeRatings)
                    _runtimeVsRatingData.postValue(runtimeVsRating)

                    //calcola top per ogni categoria
                    genres.maxByOrNull { it.value }?.let { _topGenre.postValue(it.key to it.value) }
                    years.maxByOrNull { it.value }?.let { _topYear.postValue(it.key to it.value) }
                    directors.maxByOrNull { it.value }?.let { _topDirector.postValue(it.key to it.value) }
                    countries.maxByOrNull { it.value }?.let { _topCountry.postValue(it.key to it.value) }
                    decades.maxByOrNull { it.value }?.let { _topDecade.postValue(it.key to it.value) }
                }

                _chartsReady.value = true
                Log.d(TAG, "grafici generati con successo")

            } catch (e: Exception) {
                Log.e(TAG, "errore generazione grafici", e)
                _chartsReady.value = false
            }
        }
    }

    //calcola tempo totale visione
    private fun calculateTotalWatchTime(movies: List<Movie>) {
        val totalMinutes = movies.filter { it.isWatched }.sumOf { it.runtime ?: 0 }
        val hours = totalMinutes / 60
        val days = hours / 24
        val remainingHours = hours % 24

        val timeText = when {
            days > 0 -> "$days giorni e $remainingHours ore"
            hours > 0 -> "$hours ore"
            else -> "$totalMinutes minuti"
        }

        _totalWatchTime.postValue("⏱️ Tempo totale: $timeText di film visti!")
    }

    //analisi generi
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
            .take(10)
            .associate { it.key to it.value }
    }

    //analisi anni
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
            .sortedByDescending { it.value }
            .take(15)
            .associate { it.key to it.value }
    }

    //analisi registi
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
            .take(10)
            .associate { it.key to it.value }
    }

    //analisi ratings
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

    //analisi paesi
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

    //analisi decenni
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

    private fun analyzeGenreRatings(movies: List<Movie>): Map<String, Float> {
        val genreRatings = mutableMapOf<String, MutableList<Float>>()

        movies.forEach { movie ->
            movie.userRating?.let { rating ->
                movie.genres.forEach { genre ->
                    if (genre.isNotBlank()) {
                        val ratings = genreRatings.getOrPut(genre) { mutableListOf() }
                        ratings.add(rating.toFloat())
                    }
                }
            }
        }

        return genreRatings
            .entries
            .filter { it.value.isNotEmpty() }
            .associate { (key, ratings) ->
                key to ratings.average().toFloat()
            }
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    private fun analyzeDecadeRatings(movies: List<Movie>): Map<String, Float> {
        val decadeRatings = mutableMapOf<String, MutableList<Float>>()

        movies.forEach { movie ->
            movie.year?.let { year ->
                movie.userRating?.let { rating ->
                    val decade = (year / 10) * 10
                    val decadeKey = "${decade}s"
                    val ratings = decadeRatings.getOrPut(decadeKey) { mutableListOf() }
                    ratings.add(rating.toFloat())  // <-- Aggiungi .toFloat() QUI
                }
            }
        }

        return decadeRatings
            .entries
            .filter { it.value.isNotEmpty() }
            .associate { (key, ratings) ->
                key to ratings.average().toFloat()
            }
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    //durata vs valutazione (scatter plot data)
    private fun analyzeRuntimeVsRating(movies: List<Movie>): List<Pair<Int, Float>> {
        return movies
            .filter { it.runtime != null && it.userRating != null }
            .map { movie ->
                Pair(movie.runtime!!, movie.userRating!!.toFloat())
            }
    }

    //funzioni per recuperare film per categoria (per i click)
    fun getMoviesByGenre(genre: String): List<Movie> = genreMoviesMap[genre] ?: emptyList()
    fun getMoviesByYear(year: Int): List<Movie> = yearMoviesMap[year] ?: emptyList()
    fun getMoviesByDirector(director: String): List<Movie> = directorMoviesMap[director] ?: emptyList()
    fun getMoviesByRating(rating: Int): List<Movie> = ratingMoviesMap[rating] ?: emptyList()
    fun getMoviesByCountry(country: String): List<Movie> = countryMoviesMap[country] ?: emptyList()
    fun getMoviesByDecade(decade: String): List<Movie> = decadeMoviesMap[decade] ?: emptyList()
}