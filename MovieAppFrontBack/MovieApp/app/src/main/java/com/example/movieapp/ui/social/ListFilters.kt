// FILE: app/src/main/java/com/example/movieapp/ui/social/ListFilters.kt
// Data class per gestire i filtri delle liste

package com.example.movieapp.ui.social

import com.example.movieapp.data.models.MovieList

/**
 * Enum per ordinamento liste
 */
enum class SortOrder {
    RECENT,      // Più recenti
    NAME_ASC,    // Nome A-Z
    MOVIE_COUNT, // Più film
    POPULAR      // Più popolari (followers)
}

/**
 * Data class per filtri liste
 */
data class ListFilters(
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.RECENT,
    val minMovieCount: Int = 0
) {
    /**
     * Applica i filtri ad una lista di MovieList
     */
    fun applyTo(lists: List<MovieList>): List<MovieList> {
        var filtered = lists

        // 🔍 Filtro per ricerca nome
        if (searchQuery.isNotEmpty()) {
            filtered = filtered.filter { list ->
                list.name.contains(searchQuery, ignoreCase = true) ||
                        list.description?.contains(searchQuery, ignoreCase = true) == true
            }
        }

        // 📊 Filtro per numero minimo film
        if (minMovieCount > 0) {
            filtered = filtered.filter { list ->
                list.movieCount >= minMovieCount
            }
        }

        // 📈 Ordinamento
        filtered = when (sortOrder) {
            SortOrder.RECENT -> filtered.sortedByDescending { it.createdAt }
            SortOrder.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortOrder.MOVIE_COUNT -> filtered.sortedByDescending { it.movieCount }
            SortOrder.POPULAR -> filtered.sortedByDescending { it.followersCount }
        }

        return filtered
    }

    /**
     * Verifica se ci sono filtri attivi (diversi dai default)
     */
    fun hasActiveFilters(): Boolean {
        return searchQuery.isNotEmpty() ||
                sortOrder != SortOrder.RECENT ||
                minMovieCount > 0
    }

    /**
     * Ottieni descrizione filtri attivi per UI
     */
    fun getActiveFiltersDescription(): List<String> {
        val descriptions = mutableListOf<String>()

        if (searchQuery.isNotEmpty()) {
            descriptions.add("Cerca: $searchQuery")
        }

        if (minMovieCount > 0) {
            descriptions.add("Min. $minMovieCount film")
        }

        val sortDescription = when (sortOrder) {
            SortOrder.RECENT -> "Più recenti"
            SortOrder.NAME_ASC -> "Nome A-Z"
            SortOrder.MOVIE_COUNT -> "Più film"
            SortOrder.POPULAR -> "Più popolari"
        }
        descriptions.add(sortDescription)

        return descriptions
    }

    companion object {
        /**
         * Filtri di default
         */
        fun default() = ListFilters()
    }
}