package com.example.movieapp.data.models

import java.io.Serializable

/**
 * data class film con tutti i campi tmdb del backend
 */
data class Movie(
    val id: String,
    val title: String,
    val year: Int? = null,
    val director: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val overview: String? = null,
    val tagline: String? = null,
    val runtime: Int? = null,
    val userRating: Double? = null,
    val dateRated: String? = null,
    val isWatched: Boolean = false,
    val source: DataSource = DataSource.UNKNOWN,

    //campi tmdb completi
    val tmdbId: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val tmdbRating: Double? = null,
    val voteCount: Int? = null,
    val budget: Long? = null,
    val revenue: Long? = null,
    val status: String? = null,
    val originalLanguage: String? = null,
    val originalTitle: String? = null,
    val popularity: Double? = null,
    val adult: Boolean = false,
    val homepage: String? = null,
    val imdbId: String? = null,
    val productionCompanies: List<String> = emptyList(),
    val productionCountries: List<String> = emptyList(),
    val spokenLanguages: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val certification: String? = null,
    val trailerUrl: String? = null
) : Serializable {

    /**
     * verifica se film è arricchito con dati tmdb
     */
    fun isEnriched(): Boolean {
        return tmdbId != null && tmdbId > 0
    }

    /**
     * verifica se ha dati completi
     */
    fun hasCompleteData(): Boolean {
        return isEnriched() &&
                (genres.isNotEmpty() || director != null || overview != null)
    }

    /**
     * score completezza (0-100)
     */
    fun getCompletenessScore(): Int {
        var score = 0

        if (tmdbId != null) score += 20
        if (genres.isNotEmpty()) score += 15
        if (director != null) score += 15
        if (overview != null) score += 10
        if (posterUrl != null) score += 10
        if (cast.isNotEmpty()) score += 10
        if (runtime != null) score += 5
        if (tmdbRating != null) score += 5
        if (backdropUrl != null) score += 5
        if (certification != null) score += 5

        return score
    }

    /**
     * testo completezza per ui
     */
    fun getCompletenessText(): String {
        val score = getCompletenessScore()
        return when {
            score >= 80 -> "completo"
            score >= 50 -> "parziale"
            score >= 20 -> "base"
            else -> "minimo"
        }
    }

    /**
     * copy con dati tmdb
     */
    fun enrichWith(tmdbData: Map<String, Any?>): Movie {
        // ✅ FIXED: Usato @Suppress per cast inevitabili da Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        return copy(
            tmdbId = tmdbData["tmdbId"] as? Int ?: tmdbId,
            genres = (tmdbData["genres"] as? List<*>)?.filterIsInstance<String>() ?: genres,
            director = tmdbData["director"] as? String ?: director,
            cast = (tmdbData["cast"] as? List<*>)?.filterIsInstance<String>() ?: cast,
            overview = tmdbData["overview"] as? String ?: overview,
            tagline = tmdbData["tagline"] as? String ?: tagline,
            posterUrl = tmdbData["posterUrl"] as? String ?: posterUrl,
            backdropUrl = tmdbData["backdropUrl"] as? String ?: backdropUrl,
            tmdbRating = tmdbData["tmdbRating"] as? Double ?: tmdbRating,
            voteCount = tmdbData["voteCount"] as? Int ?: voteCount,
            runtime = tmdbData["runtime"] as? Int ?: runtime,
            budget = tmdbData["budget"] as? Long ?: budget,
            revenue = tmdbData["revenue"] as? Long ?: revenue,
            status = tmdbData["status"] as? String ?: status,
            originalLanguage = tmdbData["originalLanguage"] as? String ?: originalLanguage,
            originalTitle = tmdbData["originalTitle"] as? String ?: originalTitle,
            popularity = tmdbData["popularity"] as? Double ?: popularity,
            adult = tmdbData["adult"] as? Boolean ?: adult,
            homepage = tmdbData["homepage"] as? String ?: homepage,
            imdbId = tmdbData["imdbId"] as? String ?: imdbId,
            productionCompanies = (tmdbData["productionCompanies"] as? List<*>)?.filterIsInstance<String>() ?: productionCompanies,
            productionCountries = (tmdbData["productionCountries"] as? List<*>)?.filterIsInstance<String>() ?: productionCountries,
            spokenLanguages = (tmdbData["spokenLanguages"] as? List<*>)?.filterIsInstance<String>() ?: spokenLanguages,
            keywords = (tmdbData["keywords"] as? List<*>)?.filterIsInstance<String>() ?: keywords,
            certification = tmdbData["certification"] as? String ?: certification,
            trailerUrl = tmdbData["trailerUrl"] as? String ?: trailerUrl
        )
    }

    /**
     * descrizione breve per logging
     */
    fun toLogString(): String {
        return "$title (${year ?: "?"}) - tmdb: ${tmdbId ?: "none"} - enriched: ${isEnriched()}"
    }

    /**
     * info dettagliate
     */
    fun toDetailedString(): String {
        return buildString {
            appendLine("=== $title ===")
            appendLine("anno: ${year ?: "?"}")
            appendLine("tmdb id: ${tmdbId ?: "none"}")
            appendLine("arricchito: ${if (isEnriched()) "sì" else "no"}")
            appendLine("completezza: ${getCompletenessScore()}% (${getCompletenessText()})")
            appendLine("generi: ${genres.joinToString().ifEmpty { "nessuno" }}")
            appendLine("regista: ${director ?: "sconosciuto"}")
            appendLine("cast: ${cast.take(3).joinToString().ifEmpty { "nessuno" }}")
            appendLine("durata: ${runtime?.let { "${it}min" } ?: "?"}")
            appendLine("voto tmdb: ${tmdbRating?.let { String.format("%.1f", it) } ?: "?"}")
            appendLine("voto utente: ${userRating?.let { String.format("%.1f", it) } ?: "?"}")
            appendLine("visto: ${if (isWatched) "sì" else "no"}")
            appendLine("fonte: ${source.name}")
        }
    }
}

/**
 * fonte importazione film
 */
enum class DataSource {
    IMDB,
    LETTERBOXD,
    MANUAL,
    UNKNOWN
}