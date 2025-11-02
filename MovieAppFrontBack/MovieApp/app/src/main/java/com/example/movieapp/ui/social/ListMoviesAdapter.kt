//file: app/src/main/java/com/example/movieapp/ui/social/ListMoviesAdapter.kt
//adapter per film in una lista con bottone rimuovi

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
                //titolo
                textTitle.text = movie.title

                //anno e regista
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

                //generi
                val genres = movie.genres.joinToString(", ")
                textGenres.text = genres
                textGenres.isVisible = genres.isNotEmpty()

                //poster
                if (movie.posterUrl != null) {
                    Glide.with(root.context)
                        .load(movie.posterUrl)
                        .placeholder(R.drawable.ic_home)
                        .error(R.drawable.ic_home)
                        .into(ivMoviePoster)
                } else {
                    ivMoviePoster.setImageResource(R.drawable.ic_home)
                }

                //bottone rimuovi
                btnRemove.setOnClickListener {
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