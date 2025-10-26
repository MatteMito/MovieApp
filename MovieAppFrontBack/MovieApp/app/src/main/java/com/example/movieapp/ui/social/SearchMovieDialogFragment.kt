// FILE: app/src/main/java/com/example/movieapp/ui/social/SearchMovieDialogFragment.kt
// Dialog aggiornato con ricerca TMDB integrata

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.databinding.DialogSearchMovieBinding
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.repository.MovieRepository
import com.example.movieapp.data.network.ApiService
import android.util.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dialog per ricerca film
 *
 * 🔥 NUOVO: Supporta ricerca TMDB quando non trova risultati locali
 */
class SearchMovieDialogFragment : DialogFragment() {
    private val TAG = "SearchMovieDialog"

    private var _binding: DialogSearchMovieBinding? = null
    private val binding get() = _binding!!

    private lateinit var movieRepository: MovieRepository
    private lateinit var searchAdapter: SearchMovieAdapter

    private var onMovieSelected: ((Movie) -> Unit)? = null
    private var searchJob: Job? = null

    private var isSearchingTmdb = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog)
        movieRepository = MovieRepository.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSearchMovieBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchView()

        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchMovieAdapter { movie ->
            onMovieSelected?.invoke(movie)
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

    private fun performSearch(query: String) {
        Log.d(TAG, "🔍 Ricerca: $query")

        isSearchingTmdb = false
        binding.progressBar.visibility = View.VISIBLE
        binding.textNoResults.visibility = View.GONE
        binding.recyclerResults.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val results = movieRepository.getAllMovies().filter { movie ->
                    movie.title.contains(query, ignoreCase = true) ||
                            movie.director?.contains(query, ignoreCase = true) == true ||
                            movie.genres.any { it.contains(query, ignoreCase = true) }
                }

                binding.progressBar.visibility = View.GONE

                if (results.isNotEmpty()) {
                    showLocalResults(results)
                } else {
                    showTmdbSearchOption(query)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore ricerca locale", e)
                binding.progressBar.visibility = View.GONE
                showError("Errore durante la ricerca")
            }
        }
    }

    private fun showLocalResults(results: List<Movie>) {
        searchAdapter.submitList(results)
        binding.recyclerResults.visibility = View.VISIBLE
        binding.textNoResults.visibility = View.GONE
        binding.textResultCount.text = "${results.size} film trovati nel database"
        binding.textResultCount.visibility = View.VISIBLE

        Log.d(TAG, "✅ ${results.size} risultati locali trovati")
    }

    private fun showTmdbSearchOption(query: String) {
        binding.recyclerResults.visibility = View.GONE
        binding.textNoResults.visibility = View.VISIBLE
        binding.textResultCount.visibility = View.GONE

        binding.textNoResults.text = """
            Nessun film trovato nel database locale.
            
            🎬 Vuoi cercare "$query" su TMDB?
        """.trimIndent()

        binding.textNoResults.setOnClickListener {
            searchOnTmdb(query)
        }

        binding.textNoResults.isClickable = true
        binding.textNoResults.isFocusable = true
        binding.textNoResults.setBackgroundResource(android.R.drawable.list_selector_background)
        binding.textNoResults.setPadding(32, 32, 32, 32)

        Log.d(TAG, "💡 Nessun risultato locale → suggerito TMDB")
    }

    private fun searchOnTmdb(query: String) {
        Log.d(TAG, "🌐 Ricerca su TMDB: $query")

        isSearchingTmdb = true
        binding.progressBar.visibility = View.VISIBLE
        binding.textNoResults.visibility = View.GONE
        binding.textNoResults.setOnClickListener(null)
        binding.textNoResults.isClickable = false

        lifecycleScope.launch {
            try {
                val result = ApiService.searchMovieOnTmdb(query)

                binding.progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    val movie = result.getOrNull()
                    if (movie != null) {
                        showTmdbResult(movie)
                    } else {
                        showError("Nessun film trovato su TMDB")
                    }
                } else {
                    showError("Nessun film trovato su TMDB")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore ricerca TMDB", e)
                binding.progressBar.visibility = View.GONE
                showError("Errore durante la ricerca su TMDB")
            }
        }
    }

    private fun showTmdbResult(movie: Movie) {
        Log.d(TAG, "✅ Film trovato su TMDB: ${movie.title}")

        searchAdapter.submitList(listOf(movie))
        binding.recyclerResults.visibility = View.VISIBLE
        binding.textNoResults.visibility = View.GONE
        binding.textResultCount.text = "Film trovato su TMDB"
        binding.textResultCount.visibility = View.VISIBLE

        searchAdapter.setOnItemClickListener { selectedMovie ->
            addTmdbMovieToDatabase(selectedMovie)
        }
    }

    /**
     * ✅ FIX: Aggiunge film da TMDB al database
     */
    private fun addTmdbMovieToDatabase(movie: Movie) {
        Log.d(TAG, "📥 Aggiunta film da TMDB al database: ${movie.title}")

        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val userId = ApiService.getCurrentUserId() ?: throw Exception("User ID non disponibile")
                val tmdbId = movie.tmdbId ?: throw Exception("TMDB ID mancante")

                val result = ApiService.addMovieFromTmdb(
                    tmdbId = tmdbId,
                    userId = userId,
                    status = "watchlist"
                )

                binding.progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    val savedMovie = result.getOrNull()
                    if (savedMovie != null) {
                        Log.d(TAG, "✅ Film aggiunto al database: ${savedMovie.id}")

                        onMovieSelected?.invoke(savedMovie)
                        dismiss()
                    }
                } else {
                    showError("Errore durante l'aggiunta del film")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Errore aggiunta film", e)
                binding.progressBar.visibility = View.GONE
                showError("Errore: ${e.message}")
            }
        }
    }

    private fun showError(message: String) {
        binding.recyclerResults.visibility = View.GONE
        binding.textNoResults.visibility = View.VISIBLE
        binding.textNoResults.text = "⚠️ $message"
        binding.textNoResults.setOnClickListener(null)
        binding.textNoResults.isClickable = false
        binding.textResultCount.visibility = View.GONE
    }

    private fun clearResults() {
        searchAdapter.submitList(emptyList())
        binding.recyclerResults.visibility = View.GONE
        binding.textNoResults.visibility = View.GONE
        binding.textResultCount.visibility = View.GONE
    }

    fun setOnMovieSelectedListener(listener: (Movie) -> Unit) {
        onMovieSelected = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchJob?.cancel()
        _binding = null
    }
}