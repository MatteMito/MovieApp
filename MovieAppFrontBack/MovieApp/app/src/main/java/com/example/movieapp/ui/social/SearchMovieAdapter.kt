package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movieapp.R
import com.example.movieapp.data.models.Movie
import com.example.movieapp.databinding.ItemSearchMovieBinding

// adapter per risultati ricerca film, layout compatto per dialog aggiunta
class SearchMovieAdapter(
    private val onMovieClick: (Movie) -> Unit
) : ListAdapter<Movie, SearchMovieAdapter.ViewHolder>(MovieDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSearchMovieBinding.inflate(
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
        private val binding: ItemSearchMovieBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(movie: Movie) {
            binding.apply {
                // titolo film
                tvMovieTitle.text = movie.title
                // anno o n/a se non disponibile
                tvMovieYear.text = movie.year?.toString() ?: "N/A"

                // carica poster con glide, fallback su icona default
                if (movie.posterUrl != null) {
                    Glide.with(root.context)
                        .load(movie.posterUrl)
                        .placeholder(R.drawable.ic_home)
                        .error(R.drawable.ic_home)
                        .into(ivMoviePoster)
                } else {
                    ivMoviePoster.setImageResource(R.drawable.ic_home)
                }

                // click sull'intero item per aggiungere film
                root.setOnClickListener {
                    onMovieClick(movie)
                }
            }
        }
    }

    // diffutil per ottimizzare aggiornamenti recyclerview
    private class MovieDiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem == newItem
        }
    }
}