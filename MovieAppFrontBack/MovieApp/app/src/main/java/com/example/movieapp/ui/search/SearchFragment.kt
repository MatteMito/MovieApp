package com.example.movieapp.ui.search

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.databinding.FragmentSearchBinding
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.MovieRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.util.Log

class SearchFragment : Fragment() {
    private val TAG = "SearchFragment"

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: SearchResultAdapter
    private lateinit var movieRepository: MovieRepository

    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)

        movieRepository = MovieRepository.getInstance(requireContext())

        setupRecyclerView()
        setupSearchView()
        setupFilters()

        Log.d(TAG, "SearchFragment creato")

        return binding.root
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchResultAdapter { movie ->
            openMovieDetails(movie)
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
                    delay(500) // Attendi 500ms dopo l'ultimo carattere
                    newText?.let {
                        if (it.length >= 2) {
                            performSearch(it)
                        } else if (it.isEmpty()) {
                            clearResults()
                        }
                    }
                }
                return true
            }
        })
    }

    private fun setupFilters() {
        // Toggle filtri
        binding.buttonToggleFilters.setOnClickListener {
            val isVisible = binding.layoutFilters.visibility == View.VISIBLE
            binding.layoutFilters.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        // Ordinamento - ✅ FIXED: Usa setOnCheckedStateChangeListener invece di setOnCheckedChangeListener deprecato
        binding.chipGroupSort.setOnCheckedStateChangeListener { _, _ ->
            val currentQuery = binding.searchView.query.toString()
            if (currentQuery.isNotEmpty()) {
                performSearch(currentQuery)
            }
        }

        // Filtro watched
        binding.chipWatched.setOnClickListener {
            val currentQuery = binding.searchView.query.toString()
            if (currentQuery.isNotEmpty()) {
                performSearch(currentQuery)
            }
        }

        binding.chipNotWatched.setOnClickListener {
            val currentQuery = binding.searchView.query.toString()
            if (currentQuery.isNotEmpty()) {
                performSearch(currentQuery)
            }
        }

        // Reset filtri
        binding.buttonResetFilters.setOnClickListener {
            resetFilters()
        }

        // Applica filtri
        binding.buttonApplyFilters.setOnClickListener {
            binding.layoutFilters.visibility = View.GONE
            val currentQuery = binding.searchView.query.toString()
            if (currentQuery.isNotEmpty()) {
                performSearch(currentQuery)
            }
        }
    }

    private fun performSearch(query: String) {
        Log.d(TAG, "Ricerca: $query")

        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerResults.visibility = View.GONE
        binding.textNoResults.visibility = View.GONE

        lifecycleScope.launch {
            try {
                var results = movieRepository.getAllMovies().filter { movie ->
                    movie.title.contains(query, ignoreCase = true)
                }

                // Applica filtri
                results = applyFilters(results)

                // Applica ordinamento
                results = applySorting(results)

                if (results.isNotEmpty()) {
                    searchAdapter.submitList(results)
                    binding.recyclerResults.visibility = View.VISIBLE
                    binding.textNoResults.visibility = View.GONE
                    binding.textResultCount.text = "${results.size} film"
                } else {
                    binding.recyclerResults.visibility = View.GONE
                    binding.textNoResults.visibility = View.VISIBLE
                    binding.textResultCount.text = "0 film"
                }

                binding.progressBar.visibility = View.GONE

                Log.d(TAG, "Trovati ${results.size} film")

            } catch (e: Exception) {
                Log.e(TAG, "Errore ricerca", e)
                binding.progressBar.visibility = View.GONE
                binding.textNoResults.visibility = View.VISIBLE
            }
        }
    }

    private fun applyFilters(movies: List<Movie>): List<Movie> {
        var filtered = movies

        // Filtro genere
        val selectedGenreChip = binding.chipGroupGenre.checkedChipId
        if (selectedGenreChip != View.NO_ID) {
            val selectedGenre = view?.findViewById<com.google.android.material.chip.Chip>(selectedGenreChip)?.text.toString()
            filtered = filtered.filter { movie ->
                movie.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            }
        }

        // Filtro anno
        val yearText = binding.editYear.text.toString()
        if (yearText.isNotEmpty()) {
            val year = yearText.toIntOrNull()
            if (year != null) {
                filtered = filtered.filter { it.year == year }
            }
        }

        // Filtro regista
        val directorText = binding.editDirector.text.toString()
        if (directorText.isNotEmpty()) {
            filtered = filtered.filter { movie ->
                movie.director?.contains(directorText, ignoreCase = true) == true
            }
        }

        // Filtro rating minimo
        val minRatingText = binding.editMinRating.text.toString()
        if (minRatingText.isNotEmpty()) {
            val minRating = minRatingText.toDoubleOrNull()
            if (minRating != null) {
                filtered = filtered.filter { movie ->
                    (movie.userRating ?: 0.0) >= minRating ||
                            (movie.tmdbRating ?: 0.0) >= minRating
                }
            }
        }

        // Filtro watched
        when {
            binding.chipWatched.isChecked -> {
                filtered = filtered.filter { it.isWatched }
            }
            binding.chipNotWatched.isChecked -> {
                filtered = filtered.filter { !it.isWatched }
            }
        }

        return filtered
    }

    private fun applySorting(movies: List<Movie>): List<Movie> {
        return when (binding.chipGroupSort.checkedChipId) {
            binding.chipSortTitle.id -> {
                movies.sortedBy { it.title.lowercase() }
            }
            binding.chipSortYear.id -> {
                movies.sortedByDescending { it.year ?: 0 }
            }
            binding.chipSortRating.id -> {
                movies.sortedByDescending { it.userRating ?: it.tmdbRating ?: 0.0 }
            }
            binding.chipSortDate.id -> {
                movies.reversed() // Ultimi aggiunti per primi
            }
            else -> movies
        }
    }

    private fun resetFilters() {
        binding.chipGroupGenre.clearCheck()
        binding.chipGroupWatched.clearCheck()
        binding.editYear.text?.clear()
        binding.editDirector.text?.clear()
        binding.editMinRating.text?.clear()
        binding.chipGroupSort.check(binding.chipSortTitle.id)

        val currentQuery = binding.searchView.query.toString()
        if (currentQuery.isNotEmpty()) {
            performSearch(currentQuery)
        }
    }

    private fun clearResults() {
        searchAdapter.submitList(emptyList())
        binding.recyclerResults.visibility = View.GONE
        binding.textNoResults.visibility = View.GONE
        binding.textResultCount.text = "0 film"
    }

    private fun openMovieDetails(movie: Movie) {
        // TODO: Navigare ai dettagli del film
        Log.d(TAG, "Apertura dettagli: ${movie.title}")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}