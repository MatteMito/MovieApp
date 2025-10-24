// FILE: app/src/main/java/com/example/movieapp/ui/social/ListMoviesAdapter.kt
// Adapter per film all'interno di una lista

package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movieapp.R
import com.example.movieapp.databinding.ItemListMovieBinding
import com.example.movieapp.data.models.Movie

/**
 * Adapter per visualizzare film in una lista
 */
class ListMoviesAdapter(
    private val onRemoveClick: ((Movie) -> Unit)? = null
) : ListAdapter<Movie, ListMoviesAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemListMovieBinding.inflate(
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
        private val binding: ItemListMovieBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: Movie) {
            binding.apply {
                // Titolo
                textTitle.text = movie.title

                // Anno e Regista
                val details = buildString {
                    if (movie.year != null) append(movie.year)
                    if (movie.director != null) {
                        if (isNotEmpty()) append(" • ")
                        append(movie.director)
                    }
                }
                textDetails.text = details.ifEmpty { "Dettagli non disponibili" }

                // Generi
                if (movie.genres.isNotEmpty()) {
                    textGenres.text = movie.genres.take(3).joinToString(", ")
                    textGenres.visibility = View.VISIBLE
                } else {
                    textGenres.visibility = View.GONE
                }

                // Rating
                if (movie.userRating != null && movie.userRating > 0) {
                    textRating.text = "⭐ ${String.format("%.1f", movie.userRating)}"
                    textRating.visibility = View.VISIBLE
                } else if (movie.tmdbRating != null && movie.tmdbRating > 0) {
                    textRating.text = "⭐ ${String.format("%.1f", movie.tmdbRating)}"
                    textRating.visibility = View.VISIBLE
                } else {
                    textRating.visibility = View.GONE
                }

                // Poster
                if (!movie.posterUrl.isNullOrEmpty()) {
                    Glide.with(imagePoster)
                        .load(movie.posterUrl)
                        .placeholder(R.drawable.ic_home)
                        .error(R.drawable.ic_home)
                        .into(imagePoster)
                } else if (movie.tmdbId != null) {
                    val posterUrl = "https://image.tmdb.org/t/p/w185${movie.posterUrl ?: ""}"
                    Glide.with(imagePoster)
                        .load(posterUrl)
                        .placeholder(R.drawable.ic_home)
                        .error(R.drawable.ic_home)
                        .into(imagePoster)
                } else {
                    imagePoster.setImageResource(R.drawable.ic_home)
                }

                // Bottone rimuovi (solo se proprietario)
                if (onRemoveClick != null) {
                    buttonRemove.visibility = View.VISIBLE
                    buttonRemove.setOnClickListener {
                        onRemoveClick.invoke(movie)
                    }
                } else {
                    buttonRemove.visibility = View.GONE
                }
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem == newItem
        }
    }
}