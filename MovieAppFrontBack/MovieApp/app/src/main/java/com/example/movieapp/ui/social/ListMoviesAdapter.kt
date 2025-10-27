// FILE: app/src/main/java/com/example/movieapp/ui/social/ListMoviesAdapter.kt
// Adapter per film in una lista - COMPLETO

package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movieapp.R
import com.example.movieapp.databinding.ItemListMovieBinding
import com.example.movieapp.data.models.Movie

class ListMoviesAdapter(
    private val onRemoveClick: (Movie) -> Unit
) : ListAdapter<Movie, ListMoviesAdapter.MovieViewHolder>(MovieDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val binding = ItemListMovieBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MovieViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MovieViewHolder(
        private val binding: ItemListMovieBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: Movie) {
            binding.apply {
                // Titolo
                textTitle.text = movie.title

                // Anno e regista
                val details = buildString {
                    if (movie.year != null) {
                        append(movie.year)
                    }
                    if (!movie.director.isNullOrEmpty()) {
                        if (isNotEmpty()) append(" • ")
                        append(movie.director)
                    }
                }
                textDetails.text = details
                textDetails.isVisible = details.isNotEmpty()

                // Generi
                val genres = movie.genres.joinToString(", ")
                textGenres.text = genres
                textGenres.isVisible = genres.isNotEmpty()

                // Poster
                if (!movie.posterUrl.isNullOrEmpty()) {
                    Glide.with(imagePoster.context)
                        .load(movie.posterUrl)
                        .placeholder(R.drawable.ic_home)
                        .error(R.drawable.ic_home)
                        .into(imagePoster)
                } else {
                    imagePoster.setImageResource(R.drawable.ic_home)
                }

                // Bottone rimuovi
                buttonRemove.setOnClickListener {
                    onRemoveClick(movie)
                }
            }
        }
    }

    private class MovieDiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem == newItem
        }
    }
}