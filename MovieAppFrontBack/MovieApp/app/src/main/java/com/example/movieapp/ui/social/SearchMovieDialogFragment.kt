// FILE: app/src/main/java/com/example/movieapp/ui/social/SearchMovieDialogFragment.kt
// Dialog per ricerca e selezione film - REFACTORED

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.databinding.DialogSearchMovieBinding
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.MovieRepository

class SearchMovieDialogFragment : DialogFragment() {

    private var _binding: DialogSearchMovieBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: SearchMovieAdapter
    private lateinit var movieRepository: MovieRepository
    private var allMovies: List<Movie> = emptyList()

    // Callback per film selezionato
    private var onMovieSelected: ((Movie) -> Unit)? = null

    companion object {
        fun newInstance(onMovieSelected: (Movie) -> Unit): SearchMovieDialogFragment {
            return SearchMovieDialogFragment().apply {
                this.onMovieSelected = onMovieSelected
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSearchMovieBinding.inflate(inflater, container, false)

        // Ottieni repository
        movieRepository = MovieRepository.getInstance(requireContext())

        setupUI()
        loadMovies()

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // Dialog a schermo intero (quasi)
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupUI() {
        setupRecyclerView()
        setupSearchView()
    }

    private fun setupRecyclerView() {
        adapter = SearchMovieAdapter { movie ->
            onMovieSelected?.invoke(movie)  // ⬅️ Safe call con ?.
            dismiss()
        }

        binding.recyclerResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@SearchMovieDialogFragment.adapter
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                query?.let { filterMovies(it) }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterMovies(newText ?: "")
                return true
            }
        })

        // Auto-focus sulla SearchView
        binding.searchView.requestFocus()
    }

    private fun loadMovies() {
        binding.progressBar.isVisible = true

        // Osserva i film dal repository
        movieRepository.movies.observe(viewLifecycleOwner) { movies ->
            allMovies = movies
            binding.progressBar.isVisible = false

            if (movies.isEmpty()) {
                showNoResults()
            } else {
                // Mostra tutti i film inizialmente
                updateResults(movies)
            }
        }
    }

    private fun filterMovies(query: String) {
        if (query.isEmpty()) {
            updateResults(allMovies)
            return
        }

        val lowerQuery = query.lowercase()
        val filtered = allMovies.filter { movie ->
            movie.title.lowercase().contains(lowerQuery) ||
                    movie.director?.lowercase()?.contains(lowerQuery) == true ||
                    movie.genres.any { it.lowercase().contains(lowerQuery) }
        }

        updateResults(filtered)
    }

    private fun updateResults(movies: List<Movie>) {
        adapter.submitList(movies)

        val hasResults = movies.isNotEmpty()
        binding.recyclerResults.isVisible = hasResults
        binding.textNoResults.isVisible = !hasResults

        // Aggiorna contatore
        binding.textResultCount.text = "${movies.size} film trovati"
        binding.textResultCount.isVisible = hasResults
    }

    private fun showNoResults() {
        binding.recyclerResults.isVisible = false
        binding.textNoResults.isVisible = true
        binding.textNoResults.text = "Nessun film nel tuo database.\nImporta prima i tuoi film!"
        binding.textResultCount.isVisible = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}