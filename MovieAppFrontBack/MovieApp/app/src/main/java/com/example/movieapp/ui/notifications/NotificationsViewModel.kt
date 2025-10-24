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
 * viewmodel per grafici e analytics con 8 grafici totali
 */
class NotificationsViewModel : ViewModel() {
    private val TAG = "NotificationsViewModel"

    private var movieRepository: MovieRepository? = null

    private val _movies = MutableLiveData<List<Movie>>()
    val movies: LiveData<List<Movie>> = _movies

    private val _chartsReady = MutableLiveData<Boolean>()
    val chartsReady: LiveData<Boolean> = _chartsReady

    //dati per grafici
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
        Log.d(TAG, "notificationsviewmodel inizializzato")
    }

    fun initialize(context: Context) {
        movieRepository = MovieRepository.getInstance(context.applicationContext)

        movieRepository?.movies?.observeForever { movies ->
            _movies.value = movies ?: emptyList()

            if (movies.isNotEmpty()) {
                generateAllCharts(movies)
            }
        }

        loadMovies()
    }

    private fun loadMovies() {
        viewModelScope.launch {
            try {
                val repository = movieRepository ?: return@launch

                repository.loadMoviesFromDatabase()
                val movies = repository.getAllMovies()

                _movies.value = movies

                if (movies.isNotEmpty()) {
                    generateAllCharts(movies)
                }

            } catch (e: Exception) {
                Log.e(TAG, "errore caricamento film", e)
            }
        }
    }

    /**
     * genera tutti i grafici in background
     */
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

                    //nuovi grafici
                    val decades = analyzeDecades(movies)
                    val runtimeDist = analyzeRuntimeDistribution(movies)
                    val watchedByMonth = analyzeWatchedByMonth(movies)

                    //pubblica risultati
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
                Log.d(TAG, "grafici generati con successo")

            } catch (e: Exception) {
                Log.e(TAG, "errore generazione grafici", e)
                _chartsReady.value = false
            }
        }
    }

    /**
     * analizza generi - TUTTI i generi
     */
    private fun analyzeGenres(movies: List<Movie>): Map<String, Int> {
        val genreCount = mutableMapOf<String, Int>()
        genreMoviesMap.clear()

        movies.forEach { movie ->
            movie.genres.forEach { genre ->
                if (genre.isNotBlank()) {
                    genreCount[genre] = genreCount.getOrDefault(genre, 0) + 1

                    // Aggiungi film alla mappa
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

    /**
     * analizza anni - TUTTI gli anni
     */
    private fun analyzeYears(movies: List<Movie>): Map<Int, Int> {
        val yearCount = mutableMapOf<Int, Int>()
        yearMoviesMap.clear()

        movies.forEach { movie ->
            movie.year?.let { year ->
                yearCount[year] = yearCount.getOrDefault(year, 0) + 1

                // Aggiungi film alla mappa
                val currentList = yearMoviesMap[year] ?: emptyList()
                yearMoviesMap[year] = currentList + movie
            }
        }

        return yearCount
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    /**
     * analizza registi - TUTTI i registi
     */
    private fun analyzeDirectors(movies: List<Movie>): Map<String, Int> {
        val directorCount = mutableMapOf<String, Int>()
        directorMoviesMap.clear()

        movies.forEach { movie ->
            movie.director?.let { director ->
                if (director.isNotBlank()) {
                    directorCount[director] = directorCount.getOrDefault(director, 0) + 1

                    // Aggiungi film alla mappa
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

    /**
     * analizza distribuzione rating utente - TUTTE le stelle 0-10
     */
    private fun analyzeRatings(movies: List<Movie>): Map<String, Int> {
        val ratingBuckets = mutableMapOf<String, Int>()
        ratingMoviesMap.clear()

        //inizializza buckets da 0 a 10
        for (i in 0..10) {
            ratingBuckets["$i"] = 0
            ratingMoviesMap[i] = emptyList()
        }

        movies.forEach { movie ->
            movie.userRating?.let { rating ->
                val bucket = rating.toInt().coerceIn(0, 10)
                val key = bucket.toString()
                ratingBuckets[key] = ratingBuckets.getOrDefault(key, 0) + 1

                // Aggiungi film alla mappa
                val currentList = ratingMoviesMap[bucket] ?: emptyList()
                ratingMoviesMap[bucket] = currentList + movie
            }
        }

        return ratingBuckets
    }

    /**
     * analizza paesi di produzione - TUTTI i paesi principali
     */
    private fun analyzeCountries(movies: List<Movie>): Map<String, Int> {
        val countryCount = mutableMapOf<String, Int>()
        countryMoviesMap.clear()

        //lista paesi comuni nel cinema
        val knownCountries = setOf(
            "USA", "United States", "US",
            "UK", "United Kingdom", "Britain",
            "France", "Francia",
            "Italy", "Italia",
            "Germany", "Germania",
            "Spain", "Spagna",
            "Japan", "Giappone",
            "South Korea", "Corea del Sud",
            "India",
            "Canada",
            "Australia",
            "China", "Cina",
            "Mexico", "Messico",
            "Russia",
            "Brazil", "Brasile",
            "Argentina",
            "Sweden", "Svezia",
            "Norway", "Norvegia",
            "Denmark", "Danimarca",
            "Netherlands", "Olanda",
            "Belgium", "Belgio",
            "Poland", "Polonia",
            "Czech Republic", "Repubblica Ceca",
            "Austria",
            "Switzerland", "Svizzera",
            "Ireland", "Irlanda",
            "New Zealand", "Nuova Zelanda",
            "Hong Kong"
        )

        //normalizzazione nomi paesi
        val countryNormalization = mapOf(
            "United States" to "USA",
            "US" to "USA",
            "United Kingdom" to "UK",
            "Britain" to "UK",
            "Francia" to "France",
            "Italia" to "Italy",
            "Germania" to "Germany",
            "Spagna" to "Spain",
            "Giappone" to "Japan",
            "Corea del Sud" to "South Korea",
            "Cina" to "China",
            "Messico" to "Mexico",
            "Brasile" to "Brazil",
            "Svezia" to "Sweden",
            "Norvegia" to "Norway",
            "Danimarca" to "Denmark",
            "Olanda" to "Netherlands",
            "Belgio" to "Belgium",
            "Polonia" to "Poland",
            "Repubblica Ceca" to "Czech Republic",
            "Svizzera" to "Switzerland",
            "Irlanda" to "Ireland",
            "Nuova Zelanda" to "New Zealand"
        )

        // Inizializza tutti i paesi principali con 0
        val mainCountries = setOf("USA", "UK", "France", "Italy", "Germany", "Spain", "Japan",
            "South Korea", "India", "Canada", "Australia", "China", "Mexico", "Poland")
        mainCountries.forEach {
            countryCount[it] = 0
            countryMoviesMap[it] = emptyList()
        }

        movies.forEach { movie ->
            //cerca nei generi e overview
            val textToSearch = "${movie.genres.joinToString(" ")} ${movie.overview ?: ""}"

            var found = false
            knownCountries.forEach { country ->
                if (textToSearch.contains(country, ignoreCase = true)) {
                    val normalizedCountry = countryNormalization[country] ?: country
                    countryCount[normalizedCountry] = countryCount.getOrDefault(normalizedCountry, 0) + 1

                    // Aggiungi film alla mappa
                    val currentList = countryMoviesMap[normalizedCountry] ?: emptyList()
                    countryMoviesMap[normalizedCountry] = currentList + movie
                    found = true
                }
            }

            //default a USA se non trovato
            if (!found && movie.tmdbId != null) {
                countryCount["USA"] = countryCount.getOrDefault("USA", 0) + 1
                val currentList = countryMoviesMap["USA"] ?: emptyList()
                countryMoviesMap["USA"] = currentList + movie
            }
        }

        return countryCount
            .entries
            .sortedByDescending { it.value }
            .associate { it.key to it.value }
    }

    /**
     * analizza decadi - TUTTI i decenni
     */
    private fun analyzeDecades(movies: List<Movie>): Map<String, Int> {
        val decadeCount = mutableMapOf<String, Int>()
        decadeMoviesMap.clear()

        movies.forEach { movie ->
            movie.year?.let { year ->
                val decade = (year / 10) * 10
                val label = "${decade}s"
                decadeCount[label] = decadeCount.getOrDefault(label, 0) + 1

                // Aggiungi film alla mappa
                val currentList = decadeMoviesMap[label] ?: emptyList()
                decadeMoviesMap[label] = currentList + movie
            }
        }

        return decadeCount
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    /**
     * distribuzione durata film - TUTTE le fasce
     */
    private fun analyzeRuntimeDistribution(movies: List<Movie>): Map<String, Int> {
        val runtimeBuckets = mutableMapOf<String, Int>()
        runtimeMoviesMap.clear()

        //inizializza tutte le fasce
        val buckets = listOf(
            "< 60 min",
            "60-90 min",
            "90-120 min",
            "120-150 min",
            "150-180 min",
            "> 180 min"
        )

        buckets.forEach {
            runtimeBuckets[it] = 0
            runtimeMoviesMap[it] = emptyList()
        }

        movies.forEach { movie ->
            movie.runtime?.let { runtime ->
                val bucket = when (runtime) {
                    in 0 until 60 -> "< 60 min"
                    in 60 until 90 -> "60-90 min"
                    in 90 until 120 -> "90-120 min"
                    in 120 until 150 -> "120-150 min"
                    in 150 until 180 -> "150-180 min"
                    else -> "> 180 min"
                }
                runtimeBuckets[bucket] = runtimeBuckets.getOrDefault(bucket, 0) + 1

                // Aggiungi film alla mappa
                val currentList = runtimeMoviesMap[bucket] ?: emptyList()
                runtimeMoviesMap[bucket] = currentList + movie
            }
        }

        return runtimeBuckets
    }

    /**
     * film visti per mese - TUTTI i mesi disponibili
     */
    private fun analyzeWatchedByMonth(movies: List<Movie>): Map<String, Int> {
        val monthCount = mutableMapOf<String, Int>()

        val watchedMovies = movies.filter { it.isWatched && it.dateRated != null }

        watchedMovies.forEach { movie ->
            movie.dateRated?.let { date ->
                try {
                    //formato data: "2024-01" o simile
                    val monthLabel = date.substring(0, 7) //prendi YYYY-MM
                    monthCount[monthLabel] = monthCount.getOrDefault(monthLabel, 0) + 1
                } catch (e: Exception) {
                    Log.w(TAG, "formato data non valido: $date")
                }
            }
        }

        return monthCount
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    // Funzioni per ottenere i film di una categoria
    fun getMoviesByGenre(genre: String): List<Movie> = genreMoviesMap[genre] ?: emptyList()
    fun getMoviesByYear(year: Int): List<Movie> = yearMoviesMap[year] ?: emptyList()
    fun getMoviesByDirector(director: String): List<Movie> = directorMoviesMap[director] ?: emptyList()
    fun getMoviesByRating(rating: Int): List<Movie> = ratingMoviesMap[rating] ?: emptyList()
    fun getMoviesByCountry(country: String): List<Movie> = countryMoviesMap[country] ?: emptyList()
    fun getMoviesByDecade(decade: String): List<Movie> = decadeMoviesMap[decade] ?: emptyList()
    fun getMoviesByRuntime(runtime: String): List<Movie> = runtimeMoviesMap[runtime] ?: emptyList()
}