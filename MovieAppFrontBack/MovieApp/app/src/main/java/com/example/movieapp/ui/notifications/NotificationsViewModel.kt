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

    private val _tmdbRatingsData = MutableLiveData<Map<Int, Int>>()
    val tmdbRatingsData: LiveData<Map<Int, Int>> = _tmdbRatingsData

    private val _genreCombinationsData = MutableLiveData<Map<Pair<String, String>, Int>>()
    val genreCombinationsData: LiveData<Map<Pair<String, String>, Int>> = _genreCombinationsData

    private val _runtimeRangesData = MutableLiveData<Map<String, Int>>()
    val runtimeRangesData: LiveData<Map<String, Int>> = _runtimeRangesData

    private val _originalLanguagesData = MutableLiveData<Map<String, Int>>()
    val originalLanguagesData: LiveData<Map<String, Int>> = _originalLanguagesData

    //grafici avanzati
    private val _popularityVsRatingData = MutableLiveData<List<Pair<Double, Double>>>()
    val popularityVsRatingData: LiveData<List<Pair<Double, Double>>> = _popularityVsRatingData

    private val _popularityTrendData = MutableLiveData<Map<String, Double>>()
    val popularityTrendData: LiveData<Map<String, Double>> = _popularityTrendData

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

    private val _topOriginalLanguage = MutableLiveData<Pair<String, Int>>()
    val topOriginalLanguage: LiveData<Pair<String, Int>> = _topOriginalLanguage

    //cache film per recupero veloce e per mappatura cognome->nome completo
    private var allMoviesCache: List<Movie> = emptyList()
    private val directorLastNameToFullName = mutableMapOf<String, String>()
    private val actorLastNameToFullName = mutableMapOf<String, String>()

    fun initialize(context: Context) {
        loadMovies(context)
    }

    fun refreshData() {
        _chartsReady.value = false
        _movies.value?.let { movies ->
            if (movies.isNotEmpty()) {
                viewModelScope.launch {
                    generateAnalytics(movies)
                    _chartsReady.value = true
                }
            }
        }
    }

    private fun loadMovies(context: Context) {
        viewModelScope.launch {
            try {
                val repository = MovieRepository.getInstance(context)

                //usa il LiveData del repository
                val allMovies = repository.movies.value ?: emptyList()

                allMoviesCache = allMovies
                _movies.value = allMovies

                Log.d(TAG, "film caricati: ${allMovies.size}")

                //aggiorna contatori
                _totalMovies.value = allMovies.size
                _watchedMovies.value = allMovies.count { movie: Movie -> movie.isWatched }
                _watchlistMovies.value = allMovies.count { movie: Movie -> !movie.isWatched }

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
            Log.d(TAG, "=== calcolo statistiche ===")
            Log.d(TAG, "film totali: ${allMovies.size}")

            //statistiche base
            calculateGenresData(allMovies)
            calculateYearsData(allMovies)
            calculateDirectorsData(allMovies)
            calculateActorsData(allMovies)
            calculateTmdbRatingsData(allMovies)
            calculateCountriesData(allMovies)
            calculateDecadesData(allMovies)
            calculateGenreCombinationsData(allMovies)
            calculateRuntimeRangesData(allMovies)
            calculateOriginalLanguagesData(allMovies)

            //grafici avanzati
            calculatePopularityVsRatingData(allMovies)
            calculatePopularityTrendData(allMovies)

            //tempo totale visione
            calculateTotalWatchTime(allMovies)
        }
    }

    private fun calculateGenresData(movies: List<Movie>) {
        val genreCount = mutableMapOf<String, Int>()
        movies.forEach { movie ->
            movie.genres.forEach { genre ->
                genreCount[genre] = (genreCount[genre] ?: 0) + 1
            }
        }

        _genresData.postValue(genreCount.toSortedMap())

        if (genreCount.isNotEmpty()) {
            val top = genreCount.maxByOrNull { it.value }!!
            _topGenre.postValue(top.key to top.value)
        }

        Log.d(TAG, "generi: ${genreCount.size} generi trovati")
    }

    private fun calculateYearsData(movies: List<Movie>) {
        val yearCount = mutableMapOf<Int, Int>()
        movies.forEach { movie ->
            movie.year?.let { year ->
                yearCount[year] = (yearCount[year] ?: 0) + 1
            }
        }

        _yearsData.postValue(yearCount)

        if (yearCount.isNotEmpty()) {
            val top = yearCount.maxByOrNull { it.value }!!
            _topYear.postValue(top.key to top.value)
        }

        Log.d(TAG, "anni: ${yearCount.size} anni trovati")
    }

    private fun calculateDirectorsData(movies: List<Movie>) {
        val directorCount = mutableMapOf<String, Int>()
        directorLastNameToFullName.clear()

        movies.forEach { movie ->
            movie.director?.let { fullName ->
                val lastName = extractLastName(fullName)
                directorCount[lastName] = (directorCount[lastName] ?: 0) + 1
                //salva mapping cognome -> nome completo
                if (!directorLastNameToFullName.containsKey(lastName)) {
                    directorLastNameToFullName[lastName] = fullName
                }
            }
        }

        _directorsData.postValue(directorCount)

        if (directorCount.isNotEmpty()) {
            val top = directorCount.maxByOrNull { it.value }!!
            val fullName = directorLastNameToFullName[top.key] ?: top.key
            _topDirector.postValue(fullName to top.value)
        }

        Log.d(TAG, "registi: ${directorCount.size} registi trovati")
    }

    private fun calculateActorsData(movies: List<Movie>) {
        val actorCount = mutableMapOf<String, Int>()
        actorLastNameToFullName.clear()

        movies.forEach { movie ->
            movie.actors?.forEach { fullName ->
                val lastName = extractLastName(fullName)
                actorCount[lastName] = (actorCount[lastName] ?: 0) + 1
                //salva mapping cognome -> nome completo
                if (!actorLastNameToFullName.containsKey(lastName)) {
                    actorLastNameToFullName[lastName] = fullName
                }
            }
        }

        _actorsData.postValue(actorCount)

        if (actorCount.isNotEmpty()) {
            val top = actorCount.maxByOrNull { it.value }!!
            val fullName = actorLastNameToFullName[top.key] ?: top.key
            _topActor.postValue(fullName to top.value)
        }

        Log.d(TAG, "attori: ${actorCount.size} attori trovati")
    }

    //estrae solo il cognome da un nome completo
    private fun extractLastName(fullName: String): String {
        val parts = fullName.trim().split(" ")
        return if (parts.size > 1) parts.last() else fullName
    }

    //ottieni nome completo da cognome (per dialog)
    fun getDirectorFullName(lastName: String): String {
        return directorLastNameToFullName[lastName] ?: lastName
    }

    fun getActorFullName(lastName: String): String {
        return actorLastNameToFullName[lastName] ?: lastName
    }

    private fun calculateTmdbRatingsData(movies: List<Movie>) {
        val ratingCount = mutableMapOf<Int, Int>()

        //inizializza tutti i valori da 0 a 10
        for (i in 0..10) {
            ratingCount[i] = 0
        }

        movies.forEach { movie ->
            movie.tmdbRating?.let { rating ->
                val roundedRating = rating.toInt().coerceIn(0, 10)
                ratingCount[roundedRating] = (ratingCount[roundedRating] ?: 0) + 1
            }
        }

        _tmdbRatingsData.postValue(ratingCount)
        Log.d(TAG, "rating tmdb: distribuzioni calcolate da 0 a 10")
    }

    private fun calculateCountriesData(movies: List<Movie>) {
        val countryCount = mutableMapOf<String, Int>()
        movies.forEach { movie ->
            movie.productionCountries.forEach { country ->
                val abbreviatedCountry = abbreviateCountryName(country)
                countryCount[abbreviatedCountry] = (countryCount[abbreviatedCountry] ?: 0) + 1
            }
        }

        _countriesData.postValue(countryCount)

        if (countryCount.isNotEmpty()) {
            val top = countryCount.maxByOrNull { it.value }!!
            _topCountry.postValue(top.key to top.value)
        }

        Log.d(TAG, "paesi: ${countryCount.size} paesi trovati")
    }

    //abbrevia nomi paesi lunghi
    private fun abbreviateCountryName(country: String): String {
        return when (country.trim()) {
            "United States of America" -> "USA"
            "United Kingdom" -> "UK"
            else -> country
        }
    }

    private fun calculateDecadesData(movies: List<Movie>) {
        val decadeCount = mutableMapOf<String, Int>()
        movies.forEach { movie ->
            movie.year?.let { year ->
                val decade = "${(year / 10) * 10}s"
                decadeCount[decade] = (decadeCount[decade] ?: 0) + 1
            }
        }

        _decadesData.postValue(decadeCount)

        if (decadeCount.isNotEmpty()) {
            val top = decadeCount.maxByOrNull { it.value }!!
            _topDecade.postValue(top.key to top.value)
        }

        Log.d(TAG, "decenni: ${decadeCount.size} decenni trovati")
    }

    private fun calculateGenreCombinationsData(movies: List<Movie>) {
        val combinations = mutableMapOf<Pair<String, String>, Int>()

        movies.forEach { movie ->
            val genres = movie.genres
            if (genres.size >= 2) {
                //prende tutte le coppie di generi
                for (i in genres.indices) {
                    for (j in i + 1 until genres.size) {
                        val pair = if (genres[i] <= genres[j]) {
                            Pair(genres[i], genres[j])
                        } else {
                            Pair(genres[j], genres[i])
                        }
                        combinations[pair] = (combinations[pair] ?: 0) + 1
                    }
                }
            }
        }

        _genreCombinationsData.postValue(combinations)

        if (combinations.isNotEmpty()) {
            val top = combinations.maxByOrNull { it.value }!!
            _topGenreCombination.postValue(top.key to top.value)
        }

        Log.d(TAG, "combinazioni generi: ${combinations.size} combinazioni trovate")
    }

    private fun calculateRuntimeRangesData(movies: List<Movie>) {
        val ranges = mutableMapOf<String, Int>()
        ranges["0-60"] = 0
        ranges["60-90"] = 0
        ranges["90-120"] = 0
        ranges["120-150"] = 0
        ranges["150-180"] = 0
        ranges["180+"] = 0

        movies.forEach { movie ->
            movie.runtime?.let { runtime ->
                val range = when {
                    runtime < 60 -> "0-60"
                    runtime < 90 -> "60-90"
                    runtime < 120 -> "90-120"
                    runtime < 150 -> "120-150"
                    runtime < 180 -> "150-180"
                    else -> "180+"
                }
                ranges[range] = (ranges[range] ?: 0) + 1
            }
        }

        _runtimeRangesData.postValue(ranges)

        //calcola film piu lunghi
        val sortedByRuntime = movies.filter { it.runtime != null }
            .sortedByDescending { it.runtime }
            .take(5)
        _longestMovies.postValue(sortedByRuntime)

        Log.d(TAG, "runtime ranges calcolati")
    }

    private fun calculateOriginalLanguagesData(movies: List<Movie>) {
        val langCount = mutableMapOf<String, Int>()
        movies.forEach { movie ->
            movie.originalLanguage?.let { lang ->
                langCount[lang] = (langCount[lang] ?: 0) + 1
            }
        }

        _originalLanguagesData.postValue(langCount)

        if (langCount.isNotEmpty()) {
            val top = langCount.maxByOrNull { it.value }!!
            _topOriginalLanguage.postValue(top.key to top.value)
        }

        Log.d(TAG, "lingue: ${langCount.size} lingue trovate")
    }

    private fun calculatePopularityVsRatingData(movies: List<Movie>) {
        val data = movies.mapNotNull { movie ->
            val pop = movie.popularity
            val rating = movie.tmdbRating
            if (pop != null && rating != null) {
                Pair(pop, rating)
            } else null
        }

        _popularityVsRatingData.postValue(data)
        Log.d(TAG, "popularity vs rating: ${data.size} punti")
    }

    private fun calculatePopularityTrendData(movies: List<Movie>) {
        val decadePopularity = mutableMapOf<String, MutableList<Double>>()

        movies.forEach { movie ->
            movie.year?.let { year ->
                val decade = "${(year / 10) * 10}s"
                movie.popularity?.let { pop ->
                    decadePopularity.getOrPut(decade) { mutableListOf() }.add(pop)
                }
            }
        }

        val avgPopularity = decadePopularity.mapValues { entry ->
            entry.value.average()
        }

        _popularityTrendData.postValue(avgPopularity)
        Log.d(TAG, "popularity trend: ${avgPopularity.size} decenni")
    }

    private fun calculateTotalWatchTime(movies: List<Movie>) {
        val watchedMovies = movies.filter { it.isWatched }
        val totalMinutes = watchedMovies.sumOf { it.runtime ?: 0 }
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        val timeText = if (hours > 0) {
            "⏱️ Durata Totale Film: $hours ore e $minutes minuti"
        } else {
            "⏱️ Durata Totale Film: $minutes minuti"
        }

        _totalWatchTime.postValue(timeText)
        Log.d(TAG, "tempo totale: $hours ore, $minutes minuti")
    }

    //metodi per recuperare film per categoria (per dialog)
    fun getMoviesByGenre(genre: String): List<Movie> {
        return allMoviesCache.filter { it.genres.contains(genre) }
    }

    fun getMoviesByYear(year: Int): List<Movie> {
        return allMoviesCache.filter { it.year == year }
    }

    fun getMoviesByDirector(directorLastName: String): List<Movie> {
        return allMoviesCache.filter { movie ->
            movie.director?.let { extractLastName(it) == directorLastName } ?: false
        }
    }

    fun getMoviesByActor(actorLastName: String): List<Movie> {
        return allMoviesCache.filter { movie ->
            movie.actors?.any { extractLastName(it) == actorLastName } ?: false
        }
    }

    fun getMoviesByCountry(country: String): List<Movie> {
        return allMoviesCache.filter { movie ->
            movie.productionCountries.any { abbreviateCountryName(it) == country }
        }
    }

    fun getMoviesByDecade(decade: String): List<Movie> {
        return allMoviesCache.filter { movie ->
            movie.year?.let { year ->
                "${(year / 10) * 10}s" == decade
            } ?: false
        }
    }

    fun getMoviesByRating(rating: Int): List<Movie> {
        return allMoviesCache.filter { movie ->
            movie.tmdbRating?.toInt() == rating
        }
    }

    fun getMoviesByRuntimeRange(range: String): List<Movie> {
        return allMoviesCache.filter { movie ->
            movie.runtime?.let { runtime ->
                when (range) {
                    "0-60" -> runtime < 60
                    "60-90" -> runtime in 60 until 90
                    "90-120" -> runtime in 90 until 120
                    "120-150" -> runtime in 120 until 150
                    "150-180" -> runtime in 150 until 180
                    "180+" -> runtime >= 180
                    else -> false
                }
            } ?: false
        }
    }

    fun getMoviesByOriginalLanguage(language: String): List<Movie> {
        return allMoviesCache.filter { it.originalLanguage == language }
    }

    fun getMoviesByGenreCombination(combo: Pair<String, String>): List<Movie> {
        return allMoviesCache.filter { movie ->
            movie.genres.contains(combo.first) && movie.genres.contains(combo.second)
        }
    }
}