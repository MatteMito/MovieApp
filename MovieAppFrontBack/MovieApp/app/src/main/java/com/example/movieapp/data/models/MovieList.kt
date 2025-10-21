package com.example.movieapp.data.models

import java.io.Serializable
import java.util.Date
import java.util.UUID

/**
 * Data class per liste personalizzate di film
 */
data class MovieList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String? = null,
    val movieIds: List<String> = emptyList(),
    val createdBy: String = "",
    val isPublic: Boolean = false,
    val targetDate: Date? = null,
    val frequency: ListFrequency? = null,
    val followerCount: Int = 0,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),

    // ✅ AGGIUNTI: campi necessari per compatibilità con API e UI
    val movieCount: Int = movieIds.size,  // Calcolato automaticamente
    val ownerId: String? = createdBy,     // Alias per compatibilità
    val ownerName: String? = null,        // Nome del proprietario (dalle API)
    val isFollowing: Boolean = false      // Se l'utente corrente segue questa lista
) : Serializable

enum class ListFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    CUSTOM
}

data class CreateListRequest(
    val name: String,
    val description: String? = null,
    val movieIds: List<String> = emptyList(),
    val isPublic: Boolean = false,
    val targetDate: String? = null,
    val frequency: String? = null
)