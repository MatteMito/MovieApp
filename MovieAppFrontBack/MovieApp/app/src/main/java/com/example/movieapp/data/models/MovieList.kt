package com.example.movieapp.data.models

import com.google.gson.annotations.SerializedName

// model per liste personalizzate di film con supporto notifiche intelligenti
data class MovieList(
    @SerializedName("id")
    val id: String,

    @SerializedName("user_id")
    val userId: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("description")
    val description: String?,

    @SerializedName("movie_ids")
    val movieIds: List<String> = emptyList(),

    @SerializedName("movies")
    val movies: List<Movie> = emptyList(),

    @SerializedName("is_public")
    val isPublic: Boolean = false,

    @SerializedName("follower_ids")
    val followerIds: List<String> = emptyList(),

    @SerializedName("followers_count")
    val followersCount: Int = 0,

    @SerializedName("username")
    val username: String? = null,

    // campo per sapere se l'utente corrente segue questa lista
    @SerializedName("isFollowing")
    val isFollowing: Boolean = false,

    // campi notifiche intelligenti
    @SerializedName("target_date")
    val targetDate: String? = null,

    @SerializedName("frequency")
    val frequency: String? = null, // daily, weekly, monthly

    @SerializedName("notifications_enabled")
    val notificationsEnabled: Boolean = false,

    @SerializedName("last_notification_sent")
    val lastNotificationSent: String? = null,

    @SerializedName("created_at")
    val createdAt: String,

    @SerializedName("updated_at")
    val updatedAt: String
) {
    // helper per formattare frequenza in italiano
    fun getFrequencyDisplay(): String? {
        return when (frequency) {
            "daily" -> "Giornaliera"
            "weekly" -> "Settimanale"
            "monthly" -> "Mensile"
            else -> null
        }
    }

    // helper per verificare se la pianificazione è attiva
    fun hasScheduling(): Boolean {
        return targetDate != null && frequency != null
    }
}