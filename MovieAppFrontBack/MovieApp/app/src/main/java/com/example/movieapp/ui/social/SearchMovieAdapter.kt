package com.example.movieapp.ui.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.movieapp.R
import com.example.movieapp.data.models.Movie

class SearchMovieAdapter(
    private var onItemClick: (Movie) -> Unit
) : ListAdapter<Movie, SearchMovieAdapter.ViewHolder>(DiffCallback()) {

    fun setOnItemClickListener(listener: (Movie) -> Unit) {
        onItemClick = listener
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_movie, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imagePoster: ImageView = itemView.findViewById(R.id.imagePoster)
        private val textMovieTitle: TextView = itemView.findViewById(R.id.textMovieTitle)
        private val textMovieYear: TextView = itemView.findViewById(R.id.textMovieYear)
        private val textMovieDirector: TextView = itemView.findViewById(R.id.textMovieDirector)
        private val textMovieGenres: TextView = itemView.findViewById(R.id.textMovieGenres)

        fun bind(movie: Movie) {
            textMovieTitle.text = movie.title
            textMovieYear.text = movie.year.toString()
            textMovieDirector.text = movie.director ?: "Regista sconosciuto"

            // Generi
            if (movie.genres.isNotEmpty()) {
                textMovieGenres.text = movie.genres.joinToString(", ")
            } else {
                textMovieGenres.text = "Generi non disponibili"
            }

            // Poster
            if (!movie.posterUrl.isNullOrEmpty()) {
                val posterUrl = if (movie.posterUrl!!.startsWith("http")) {
                    movie.posterUrl
                } else {
                    "https://image.tmdb.org/t/p/w500${movie.posterUrl}"
                }
                Glide.with(imagePoster)
                    .load(posterUrl)
                    .placeholder(R.drawable.ic_home)
                    .error(R.drawable.ic_home)
                    .into(imagePoster)
            } else {
                imagePoster.setImageResource(R.drawable.ic_home)
            }

            // Click
            itemView.setOnClickListener {
                onItemClick(movie)
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