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

    private val _analyticsText = MutableLiveData<String>()
    val analyticsText: LiveData<String> = _analyticsText

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

    //nuovi grafici
    private val _decadesData = MutableLiveData<Map<String, Int>>()
    val decadesData: LiveData<Map<String, Int>> = _decadesData

    private val _runtimeDistributionData = MutableLiveData<Map<String, Int>>()
    val runtimeDistributionData: LiveData<Map<String, Int>> = _runtimeDistributionData

    private val _watchedByMonthData = MutableLiveData<Map<String, Int>>()
    val watchedByMonthData: LiveData<Map<String, Int>> = _watchedByMonthData

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

                    //genera testo analytics
                    val analyticsReport = generateAnalyticsReport(
                        movies, genres, years, directors, ratings, countries
                    )
                    _analyticsText.postValue(analyticsReport)
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
     * analizza generi - top 10
     */
    private fun analyzeGenres(movies: List<Movie>): Map<String, Int> {
        val genreCount = mutableMapOf<String, Int>()

        movies.forEach { movie ->
            movie.genres.forEach { genre ->
                if (genre.isNotBlank()) {
                    genreCount[genre] = genreCount.getOrDefault(genre, 0) + 1
                }
            }
        }

        return genreCount
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    /**
     * analizza anni - ultimi 20 anni con film
     */
    private fun analyzeYears(movies: List<Movie>): Map<Int, Int> {
        val yearCount = mutableMapOf<Int, Int>()

        movies.forEach { movie ->
            movie.year?.let { year ->
                yearCount[year] = yearCount.getOrDefault(year, 0) + 1
            }
        }

        return yearCount
            .entries
            .sortedByDescending { it.key }
            .take(20)
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    /**
     * analizza registi - top 10
     */
    private fun analyzeDirectors(movies: List<Movie>): Map<String, Int> {
        val directorCount = mutableMapOf<String, Int>()

        movies.forEach { movie ->
            movie.director?.let { director ->
                if (director.isNotBlank()) {
                    directorCount[director] = directorCount.getOrDefault(director, 0) + 1
                }
            }
        }

        return directorCount
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .associate { it.key to it.value }
    }

    /**
     * analizza distribuzione rating utente
     */
    private fun analyzeRatings(movies: List<Movie>): Map<String, Int> {
        val ratingBuckets = mutableMapOf<String, Int>()

        //inizializza buckets
        for (i in 1..10) {
            ratingBuckets["$i"] = 0
        }

        movies.forEach { movie ->
            movie.userRating?.let { rating ->
                val bucket = rating.toInt().coerceIn(1, 10).toString()
                ratingBuckets[bucket] = ratingBuckets.getOrDefault(bucket, 0) + 1
            }
        }

        //rimuovi buckets vuoti
        return ratingBuckets.filter { it.value > 0 }
    }

    /**
     * analizza paesi di produzione - top 15
     * estrae paesi dal campo genres o overview tmdb
     */
    private fun analyzeCountries(movies: List<Movie>): Map<String, Int> {
        val countryCount = mutableMapOf<String, Int>()

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

        movies.forEach { movie ->
            //cerca nei generi e overview
            val textToSearch = "${movie.genres.joinToString(" ")} ${movie.overview ?: ""}"

            var found = false
            knownCountries.forEach { country ->
                if (textToSearch.contains(country, ignoreCase = true)) {
                    val normalizedCountry = countryNormalization[country] ?: country
                    countryCount[normalizedCountry] = countryCount.getOrDefault(normalizedCountry, 0) + 1
                    found = true
                }
            }

            //default a USA se non trovato (maggior produzione mondiale)
            if (!found && movie.tmdbId != null) {
                countryCount["USA"] = countryCount.getOrDefault("USA", 0) + 1
            }
        }

        return countryCount
            .entries
            .sortedByDescending { it.value }
            .take(15)
            .associate { it.key to it.value }
    }

    /**
     * nuovo: analizza decadi - raggruppa per decennio
     */
    private fun analyzeDecades(movies: List<Movie>): Map<String, Int> {
        val decadeCount = mutableMapOf<String, Int>()

        movies.forEach { movie ->
            movie.year?.let { year ->
                val decade = (year / 10) * 10
                val label = "${decade}s"
                decadeCount[label] = decadeCount.getOrDefault(label, 0) + 1
            }
        }

        return decadeCount
            .entries
            .sortedBy { it.key }
            .associate { it.key to it.value }
    }

    /**
     * nuovo: distribuzione durata film
     */
    private fun analyzeRuntimeDistribution(movies: List<Movie>): Map<String, Int> {
        val runtimeBuckets = mutableMapOf<String, Int>()

        //buckets: <60, 60-90, 90-120, 120-150, 150-180, 180+
        val buckets = listOf(
            "< 60 min" to (0 until 60),
            "60-90 min" to (60 until 90),
            "90-120 min" to (90 until 120),
            "120-150 min" to (120 until 150),
            "150-180 min" to (150 until 180),
            "180+ min" to (180 until Int.MAX_VALUE)
        )

        buckets.forEach { (label, _) ->
            runtimeBuckets[label] = 0
        }

        movies.forEach { movie ->
            movie.runtime?.let { runtime ->
                val bucket = when (runtime) {
                    in 0 until 60 -> "< 60 min"
                    in 60 until 90 -> "60-90 min"
                    in 90 until 120 -> "90-120 min"
                    in 120 until 150 -> "120-150 min"
                    in 150 until 180 -> "150-180 min"
                    else -> "180+ min"
                }
                runtimeBuckets[bucket] = runtimeBuckets.getOrDefault(bucket, 0) + 1
            }
        }

        return runtimeBuckets.filter { it.value > 0 }
    }

    /**
     * nuovo: film visti per mese (ultimi 12 mesi)
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
            .takeLast(12) //ultimi 12 mesi
            .associate { it.key to it.value }
    }

    /**
     * genera report testuale analytics
     */
    private fun generateAnalyticsReport(
        movies: List<Movie>,
        genres: Map<String, Int>,
        years: Map<Int, Int>,
        directors: Map<String, Int>,
        ratings: Map<String, Int>,
        countries: Map<String, Int>
    ): String {
        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }
        val withRating = movies.count { it.userRating != null }

        val totalRuntime = movies.mapNotNull { it.runtime }.sum()
        val avgRuntime = if (movies.isNotEmpty()) totalRuntime / movies.size else 0

        val topGenre = genres.maxByOrNull { it.value }
        val topDirector = directors.maxByOrNull { it.value }
        val topCountry = countries.maxByOrNull { it.value }

        val avgRating = movies.mapNotNull { it.userRating }.average()

        return buildString {
            appendLine("analisi cinematografica")
            appendLine("=" .repeat(30))
            appendLine()

            appendLine("statistiche generali")
            appendLine("-".repeat(30))
            appendLine("film totali: ${movies.size}")
            appendLine("• visti: $watched")
            appendLine("• da vedere: $watchlist")
            appendLine("• con voto: $withRating")
            appendLine()

            appendLine("durata")
            appendLine("-".repeat(30))
            appendLine("totale: ${totalRuntime / 60}h ${totalRuntime % 60}m")
            appendLine("media: ${avgRuntime}min")
            appendLine()

            if (topGenre != null) {
                appendLine("genere preferito")
                appendLine("-".repeat(30))
                appendLine("${topGenre.key}: ${topGenre.value} film")
                appendLine()
            }

            if (topDirector != null) {
                appendLine("regista preferito")
                appendLine("-".repeat(30))
                appendLine("${topDirector.key}: ${topDirector.value} film")
                appendLine()
            }

            if (topCountry != null) {
                appendLine("paese principale")
                appendLine("-".repeat(30))
                appendLine("${topCountry.key}: ${topCountry.value} film")
                appendLine()
            }

            if (avgRating.isFinite() && avgRating > 0) {
                appendLine("valutazione media")
                appendLine("-".repeat(30))
                appendLine("${String.format("%.1f", avgRating)}/10")
                appendLine()
            }

            val oldestYear = years.keys.minOrNull()
            val newestYear = years.keys.maxOrNull()
            if (oldestYear != null && newestYear != null) {
                appendLine("range temporale")
                appendLine("-".repeat(30))
                appendLine("$oldestYear - $newestYear")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        movieRepository?.cleanup()
    }
}