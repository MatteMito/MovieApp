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
import kotlin.math.roundToInt

/**
 * viewmodel per grafici e analytics
 */
class NotificationsViewModel : ViewModel() {
    private val TAG = "NotificationsViewModel"

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _chartsReady = MutableLiveData<Boolean>()
    val chartsReady: LiveData<Boolean> = _chartsReady

    //dati grafici esistenti
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

    //nuovi dati grafici
    private val _averageRatingByGenre = MutableLiveData<Map<String, Double>>()
    val averageRatingByGenre: LiveData<Map<String, Double>> = _averageRatingByGenre

    private val _actorsData = MutableLiveData<Map<String, Int>>()
    val actorsData: LiveData<Map<String, Int>> = _actorsData

    private val _productionCompaniesData = MutableLiveData<Map<String, Int>>()
    val productionCompaniesData: LiveData<Map<String, Int>> = _productionCompaniesData

    private val _runtimeVsRatingData = MutableLiveData<List<Pair<Int, Double>>>()
    val runtimeVsRatingData: LiveData<List<Pair<Int, Double>>> = _runtimeVsRatingData

    private val _totalWatchTimeText = MutableLiveData<String>()
    val totalWatchTimeText: LiveData<String> = _totalWatchTimeText

    private val _genreCombinationsData = MutableLiveData<Map<String, Int>>()
    val genreCombinationsData: LiveData<Map<String, Int>> = _genreCombinationsData

    //statistiche top
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

    private val _topActor = MutableLiveData<Pair<String, Int>>()
    val topActor: LiveData<Pair<String, Int>> = _topActor

    private val _topProductionCompany = MutableLiveData<Pair<String, Int>>()
    val topProductionCompany: LiveData<Pair<String, Int>> = _topProductionCompany

    //map per click
    private val genreMoviesMap = mutableMapOf<String, List<Movie>>()
    private val yearMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val directorMoviesMap = mutableMapOf<String, List<Movie>>()
    private val ratingMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val countryMoviesMap = mutableMapOf<String, List<Movie>>()
    private val decadeMoviesMap = mutableMapOf<String, List<Movie>>()
    private val runtimeMoviesMap = mutableMapOf<String, List<Movie>>()
    private val actorMoviesMap = mutableMapOf<String, List<Movie>>()
    private val companyMoviesMap = mutableMapOf<String, List<Movie>>()

    init {
        _chartsReady.value = false
        Log.d(TAG, "notificationsviewmodel inizializzato")
    }

