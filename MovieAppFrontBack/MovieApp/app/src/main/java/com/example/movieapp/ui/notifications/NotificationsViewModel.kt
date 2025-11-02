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

//viewmodel per grafici analytics avanzati
class NotificationsViewModel : ViewModel() {
    private val TAG = "NotificationsViewModel"

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _chartsReady = MutableLiveData<Boolean>()
    val chartsReady: LiveData<Boolean> = _chartsReady

    //contatori per card riepilogo
    private val _totalMovies = MutableLiveData<Int>()
    val totalMovies: LiveData<Int> = _totalMovies

    private val _watchedMovies = MutableLiveData<Int>()
    val watchedMovies: LiveData<Int> = _watchedMovies

    private val _watchlistMovies = MutableLiveData<Int>()
    val watchlistMovies: LiveData<Int> = _watchlistMovies

    //statistiche testuali
    private val _totalWatchTime = MutableLiveData<String>()
    val totalWatchTime: LiveData<String> = _totalWatchTime

    //dati per grafici base
    private val _genresData = MutableLiveData<Map<String, Int>>()
    val genresData: LiveData<Map<String, Int>> = _genresData

    private val _yearsData = MutableLiveData<Map<Int, Int>>()
    val yearsData: LiveData<Map<Int, Int>> = _yearsData

    private val _directorsData = MutableLiveData<Map<String, Int>>()
    val directorsData: LiveData<Map<String, Int>> = _directorsData

    private val _actorsData = MutableLiveData<Map<String, Int>>()
    val actorsData: LiveData<Map<String, Int>> = _actorsData

    private val _countriesData = MutableLiveData<Map<String, Int>>()
    val countriesData: LiveData<Map<String, Int>> = _countriesData

    private val _decadesData = MutableLiveData<Map<String, Int>>()
    val decadesData: LiveData<Map<String, Int>> = _decadesData

    private val _ratingsData = MutableLiveData<Map<String, Int>>()
    val ratingsData: LiveData<Map<String, Int>> = _ratingsData

    private val _genreCombinationsData = MutableLiveData<Map<Pair<String, String>, Int>>()
    val genreCombinationsData: LiveData<Map<Pair<String, String>, Int>> = _genreCombinationsData

    private val _runtimeRangesData = MutableLiveData<Map<String, Int>>()
    val runtimeRangesData: LiveData<Map<String, Int>> = _runtimeRangesData

    //grafico finale creativo (scatter plot rating vs anno)
    private val _ratingVsYearData = MutableLiveData<List<Triple<Int, Double, String>>>()
    val ratingVsYearData: LiveData<List<Triple<Int, Double, String>>> = _ratingVsYearData

    //statistiche top cliccabili
    private val _topGenre = MutableLiveData<Pair<String, Int>>()
    val topGenre: LiveData<Pair<String, Int>> = _topGenre

    private val _topYear = MutableLiveData<Pair<Int, Int>>()
    val topYear: LiveData<Pair<Int, Int>> = _topYear

    private val _topDirector = MutableLiveData<Pair<String, Int>>()
    val topDirector: LiveData<Pair<String, Int>> = _topDirector

    private val _topActor = MutableLiveData<Pair<String, Int>>()
    val topActor: LiveData<Pair<String, Int>> = _topActor

    private val _topCountry = MutableLiveData<Pair<String, Int>>()
    val topCountry: LiveData<Pair<String, Int>> = _topCountry

    private val _topDecade = MutableLiveData<Pair<String, Int>>()
    val topDecade: LiveData<Pair<String, Int>> = _topDecade

    private val _topGenreCombination = MutableLiveData<Pair<Pair<String, String>, Int>>()
    val topGenreCombination: LiveData<Pair<Pair<String, String>, Int>> = _topGenreCombination

    private val _longestMovies = MutableLiveData<List<Movie>>()
    val longestMovies: LiveData<List<Movie>> = _longestMovies

    //testi descrittivi decenni
    private val _decadeDescription = MutableLiveData<String>()
    val decadeDescription: LiveData<String> = _decadeDescription

    //mappe per recuperare film per categoria
    private val genreMoviesMap = mutableMapOf<String, List<Movie>>()
    private val yearMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val directorMoviesMap = mutableMapOf<String, List<Movie>>()
    private val actorMoviesMap = mutableMapOf<String, List<Movie>>()
    private val countryMoviesMap = mutableMapOf<String, List<Movie>>()
    private val decadeMoviesMap = mutableMapOf<String, List<Movie>>()
    private val ratingMoviesMap = mutableMapOf<Int, List<Movie>>()
    private val genreCombinationMoviesMap = mutableMapOf<Pair<String, String>, List<Movie>>()
    private val runtimeRangeMoviesMap = mutableMapOf<String, List<Movie>>()

