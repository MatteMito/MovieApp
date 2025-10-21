import com.example.movieapp.data.models.Movie

/**
 * analytics helper
 */
data class BasicAnalytics(
    val totalMovies: Int = 0,
    val watchedCount: Int = 0,
    val watchlistCount: Int = 0,
    val enrichedCount: Int = 0,
    val topGenres: List<Pair<String, Int>> = emptyList(),
    val topDirectors: List<Pair<String, Int>> = emptyList(),
    val averageRating: Double? = null,
    val totalWatchTimeMinutes: Int = 0
)

/**
 * generatore analytics da lista film
 */
object AnalyticsGenerator {

    fun generateBasicAnalytics(movies: List<Movie>): BasicAnalytics {
        val watched = movies.filter { it.isWatched }
        val watchlist = movies.filter { !it.isWatched }
        val enriched = movies.filter { it.isEnriched() }

        val topGenres = movies
            .flatMap { it.genres }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)

        val topDirectors = movies
            .mapNotNull { it.director }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
            .take(10)

        val avgRating = watched
            .mapNotNull { it.userRating }
            .takeIf { it.isNotEmpty() }
            ?.average()

        val totalMinutes = watched
            .mapNotNull { it.runtime }
            .sum()

        return BasicAnalytics(
            totalMovies = movies.size,
            watchedCount = watched.size,
            watchlistCount = watchlist.size,
            enrichedCount = enriched.size,
            topGenres = topGenres,
            topDirectors = topDirectors,
            averageRating = avgRating,
            totalWatchTimeMinutes = totalMinutes
        )
    }

    fun generateDetailedReport(movies: List<Movie>): String {
        val analytics = generateBasicAnalytics(movies)

        return buildString {
            appendLine("=== report dettagliato ===")
            appendLine()
            appendLine("📊 statistiche generali")
            appendLine("film totali: ${analytics.totalMovies}")
            appendLine("visti: ${analytics.watchedCount}")
            appendLine("da vedere: ${analytics.watchlistCount}")
            appendLine("arricchiti: ${analytics.enrichedCount}")
            appendLine()

            if (analytics.totalWatchTimeMinutes > 0) {
                val hours = analytics.totalWatchTimeMinutes / 60
                val days = hours / 24
                appendLine("⏱️ tempo visione")
                appendLine("totale: ${hours}h (${days} giorni)")
                appendLine()
            }

            analytics.averageRating?.let { avg ->
                appendLine("⭐ valutazioni")
                appendLine("voto medio: ${String.format("%.1f", avg)}/10")
                appendLine()
            }

            if (analytics.topGenres.isNotEmpty()) {
                appendLine("🎭 top 5 generi")
                analytics.topGenres.take(5).forEachIndexed { index, (genre, count) ->
                    val percentage = (count * 100) / analytics.totalMovies
                    appendLine("${index + 1}. $genre: $count ($percentage%)")
                }
                appendLine()
            }

            if (analytics.topDirectors.isNotEmpty()) {
                appendLine("🎬 top 5 registi")
                analytics.topDirectors.take(5).forEachIndexed { index, (director, count) ->
                    appendLine("${index + 1}. $director: $count film")
                }
            }
        }
    }
}