    fun initialize(context: Context) {
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            try {
                if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
                    Log.w(TAG, "utente non autenticato")
                    _movies.value = emptyList()
                    _chartsReady.value = false
                    return@launch
                }

                val userId = ApiService.getCurrentUserId()!!
                Log.d(TAG, "caricamento film per utente: $userId")

                val result = ApiService.getUserStoredMovies()

                if (result.isSuccess) {
                    val movies = result.getOrNull() ?: emptyList()
                    _movies.value = movies

                    Log.d(TAG, "caricati ${movies.size} film")

                    if (movies.isNotEmpty()) {
                        generateAllCharts(movies)
                    } else {
                        _chartsReady.value = false
                    }
                } else {
                    _movies.value = emptyList()
                    _chartsReady.value = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore caricamento", e)
                _movies.value = emptyList()
                _chartsReady.value = false
            }
        }
    }

    fun refreshData() {
        Log.d(TAG, "refresh dati")
        loadMovies()
    }

    private fun generateAllCharts(movies: List<Movie>) {
        viewModelScope.launch {
            try {
                _chartsReady.value = false

                Log.d(TAG, "generazione grafici per ${movies.size} film")

                withContext(Dispatchers.Default) {
                    //grafici esistenti
                    val genres = analyzeGenres(movies)
                    val years = analyzeYears(movies)
                    val directors = analyzeDirectors(movies)
                    val ratings = analyzeRatings(movies)
                    val countries = analyzeCountries(movies)
                    val decades = analyzeDecades(movies)
                    val runtimeDist = analyzeRuntimeDistribution(movies)
                    val watchedByMonth = analyzeWatchedByMonth(movies)

                    //nuovi grafici
                    val avgRatingByGenre = analyzeAverageRatingByGenre(movies)
                    val actors = analyzeActors(movies)
                    val companies = analyzeProductionCompanies(movies)
                    val runtimeVsRating = analyzeRuntimeVsRating(movies)
                    val totalWatchTime = calculateTotalWatchTime(movies)
                    val genreCombinations = analyzeGenreCombinations(movies)

                    //pubblica risultati
                    _genresData.postValue(genres)
                    _yearsData.postValue(years)
                    _directorsData.postValue(directors)
                    _ratingsData.postValue(ratings)
                    _countriesData.postValue(countries)
                    _decadesData.postValue(decades)
                    _runtimeDistributionData.postValue(runtimeDist)
                    _watchedByMonthData.postValue(watchedByMonth)

                    _averageRatingByGenre.postValue(avgRatingByGenre)
                    _actorsData.postValue(actors)
                    _productionCompaniesData.postValue(companies)
                    _runtimeVsRatingData.postValue(runtimeVsRating)
                    _totalWatchTimeText.postValue(totalWatchTime)
                    _genreCombinationsData.postValue(genreCombinations)

                    //top
                    genres.maxByOrNull { it.value }?.let { _topGenre.postValue(it.key to it.value) }
                    years.maxByOrNull { it.value }?.let { _topYear.postValue(it.key to it.value) }
                    directors.maxByOrNull { it.value }?.let { _topDirector.postValue(it.key to it.value) }
                    countries.maxByOrNull { it.value }?.let { _topCountry.postValue(it.key to it.value) }
                    decades.maxByOrNull { it.value }?.let { _topDecade.postValue(it.key to it.value) }
                    actors.maxByOrNull { it.value }?.let { _topActor.postValue(it.key to it.value) }
                    companies.maxByOrNull { it.value }?.let { _topProductionCompany.postValue(it.key to it.value) }
                }

                _chartsReady.value = true
                Log.d(TAG, "grafici generati con successo")

            } catch (e: Exception) {
                Log.e(TAG, "errore generazione grafici", e)
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

        return genreCount.entries
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

        return yearCount.entries
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

        return directorCount.entries
            .sortedByDescending { it.value }
            .take(10)
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

        return ratingCount.entries
            .sortedByDescending { it.key }
            .associate { it.key to it.value }
    }

    private fun analyzeCountries(movies: List<Movie>): Map<String, Int> {
        val countryCount = mutableMapOf<String, Int>()
        countryMoviesMap.clear()

        Log.d(TAG, "=== analisi paesi ===")
        Log.d(TAG, "totale film: ${movies.size}")

        var moviesWithCountries = 0

        movies.forEach { movie ->
            if (movie.productionCountries.isNotEmpty()) {
                moviesWithCountries++

                movie.productionCountries.forEach { country ->
                    if (country.isNotBlank()) {
                        countryCount[country] = countryCount.getOrDefault(country, 0) + 1

                        val currentList = countryMoviesMap[country] ?: emptyList()
                        countryMoviesMap[country] = currentList + movie
                    }
                }
            }
        }

        Log.d(TAG, "film con paesi: $moviesWithCountries")
        Log.d(TAG, "paesi unici: ${countryCount.size}")

        if (countryCount.isEmpty()) {
            Log.w(TAG, "nessun paese trovato")
        }

        return countryCount.entries
            .sortedByDescending { it.value }
            .take(15)
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

        return decadeCount.entries
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
                Log.w(TAG, "errore parsing data: ${movie.dateRated}")
            }
        }

        return monthCount.entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    private fun analyzeAverageRatingByGenre(movies: List<Movie>): Map<String, Double> {
        val genreRatings = mutableMapOf<String, MutableList<Double>>()

        movies.filter { it.userRating != null }.forEach { movie ->
            movie.genres.forEach { genre ->
                if (genre.isNotBlank()) {
                    genreRatings.getOrPut(genre) { mutableListOf() }.add(movie.userRating!!)
                }
            }
        }

        return genreRatings.mapValues { (_, ratings) ->
            ratings.average()
        }.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    private fun analyzeActors(movies: List<Movie>): Map<String, Int> {
        val actorCount = mutableMapOf<String, Int>()
        actorMoviesMap.clear()

        movies.forEach { movie ->
            movie.cast.forEach { actor ->
                if (actor.isNotBlank()) {
                    actorCount[actor] = actorCount.getOrDefault(actor, 0) + 1
                    val currentList = actorMoviesMap[actor] ?: emptyList()
                    actorMoviesMap[actor] = currentList + movie
                }
            }
        }

        return actorCount.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    private fun analyzeProductionCompanies(movies: List<Movie>): Map<String, Int> {
        val companyCount = mutableMapOf<String, Int>()
        companyMoviesMap.clear()

        movies.forEach { movie ->
            movie.productionCompanies.forEach { company ->
                if (company.isNotBlank()) {
                    companyCount[company] = companyCount.getOrDefault(company, 0) + 1
                    val currentList = companyMoviesMap[company] ?: emptyList()
                    companyMoviesMap[company] = currentList + movie
                }
            }
        }

        return companyCount.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    private fun analyzeRuntimeVsRating(movies: List<Movie>): List<Pair<Int, Double>> {
        return movies.filter {
            it.runtime != null && it.userRating != null
        }.map { movie ->
            movie.runtime!! to movie.userRating!!
        }
    }

    private fun calculateTotalWatchTime(movies: List<Movie>): String {
        val watchedMovies = movies.filter { it.isWatched && it.runtime != null }
        val totalMinutes = watchedMovies.sumOf { it.runtime!! }

        val hours = totalMinutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "$days giorni e ${hours % 24} ore"
            else -> "$hours ore"
        }
    }

    private fun analyzeGenreCombinations(movies: List<Movie>): Map<String, Int> {
        val combinationCount = mutableMapOf<String, Int>()

        movies.forEach { movie ->
            val genres = movie.genres.filter { it.isNotBlank() }.sorted()

            for (i in genres.indices) {
                for (j in i + 1 until genres.size) {
                    val combination = "${genres[i]} + ${genres[j]}"
                    combinationCount[combination] = combinationCount.getOrDefault(combination, 0) + 1
                }
            }
        }

        return combinationCount.entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    fun getMoviesByGenre(genre: String): List<Movie> = genreMoviesMap[genre] ?: emptyList()
    fun getMoviesByYear(year: Int): List<Movie> = yearMoviesMap[year] ?: emptyList()
    fun getMoviesByDirector(director: String): List<Movie> = directorMoviesMap[director] ?: emptyList()
    fun getMoviesByRating(rating: Int): List<Movie> = ratingMoviesMap[rating] ?: emptyList()
    fun getMoviesByCountry(country: String): List<Movie> = countryMoviesMap[country] ?: emptyList()
    fun getMoviesByDecade(decade: String): List<Movie> = decadeMoviesMap[decade] ?: emptyList()
    fun getMoviesByRuntime(runtime: String): List<Movie> = runtimeMoviesMap[runtime] ?: emptyList()
    fun getMoviesByActor(actor: String): List<Movie> = actorMoviesMap[actor] ?: emptyList()
    fun getMoviesByCompany(company: String): List<Movie> = companyMoviesMap[company] ?: emptyList()
}