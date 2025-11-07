//file: app/src/main/java/com/example/movieapp/data/models/MovieList.kt
//data class per lista con username e isfollowing

package com.example.movieapp.data.models

import com.example.movieapp.data.network.ApiService
import com.google.gson.annotations.SerializedName

data class MovieList(
    @SerializedName("id")
    val id: String,

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

    @SerializedName("movies")
    val movies: List<Movie> = emptyList(),

    @SerializedName("followers_count")
    val followersCount: Int = 0,

    @SerializedName("follower_ids")
    val followerIds: List<String> = emptyList(),

    @SerializedName("username")
    val username: String? = null,

    @SerializedName("created_at")
    val createdAt: String? = null,

    @SerializedName("updated_at")
    val updatedAt: String? = null
) {
    //computed property: verifica se l'utente corrente segue questa lista
    val isFollowing: Boolean
        get() {
            val currentUserId = ApiService.getCurrentUserId()
            return currentUserId != null && followerIds.contains(currentUserId)
        }
}