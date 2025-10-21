package com.example.movieapp.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.movieapp.R
import com.example.movieapp.databinding.ItemSearchResultBinding
import com.example.movieapp.data.models.Movie

/**
 * Adapter per risultati ricerca con caricamento immagini Glide
 *
 * Features:
 * - Caricamento asincrono immagini
 * - Cache disco e memoria automatica
 * - Placeholder e error handling
 * - Performance ottimizzate
 */
class SearchResultAdapter(
    private val onItemClick: (Movie) -> Unit
) : ListAdapter<Movie, SearchResultAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchResultBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemSearchResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        // Request options per Glide
        private val glideOptions = RequestOptions()
            .centerCrop()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .placeholder(R.drawable.ic_home)
            .error(R.drawable.ic_warning)
            .fallback(R.drawable.ic_home)

        fun bind(movie: Movie) {
            binding.apply {
                // Titolo
                textTitle.text = movie.title

                // Anno e Regista
                val yearText = movie.year?.toString() ?: "Anno sconosciuto"
                val directorText = movie.director ?: "Regista sconosciuto"
                textYearDirector.text = "$yearText • $directorText"

                // Generi
                if (movie.genres.isNotEmpty()) {
                    textGenres.text = movie.genres.take(3).joinToString(", ")
                    textGenres.visibility = View.VISIBLE
                } else {
                    textGenres.visibility = View.GONE
                }

                // Rating - ✅ FIXED: Rimossi !! non necessari
                if (movie.userRating != null && movie.userRating > 0) {
                    textRating.text = String.format("%.1f", movie.userRating)
                    textRating.visibility = View.VISIBLE
                } else if (movie.tmdbRating != null && movie.tmdbRating > 0) {
                    textRating.text = String.format("%.1f", movie.tmdbRating)
                    textRating.visibility = View.VISIBLE
                } else {
                    textRating.visibility = View.GONE
                }

                // Badge visto
                if (movie.isWatched) {
                    badgeWatched.visibility = View.VISIBLE
                } else {
                    badgeWatched.visibility = View.GONE
                }

                // POSTER CON GLIDE
                loadPosterImage(movie)

                // Click
                root.setOnClickListener { onItemClick(movie) }
            }
        }

        /**
         * Carica poster con Glide
         *
         * Priorità:
         * 1. posterUrl (TMDB full URL)
         * 2. Costruisce URL da tmdbId se presente
         * 3. Placeholder se nessuna immagine disponibile
         */
        private fun loadPosterImage(movie: Movie) {
            // ✅ FIXED: Rimosso !! non necessario
            val posterUrl = when {
                // URL completo da TMDB
                !movie.posterUrl.isNullOrEmpty() -> {
                    movie.posterUrl
                }
                // Costruisci URL da tmdbId
                movie.tmdbId != null && movie.tmdbId > 0 -> {
                    // TMDB usa formato: https://image.tmdb.org/t/p/w342/{poster_path}
                    // Ma poster_path è già salvato in posterUrl dopo enrichment
                    // Quindi se arriviamo qui, significa che posterUrl è vuoto
                    null
                }
                // Nessuna immagine disponibile
                else -> null
            }

            // Carica con Glide
            Glide.with(binding.imagePoster.context)
                .load(posterUrl)
                .apply(glideOptions)
                .into(binding.imagePoster)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem == newItem
        }
    }
}