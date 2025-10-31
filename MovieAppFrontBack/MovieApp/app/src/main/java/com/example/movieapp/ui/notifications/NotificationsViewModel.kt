package com.example.movieapp.ui.notifications

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.MovieRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context

/**
 * viewmodel per grafici analytics avanzati
 * fix: usa MovieRepository invece di ApiService
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

    //mappe per recuperare film per categoria
    private val genreMoviesMap = mutableMapOf<String, List<Movie>>()
    private val yearMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val directorMoviesMap = mutableMapOf<String, List<Movie>>()
    private val countryMoviesMap = mutableMapOf<String, List<Movie>>()
    private val decadeMoviesMap = mutableMapOf<String, List<Movie>>()
    private val ratingMoviesMap = mutableMapOf<Int, List<Movie>>()

    private lateinit var repository: MovieRepository

    /**
     * inizializza repository e carica movies
     */
    fun initialize(context: Context) {
        repository = MovieRepository.getInstance(context)
        loadMovies()
    }

    /**
     * refresh dati
     */
    fun refreshData() {
        loadMovies()
    }

    /**
     * carica movies e genera analytics
     */
    private fun loadMovies() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== caricamento movies per analytics ===")

                //usa MovieRepository invece di ApiService
                val allMovies = repository.movies.value ?: emptyList()
                _movies.value = allMovies

                Log.d(TAG, "film caricati: ${allMovies.size}")

                //genera analytics
                generateAnalytics(allMovies)

                _chartsReady.value = true
            } catch (e: Exception) {
                Log.e(TAG, "errore loadMovies: ${e.message}", e)
                _chartsReady.value = false
            }
        }
    }

    /**
     * genera tutte le analytics
     */
    private suspend fun generateAnalytics(allMovies: List<Movie>) {
        withContext(Dispatchers.Default) {
            //separa watched e watchlist
            val watched = allMovies.filter { it.isWatched }
            val watchedWithRating = watched.filter { it.userRating != null && it.userRating!! > 0 }

            Log.d(TAG, "analisi: ${watched.size} watched, ${watchedWithRating.size} con rating")

            //calcola tempo totale
            val totalMinutes = watched.mapNotNull { it.runtime }.sum()
            val hours = totalMinutes / 60
            val days = hours / 24
            _totalWatchTime.postValue("$days giorni ($hours ore)")

            //genera dati per grafici (solo watched)
            _genresData.postValue(analyzeGenres(watched))
            _yearsData.postValue(analyzeYears(watched))
            _directorsData.postValue(analyzeDirectors(watched))
            _countriesData.postValue(analyzeCountries(watched))
            _decadesData.postValue(analyzeDecades(watched))

            //genera dati per grafici con rating (solo watched con userRating)
            if (watchedWithRating.isNotEmpty()) {
                _ratingsData.postValue(analyzeRatings(watchedWithRating))
                _genreRatingsData.postValue(analyzeGenreRatings(watchedWithRating))
                _decadeRatingsData.postValue(analyzeDecadeRatings(watchedWithRating))
                _runtimeVsRatingData.postValue(analyzeRuntimeVsRating(watchedWithRating))
            }

            Log.d(TAG, "analytics generate con successo")
        }
    }

    /**
     * analizza generi (solo watched)
     */
    private fun analyzeGenres(watched: List<Movie>): Map<String, Int> {
        val genreCounts = mutableMapOf<String, Int>()
        genreMoviesMap.clear()

        watched.forEach { movie ->
            movie.genres?.forEach { genre ->
                genreCounts[genre] = (genreCounts[genre] ?: 0) + 1

                if (!genreMoviesMap.containsKey(genre)) {
                    genreMoviesMap[genre] = mutableListOf()
                }
                (genreMoviesMap[genre] as MutableList).add(movie)
            }
        }

        //salva top genere
        genreCounts.maxByOrNull { it.value }?.let { (genre, count) ->
            _topGenre.postValue(genre to count)
        }

        return genreCounts.entries.sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    /**
     * analizza anni (solo watched)
     */
    private fun analyzeYears(watched: List<Movie>): Map<Int, Int> {
        val yearCounts = mutableMapOf<Int, Int>()
        yearMoviesMap.clear()

        watched.forEach { movie ->
            movie.year?.let { year ->
                yearCounts[year] = (yearCounts[year] ?: 0) + 1

                if (!yearMoviesMap.containsKey(year)) {
                    yearMoviesMap[year] = mutableListOf()
                }
                (yearMoviesMap[year] as MutableList).add(movie)
            }
        }

        //salva top anno
        yearCounts.maxByOrNull { it.value }?.let { (year, count) ->
            _topYear.postValue(year to count)
        }

        return yearCounts.entries.sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    /**
     * analizza registi (solo watched)
     */
    private fun analyzeDirectors(watched: List<Movie>): Map<String, Int> {
        val directorCounts = mutableMapOf<String, Int>()
        directorMoviesMap.clear()

        watched.forEach { movie ->
            movie.director?.let { director ->
                directorCounts[director] = (directorCounts[director] ?: 0) + 1

                if (!directorMoviesMap.containsKey(director)) {
                    directorMoviesMap[director] = mutableListOf()
                }
                (directorMoviesMap[director] as MutableList).add(movie)
            }
        }

        //salva top regista
        directorCounts.maxByOrNull { it.value }?.let { (director, count) ->
            _topDirector.postValue(director to count)
        }

        return directorCounts.entries.sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    /**
     * analizza paesi (solo watched) - usa productionCountries
     */
    private fun analyzeCountries(watched: List<Movie>): Map<String, Int> {
        val countryCounts = mutableMapOf<String, Int>()
        countryMoviesMap.clear()

        watched.forEach { movie ->
            movie.productionCountries?.forEach { country ->
                countryCounts[country] = (countryCounts[country] ?: 0) + 1

                if (!countryMoviesMap.containsKey(country)) {
                    countryMoviesMap[country] = mutableListOf()
                }
                (countryMoviesMap[country] as MutableList).add(movie)
            }
        }

        //salva top paese
        countryCounts.maxByOrNull { it.value }?.let { (country, count) ->
            _topCountry.postValue(country to count)
        }

        return countryCounts.entries.sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    /**
     * analizza decenni (solo watched)
     */
    private fun analyzeDecades(watched: List<Movie>): Map<String, Int> {
        val decadeCounts = mutableMapOf<String, Int>()
        decadeMoviesMap.clear()

        watched.forEach { movie ->
            movie.year?.let { year ->
                val decade = "${(year / 10) * 10}s"
                decadeCounts[decade] = (decadeCounts[decade] ?: 0) + 1

                if (!decadeMoviesMap.containsKey(decade)) {
                    decadeMoviesMap[decade] = mutableListOf()
                }
                (decadeMoviesMap[decade] as MutableList).add(movie)
            }
        }

        //salva top decade
        decadeCounts.maxByOrNull { it.value }?.let { (decade, count) ->
            _topDecade.postValue(decade to count)
        }

        return decadeCounts.entries.sortedBy { it.key }
            .associate { it.key to it.value }
    }

    /**
     * analizza distribuzione rating (solo watched con rating)
     */
    private fun analyzeRatings(watchedWithRating: List<Movie>): Map<String, Int> {
        val ratingCounts = mutableMapOf<Int, Int>()
        ratingMoviesMap.clear()

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
     * analizza rating medio per genere (solo watched con rating) - ritorna Float
     */
    private fun analyzeGenreRatings(watchedWithRating: List<Movie>): Map<String, Float> {
        val genreRatings = mutableMapOf<String, MutableList<Float>>()

        watchedWithRating.forEach { movie ->
            movie.userRating?.let { rating ->
                movie.genres?.forEach { genre ->
                    if (!genreRatings.containsKey(genre)) {
                        genreRatings[genre] = mutableListOf()
                    }
                    genreRatings[genre]?.add(rating.toFloat())
                }
            }
        }

        return genreRatings.entries
            .map { it.key to (it.value.sum() / it.value.size).toFloat() }
            .sortedByDescending { it.second }
            .take(10)
            .associate { it.first to it.second }
    }

    /**
     * analizza rating medio per decennio (solo watched con rating) - ritorna Float
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
                    decadeRatings[decade]?.add(rating.toFloat())
                }
            }
        }

        return decadeRatings.entries
            .map { it.key to (it.value.sum() / it.value.size).toFloat() }
            .sortedBy { it.first }
            .associate { it.first to it.second }
    }

    /**
     * analizza durata vs rating (solo watched con rating) - ritorna Float
     */
    private fun analyzeRuntimeVsRating(watchedWithRating: List<Movie>): List<Pair<Int, Float>> {
        return watchedWithRating
            .filter { it.runtime != null && it.userRating != null }
            .map { it.runtime!! to it.userRating!!.toFloat() }
    }
    
    //funzioni per recuperare film per categoria (per i click)
    fun getMoviesByGenre(genre: String): List<Movie> = genreMoviesMap[genre] ?: emptyList()
    fun getMoviesByYear(year: Int): List<Movie> = yearMoviesMap[year] ?: emptyList()
    fun getMoviesByDirector(director: String): List<Movie> = directorMoviesMap[director] ?: emptyList()
    fun getMoviesByRating(rating: Int): List<Movie> = ratingMoviesMap[rating] ?: emptyList()
    fun getMoviesByCountry(country: String): List<Movie> = countryMoviesMap[country] ?: emptyList()
    fun getMoviesByDecade(decade: String): List<Movie> = decadeMoviesMap[decade] ?: emptyList()
}