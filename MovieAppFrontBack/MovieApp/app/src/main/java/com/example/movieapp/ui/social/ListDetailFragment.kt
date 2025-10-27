// FILE: app/src/main/java/com/example/movieapp/ui/social/ListDetailFragment.kt
// Fragment per dettaglio lista - COMPLETO

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.databinding.FragmentListDetailBinding
import com.example.movieapp.data.models.Movie

class ListDetailFragment : Fragment() {

    private val TAG = "ListDetailFragment"

    private var _binding: FragmentListDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListDetailViewModel
    private lateinit var moviesAdapter: ListMoviesAdapter

    // List ID passato come argomento
    private var listId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Recupera listId dagli arguments
        listId = arguments?.getString("listId") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListDetailBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[ListDetailViewModel::class.java]

        setupUI()
        setupObservers()

        // Carica dettagli lista
        if (listId.isNotEmpty()) {
            viewModel.loadList(listId)
        } else {
            Toast.makeText(requireContext(), "Errore: Lista non trovata", Toast.LENGTH_SHORT).show()
            requireActivity().onBackPressed()
        }

        return binding.root
    }

    private fun setupUI() {
        // Setup RecyclerView
        moviesAdapter = ListMoviesAdapter { movie ->
            confirmRemoveMovie(movie)
        }

        binding.recyclerMovies.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moviesAdapter
        }

        // Bottone aggiungi film
        binding.fabAddMovie.setOnClickListener {
            showAddMovieDialog()
        }

        // Bottone back
        binding.toolbar.setNavigationOnClickListener {
            requireActivity().onBackPressed()
        }
    }

    private fun setupObservers() {
        // Loading
        viewModel.loading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.isVisible = isLoading
        }

        // Lista dettagli
        viewModel.list.observe(viewLifecycleOwner) { list ->
            binding.toolbar.title = list.name
            binding.textDescription.text = list.description ?: "Nessuna descrizione"
            binding.textDescription.isVisible = !list.description.isNullOrEmpty()
        }

        // Film
        viewModel.movies.observe(viewLifecycleOwner) { movies ->
            moviesAdapter.submitList(movies)

            val isEmpty = movies.isEmpty()
            binding.recyclerMovies.isVisible = !isEmpty
            binding.textEmpty.isVisible = isEmpty

            // Aggiorna contatore
            binding.textMovieCount.text = "${movies.size} film"
        }

        // Errori
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun showAddMovieDialog() {
        val dialog = SearchMovieDialogFragment.newInstance { movie ->
            addMovieToList(movie)
        }
        dialog.show(childFragmentManager, "SearchMovieDialog")
    }

    private fun addMovieToList(movie: Movie) {
        viewModel.addMovieToList(
            listId = listId,
            movieId = movie.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Film aggiunto!", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun confirmRemoveMovie(movie: Movie) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Rimuovi Film")
            .setMessage("Vuoi rimuovere \"${movie.title}\" dalla lista?")
            .setPositiveButton("Rimuovi") { _, _ ->
                removeMovieFromList(movie)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun removeMovieFromList(movie: Movie) {
        viewModel.removeMovieFromList(
            listId = listId,
            movieId = movie.id,
            onSuccess = {
                Toast.makeText(requireContext(), "Film rimosso", Toast.LENGTH_SHORT).show()
            },
            onError = { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}