    private lateinit var repository: MovieRepository

    fun initialize(context: Context) {
        repository = MovieRepository.getInstance(context)
        loadMovies()
    }

    fun refreshData() {
        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            try {
                Log.d(TAG, "=== caricamento movies per analytics ===")

                val allMovies = repository.movies.value ?: emptyList()
                _movies.value = allMovies

                Log.d(TAG, "film caricati: ${allMovies.size}")

                //aggiorna contatori
                _totalMovies.value = allMovies.size
                _watchedMovies.value = allMovies.count { it.isWatched }
                _watchlistMovies.value = allMovies.count { !it.isWatched }

                //genera analytics
                generateAnalytics(allMovies)

                _chartsReady.value = true
            } catch (e: Exception) {
                Log.e(TAG, "errore loadMovies: ${e.message}", e)
                _chartsReady.value = false
            }
        }
    }

    private suspend fun generateAnalytics(allMovies: List<Movie>) {
        withContext(Dispatchers.Default) {
            //usa TUTTI i film (watched + watchlist)
            val moviesWithRating = allMovies.filter { it.userRating != null && it.userRating!! > 0 }

            Log.d(TAG, "=== calcolo statistiche ===")
            Log.d(TAG, "film totali: ${allMovies.size}")
            Log.d(TAG, "film con rating: ${moviesWithRating.size}")

            //statistiche base
            calculateGenresData(allMovies)
            calculateYearsData(allMovies)
            calculateDirectorsData(allMovies)
            calculateActorsData(allMovies)
            calculateRatingsData(moviesWithRating)
            calculateCountriesData(allMovies)
            calculateDecadesData(allMovies)
            calculateGenreCombinationsData(allMovies)
            calculateRuntimeRangesData(allMovies)
            calculateRatingVsYearData(moviesWithRating)

            //calcola tempo totale (solo film visti)
            val watchedMovies = allMovies.filter { it.isWatched }
            val totalMinutes = watchedMovies.mapNotNull { it.runtime }.sum()
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            _totalWatchTime.postValue("$hours ore e $minutes minuti - Durata totale film")

            Log.d(TAG, "=== statistiche calcolate ===")
        }
    }

    private fun calculateGenresData(movies: List<Movie>) {
        val genresMap = mutableMapOf<String, MutableList<Movie>>()

        movies.forEach { movie ->
            movie.genres.forEach { genre ->
                genresMap.getOrPut(genre) { mutableListOf() }.add(movie)
            }
        }

        genreMoviesMap.clear()
        genreMoviesMap.putAll(genresMap.mapValues { it.value.toList() })

        val genresCounts = genresMap.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .toMap()

        _genresData.postValue(genresCounts)

        val topGenreEntry = genresMap.maxByOrNull { it.value.size }
        topGenreEntry?.let {
            _topGenre.postValue(Pair(it.key, it.value.size))
        }
    }

    private fun calculateYearsData(movies: List<Movie>) {
        val yearsMap = mutableMapOf<Int, MutableList<Movie>>()

        movies.forEach { movie ->
            movie.year?.let { year ->
                yearsMap.getOrPut(year) { mutableListOf() }.add(movie)
            }
        }

        yearMoviesMap.clear()
        yearMoviesMap.putAll(yearsMap.mapValues { it.value.toList() })

        val yearsCounts = yearsMap.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.first }
            .take(15)
            .sortedBy { it.first }
            .toMap()

        _yearsData.postValue(yearsCounts)

        val topYearEntry = yearsMap.maxByOrNull { it.value.size }
        topYearEntry?.let {
            _topYear.postValue(Pair(it.key, it.value.size))
        }
    }

    private fun calculateDirectorsData(movies: List<Movie>) {
        val directorsMap = mutableMapOf<String, MutableList<Movie>>()

        movies.forEach { movie ->
            movie.director?.let { director ->
                if (director.isNotEmpty() && director != "N/A") {
                    directorsMap.getOrPut(director) { mutableListOf() }.add(movie)
                }
            }
        }

        directorMoviesMap.clear()
        directorMoviesMap.putAll(directorsMap.mapValues { it.value.toList() })

        val directorsCounts = directorsMap.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .toMap()

        _directorsData.postValue(directorsCounts)

        val topDirectorEntry = directorsMap.maxByOrNull { it.value.size }
        topDirectorEntry?.let {
            _topDirector.postValue(Pair(it.key, it.value.size))
        }
    }

    private fun calculateActorsData(movies: List<Movie>) {
        val actorsMap = mutableMapOf<String, MutableList<Movie>>()

        movies.forEach { movie ->
            movie.actors.forEach { actor ->
                if (actor.isNotEmpty() && actor != "N/A") {
                    actorsMap.getOrPut(actor) { mutableListOf() }.add(movie)
                }
            }
        }

        actorMoviesMap.clear()
        actorMoviesMap.putAll(actorsMap.mapValues { it.value.toList() })

        val actorsCounts = actorsMap.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .toMap()

        _actorsData.postValue(actorsCounts)

        val topActorEntry = actorsMap.maxByOrNull { it.value.size }
        topActorEntry?.let {
            _topActor.postValue(Pair(it.key, it.value.size))
        }
    }

    private fun calculateRatingsData(moviesWithRating: List<Movie>) {
        Log.d(TAG, "calculateRatingsData: film con rating = ${moviesWithRating.size}")

        if (moviesWithRating.isEmpty()) {
            Log.w(TAG, "nessun film con rating disponibile")
            _ratingsData.postValue(emptyMap())
            return
        }

        val ratingsMap = mutableMapOf<Int, MutableList<Movie>>()

        moviesWithRating.forEach { movie ->
            movie.userRating?.toInt()?.let { rating ->
                Log.d(TAG, "film: ${movie.title}, rating: $rating")
                ratingsMap.getOrPut(rating) { mutableListOf() }.add(movie)
            }
        }

        ratingMoviesMap.clear()
        ratingMoviesMap.putAll(ratingsMap.mapValues { it.value.toList() })

        val ratingsCounts = ratingsMap.mapValues { it.value.size }
            .mapKeys { it.key.toString() }

        Log.d(TAG, "ratings calcolati: $ratingsCounts")
        _ratingsData.postValue(ratingsCounts)
    }

    private fun calculateCountriesData(movies: List<Movie>) {
        Log.d(TAG, "calculateCountriesData: totale film = ${movies.size}")

        val countriesMap = mutableMapOf<String, MutableList<Movie>>()

        movies.forEach { movie ->
            Log.d(TAG, "film: ${movie.title}, paesi: ${movie.productionCountries}")

            if (movie.productionCountries.isNotEmpty()) {
                movie.productionCountries.forEach { country ->
                    if (country.isNotEmpty() && country != "N/A") {
                        countriesMap.getOrPut(country) { mutableListOf() }.add(movie)
                    }
                }
            }
        }

        Log.d(TAG, "countriesMap size: ${countriesMap.size}")
        countriesMap.forEach { (country, moviesList) ->
            Log.d(TAG, "paese: $country, film: ${moviesList.size}")
        }

        countryMoviesMap.clear()
        countryMoviesMap.putAll(countriesMap.mapValues { it.value.toList() })

        val countriesCounts = countriesMap.mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .toMap()

        Log.d(TAG, "paesi calcolati: $countriesCounts")
        _countriesData.postValue(countriesCounts)

        val topCountryEntry = countriesMap.maxByOrNull { it.value.size }
        topCountryEntry?.let {
            _topCountry.postValue(Pair(it.key, it.value.size))
        }
    }

    private fun calculateDecadesData(movies: List<Movie>) {
        val decadesMap = mutableMapOf<String, MutableList<Movie>>()

        movies.forEach { movie ->
            movie.year?.let { year ->
                val decade = "${(year / 10) * 10}s"
                decadesMap.getOrPut(decade) { mutableListOf() }.add(movie)
            }
        }

        decadeMoviesMap.clear()
        decadeMoviesMap.putAll(decadesMap.mapValues { it.value.toList() })

        val decadesCounts = decadesMap.mapValues { it.value.size }
            .toList()
            .sortedBy { it.first }
            .toMap()

        _decadesData.postValue(decadesCounts)

        val topDecadeEntry = decadesMap.maxByOrNull { it.value.size }
        topDecadeEntry?.let {
            _topDecade.postValue(Pair(it.key, it.value.size))

            val description = when {
                it.key.startsWith("192") -> "cinema muto e primi sonori"
                it.key.startsWith("193") -> "età d'oro di hollywood"
                it.key.startsWith("194") -> "dopoguerra e neorealismo"
                it.key.startsWith("195") -> "nascita della nouvelle vague"
                it.key.startsWith("196") -> "new hollywood e sperimentazione"
                it.key.startsWith("197") -> "blockbuster e nuovi effetti speciali"
                it.key.startsWith("198") -> "cinema d'autore e action movie"
                it.key.startsWith("199") -> "cgi e cinema indipendente"
                it.key.startsWith("200") -> "superhero e franchise"
                it.key.startsWith("201") -> "streaming e cinema digitale"
                it.key.startsWith("202") -> "era post-pandemica"
                else -> "periodo cinematografico"
            }
            _decadeDescription.postValue(description)
        }
    }

    private fun calculateGenreCombinationsData(movies: List<Movie>) {
        val combinationsMap = mutableMapOf<Pair<String, String>, MutableList<Movie>>()

        movies.forEach { movie ->
            if (movie.genres.size >= 2) {
                for (i in 0 until movie.genres.size - 1) {
                    for (j in i + 1 until movie.genres.size) {
                        val genre1 = movie.genres[i]
                        val genre2 = movie.genres[j]
                        val pair = if (genre1 < genre2) Pair(genre1, genre2) else Pair(genre2, genre1)
                        combinationsMap.getOrPut(pair) { mutableListOf() }.add(movie)
                    }
                }
            }
        }

        genreCombinationMoviesMap.clear()
        genreCombinationMoviesMap.putAll(combinationsMap.mapValues { it.value.distinct() })

        val combinationsCounts = combinationsMap.mapValues { it.value.distinct().size }
            .toList()
            .sortedByDescending { it.second }
            .take(10)
            .toMap()

        _genreCombinationsData.postValue(combinationsCounts)

        val topCombination = combinationsMap.maxByOrNull { it.value.distinct().size }
        topCombination?.let {
            _topGenreCombination.postValue(Pair(it.key, it.value.distinct().size))
        }
    }

    private fun calculateRuntimeRangesData(movies: List<Movie>) {
        val moviesWithRuntime = movies.filter { it.runtime != null && it.runtime!! > 0 }

        val rangesMap = mutableMapOf<String, MutableList<Movie>>()

        moviesWithRuntime.forEach { movie ->
            val runtime = movie.runtime!!
            val range = when {
                runtime < 90 -> "< 90 min"
                runtime < 120 -> "90-120 min"
                runtime < 150 -> "120-150 min"
                else -> "> 150 min"
            }
            rangesMap.getOrPut(range) { mutableListOf() }.add(movie)
        }

        runtimeRangeMoviesMap.clear()
        runtimeRangeMoviesMap.putAll(rangesMap.mapValues { it.value.toList() })

        val orderedRanges = listOf("< 90 min", "90-120 min", "120-150 min", "> 150 min")
        val rangesCounts = orderedRanges.associateWith { range ->
            rangesMap[range]?.size ?: 0
        }.filter { it.value > 0 }

        _runtimeRangesData.postValue(rangesCounts)

        val longestMoviesList = moviesWithRuntime
            .filter { it.runtime != null && it.runtime!! > 0 }
            .sortedByDescending { it.runtime }
            .take(10)

        _longestMovies.postValue(longestMoviesList)
    }

    private fun calculateRatingVsYearData(moviesWithRating: List<Movie>) {
        val data = moviesWithRating
            .filter { it.year != null && it.userRating != null && it.userRating!! > 0 }
            .map { Triple(it.year!!, it.userRating!!, it.title) }

        _ratingVsYearData.postValue(data)
    }

    //funzioni per recuperare film per categoria
    fun getMoviesByGenre(genre: String): List<Movie> = genreMoviesMap[genre] ?: emptyList()
    fun getMoviesByYear(year: Int): List<Movie> = yearMoviesMap[year] ?: emptyList()
    fun getMoviesByDirector(director: String): List<Movie> = directorMoviesMap[director] ?: emptyList()
    fun getMoviesByActor(actor: String): List<Movie> = actorMoviesMap[actor] ?: emptyList()
    fun getMoviesByRating(rating: Int): List<Movie> = ratingMoviesMap[rating] ?: emptyList()
    fun getMoviesByCountry(country: String): List<Movie> = countryMoviesMap[country] ?: emptyList()
    fun getMoviesByDecade(decade: String): List<Movie> = decadeMoviesMap[decade] ?: emptyList()
    fun getMoviesByGenreCombination(pair: Pair<String, String>): List<Movie> = genreCombinationMoviesMap[pair] ?: emptyList()
    fun getMoviesByRuntimeRange(range: String): List<Movie> = runtimeRangeMoviesMap[range] ?: emptyList()
}