// FILE: app/src/main/java/com/example/movieapp/data/models/MovieList.kt
// Model per liste personalizzate

package com.example.movieapp.data.models

import com.google.gson.annotations.SerializedName

/**
 * Lista personalizzata di film
 *
 * Supporta:
 * - Liste pubbliche/private
 * - Film multipli
 * - Followers
 * - Deadline opzionale
 */
data class MovieList(
    @SerializedName("id")
    val id: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("movie_ids")
    val movieIds: List<String> = emptyList(),

    @SerializedName("is_public")
    val isPublic: Boolean = false,

    @SerializedName("followers_count")
    val followersCount: Int = 0,

    @SerializedName("follower_ids")
    val followerIds: List<String> = emptyList(),

    @SerializedName("target_date")
    val targetDate: String? = null,

    @SerializedName("frequency")
    val frequency: String? = null,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String,

    // Campi transitori (non salvati nel DB)
    @SerializedName("movies")
    val movies: List<Movie>? = null,

    @SerializedName("movie_count")
    val movieCount: Int = movieIds.size
) {
    /**
     * Controlla se l'utente segue questa lista
     */
    fun isFollowedBy(userId: String): Boolean {
        return followerIds.contains(userId)
    }

    /**
     * Formatta la data deadline
     */
    fun getFormattedDeadline(): String? {
        if (targetDate == null) return null
        // TODO: Formatta la data
        return targetDate
    }

    /**
     * Descrizione breve per preview
     */
    fun getShortDescription(): String {
        if (description.isNullOrEmpty()) return "Nessuna descrizione"
        return if (description.length > 100) {
            description.substring(0, 97) + "..."
        } else {
            description
        }
    }
}

/**
 * DTO per creazione nuova lista
 */
data class CreateListRequest(
    @SerializedName("user_id")
    val userId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("is_public")
    val isPublic: Boolean = false,

    @SerializedName("movie_ids")
    val movieIds: List<String> = emptyList(),

    @SerializedName("target_date")
    val targetDate: String? = null,

    @SerializedName("frequency")
    val frequency: String? = null
)

/**
 * DTO per aggiornamento lista
 */
data class UpdateListRequest(
    @SerializedName("name")
    val name: String? = null,

    @SerializedName("description")
    val description: String? = null,

    @SerializedName("is_public")
    val isPublic: Boolean? = null,

    @SerializedName("movie_ids")
    val movieIds: List<String>? = null,

    @SerializedName("target_date")
    val targetDate: String? = null,

    @SerializedName("frequency")
    val frequency: String? = null
)

/**
 * DTO per aggiunta film a lista
 */
data class AddMovieToListRequest(
    @SerializedName("movie_id")
    val movieId: String
)

/**
 * Response con lista di MovieList
 */
data class MovieListsResponse(
    @SerializedName("lists")
    val lists: List<MovieList>
)