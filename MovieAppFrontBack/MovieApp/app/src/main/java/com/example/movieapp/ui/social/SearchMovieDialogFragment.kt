// FILE: app/src/main/java/com/example/movieapp/ui/social/SearchMovieDialogFragment.kt
// Dialog per cercare film da aggiungere alla lista

package com.example.movieapp.ui.social

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.movieapp.databinding.DialogSearchMovieBinding
import com.example.movieapp.databinding.ItemSearchMovieBinding
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.MovieRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

/**
 * Dialog per cercare film nel DB locale
 */
class SearchMovieDialogFragment : DialogFragment() {
    private val TAG = "SearchMovieDialog"

    private var _binding: DialogSearchMovieBinding? = null
    private val binding get() = _binding!!

    private lateinit var movieRepository: MovieRepository
    private lateinit var searchAdapter: SearchMovieAdapter

    private var onMovieSelectedListener: ((Movie) -> Unit)? = null
    private var searchJob: Job? = null

    fun setOnMovieSelectedListener(listener: (Movie) -> Unit) {
        onMovieSelectedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogSearchMovieBinding.inflate(LayoutInflater.from(requireContext()))

        movieRepository = MovieRepository.getInstance(requireContext())

        setupRecyclerView()
        setupSearchView()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle("Cerca Film")
            .setView(binding.root)
            .setNegativeButton("Annulla", null)
            .create()
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchMovieAdapter { movie ->
            onMovieSelectedListener?.invoke(movie)
            dismiss()
        }

        binding.recyclerResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = searchAdapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { performSearch(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Debounce search
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    delay(500)
                    newText?.let {
                        if (it.length >= 2) {
                            performSearch(it)
                        } else {
                            clearResults()
                        }
                    }
                }
                return true
            }
        })
    }

    /**
     * Cerca film nel database locale
     */
    private fun performSearch(query: String) {
        Log.d(TAG, "🔍 Ricerca: $query")

        binding.progressBar.visibility = View.VISIBLE
        binding.textNoResults.visibility = View.GONE

        lifecycleScope.launch {
            try {
                // Cerca nel DB locale
                val results = movieRepository.getAllMovies().filter { movie ->
                    movie.title.contains(query, ignoreCase = true) ||
                            movie.director?.contains(query, ignoreCase = true) == true ||
                            movie.genres.any { it.contains(query, ignoreCase = true) }
                }

                if (results.isNotEmpty()) {
                    searchAdapter.submitList(results)
                    binding.recyclerResults.visibility = View.VISIBLE
                    binding.textNoResults.visibility = View.GONE
                    binding.textResultCount.text = "${results.size} film trovati"

                    Log.d(TAG, "✅ ${results.size} risultati trovati")
                } else {
                    // Nessun risultato nel DB locale
                    binding.recyclerResults.visibility = View.GONE
                    binding.textNoResults.visibility = View.VISIBLE
                    binding.textNoResults.text = "Nessun film trovato nel database.\n\n💡 Suggerimento: Importa più film dalla Home per avere più risultati!"
                    binding.textResultCount.text = "0 film trovati"

                    Log.d(TAG, "⚠️ Nessun risultato per: $query")

                    // TODO: Implementa ricerca TMDB come fallback
                    // searchTMDB(query)
                }

                binding.progressBar.visibility = View.GONE

            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore ricerca", e)
                binding.progressBar.visibility = View.GONE
                binding.textNoResults.visibility = View.VISIBLE
                binding.textNoResults.text = "Errore durante la ricerca"
            }
        }
    }

    private fun clearResults() {
        searchAdapter.submitList(emptyList())
        binding.recyclerResults.visibility = View.GONE
        binding.textNoResults.visibility = View.GONE
        binding.textResultCount.text = "0 film"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}

/**
 * Adapter semplificato per risultati ricerca nel dialog
 */
class SearchMovieAdapter(
    private val onMovieClick: (Movie) -> Unit
) : ListAdapter<Movie, SearchMovieAdapter.ViewHolder>(MovieDiffCallback()) {

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
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
                textTitle.text = movie.title

                val details = buildString {
                    if (movie.year != null) append(movie.year)
                    if (movie.director != null) {
                        if (isNotEmpty()) append(" • ")
                        append(movie.director)
                    }
                }
                textDetails.text = details

                if (movie.genres.isNotEmpty()) {
                    textGenres.text = movie.genres.take(2).joinToString(", ")
                    textGenres.visibility = View.VISIBLE
                } else {
                    textGenres.visibility = View.GONE
                }

                root.setOnClickListener {
                    onMovieClick(movie)
                }
            }
        }
    }

    class MovieDiffCallback : DiffUtil.ItemCallback<Movie>() {
        override fun areItemsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Movie, newItem: Movie): Boolean {
            return oldItem == newItem
        }
    }
}