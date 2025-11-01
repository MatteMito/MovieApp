// file: app/src/main/java/com/example/movieapp/data/models/Movie.kt
// model movie completo

package com.example.movieapp.data.models

import com.google.gson.annotations.SerializedName

data class Movie(
    @SerializedName("id")
    val id: String,

    @SerializedName("title")
    val title: String,

    @SerializedName("year")
    val year: Int? = null,

    @SerializedName("source")
    val source: DataSource = DataSource.UNKNOWN,

    //tmdb enrichment
    @SerializedName("tmdb_id")
    val tmdbId: Int? = null,

    @SerializedName("is_enriched")
    val isEnriched: Boolean = false,

    @SerializedName("genres")
    val genres: List<String> = emptyList(),

    @SerializedName("director")
    val director: String? = null,

    @SerializedName("actors")
    val actors: List<String> = emptyList(),

    @SerializedName("overview")
    val overview: String? = null,

    @SerializedName("tagline")
    val tagline: String? = null,

    @SerializedName("runtime")
    val runtime: Int? = null,

    //poster e immagini
    @SerializedName("poster_url")
    val posterUrl: String? = null,

    @SerializedName("backdrop_url")
    val backdropUrl: String? = null,

    //rating e popolarita
    @SerializedName("tmdb_rating")
    val tmdbRating: Double? = null,

    @SerializedName("vote_count")
    val voteCount: Int? = null,

    @SerializedName("popularity")
    val popularity: Double? = null,

    //produzione
    @SerializedName("budget")
    val budget: Long? = null,

    @SerializedName("revenue")
    val revenue: Long? = null,

    @SerializedName("status")
    val status: String? = null,

    @SerializedName("production_companies")
    val productionCompanies: List<String> = emptyList(),

    @SerializedName("production_countries")
    val productionCountries: List<String> = emptyList(),

    //lingue
    @SerializedName("original_language")
    val originalLanguage: String? = null,

    @SerializedName("original_title")
    val originalTitle: String? = null,

    @SerializedName("spoken_languages")
    val spokenLanguages: List<String> = emptyList(),

    //metadata
    @SerializedName("adult")
    val adult: Boolean = false,

    @SerializedName("homepage")
    val homepage: String? = null,

    @SerializedName("imdb_id")
    val imdbId: String? = null,

    @SerializedName("keywords")
    val keywords: List<String> = emptyList(),

    @SerializedName("certification")
    val certification: String? = null,

    @SerializedName("trailer_url")
    val trailerUrl: String? = null,

    //user data (dati utente da user_movies)
    @SerializedName("user_rating")
    val userRating: Double? = null,

    @SerializedName("watched_date")
    val dateRated: String? = null,

    @SerializedName("is_watched")
    val isWatched: Boolean = false,

    @SerializedName("user_review")
    val userReview: String? = null,

    @SerializedName("is_favorite")
    val isFavorite: Boolean = false,

    //timestamp
    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
) {
    //helper per display
    fun getDisplayTitle(): String {
        return if (year != null) "$title ($year)" else title
    }

    fun hasFullData(): Boolean {
        return isEnriched && tmdbId != null
    }

    fun getGenresString(): String {
        return genres.joinToString(", ")
    }

    fun getActorsString(): String {
        return actors.take(3).joinToString(", ")
    }
}

//enum per source
enum class DataSource {
    IMDB,
    LETTERBOXD,
    TMDB,
    UNKNOWN;

    companion object {
        fun fromString(value: String): DataSource {
            return when (value.uppercase()) {
                "IMDB" -> IMDB
                "LETTERBOXD" -> LETTERBOXD
                "TMDB" -> TMDB
                else -> UNKNOWN
            }
        }
    }
}