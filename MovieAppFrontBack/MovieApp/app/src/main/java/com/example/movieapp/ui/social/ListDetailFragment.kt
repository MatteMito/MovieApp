package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentListDetailBinding
import com.example.movieapp.data.models.Movie
import com.example.movieapp.data.network.ApiService
import android.util.Log
import kotlinx.coroutines.launch

class ListDetailFragment : Fragment() {
    private val TAG = "ListDetailFragment"

    private var _binding: FragmentListDetailBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ListDetailViewModel
    private lateinit var moviesAdapter: ListMoviesAdapter

    private var listId: String? = null
    private var listName: String? = null
    private var isOwner: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let {
            listId = it.getString("list_id")
            listName = it.getString("list_name")
            isOwner = it.getBoolean("is_owner", false)
        }
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

        listId?.let { viewModel.loadListDetails(it) }

        Log.d(TAG, "ListDetailFragment creato per lista: $listId")
        return binding.root
    }

    private fun setupUI() {
        binding.textListName.text = listName ?: "Lista"

        moviesAdapter = ListMoviesAdapter(
            onRemoveClick = if (isOwner) { movie -> removeMovie(movie) } else null
        )

        binding.recyclerMovies.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = moviesAdapter
        }

        if (isOwner) {
            binding.fabAddMovie.visibility = View.VISIBLE
            binding.fabAddMovie.setOnClickListener {
                openSearchMovieDialog()
            }
        } else {
            binding.fabAddMovie.visibility = View.GONE
        }

        binding.buttonBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.swipeRefresh.setOnRefreshListener {
            listId?.let { viewModel.loadListDetails(it) }
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        viewModel.listDetails.observe(viewLifecycleOwner) { list ->
            list?.let {
                binding.textListName.text = it.name
                binding.textDescription.text = it.description ?: "Nessuna descrizione"
                binding.textMovieCount.text = "${it.movieCount} film"

                val movies = it.movies ?: emptyList()
                moviesAdapter.submitList(movies)

                if (movies.isEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.recyclerMovies.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.recyclerMovies.visibility = View.VISIBLE
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMsg ->
            if (errorMsg != null) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSearchMovieDialog() {
        val dialog = SearchMovieDialogFragment()
        dialog.setOnMovieSelectedListener { movie ->
            addMovieToList(movie)
        }
        dialog.show(childFragmentManager, "SearchMovieDialog")
    }

    private fun addMovieToList(movie: Movie) {
        lifecycleScope.launch {
            try {
                val result = ApiService.addMovieToList(listId!!, movie.id)

                if (result.isSuccess) {
                    val updatedList = result.getOrNull()

                    // aggiorna subito il contatore senza reload completo
                    updatedList?.let {
                        binding.textMovieCount.text = "${it.movieCount} film"
                    }

                    Toast.makeText(requireContext(), "Film aggiunto!", Toast.LENGTH_SHORT).show()

                    // ricarica la lista completa per mostrare il film
                    viewModel.loadListDetails(listId!!)
                } else {
                    Toast.makeText(requireContext(), "Errore aggiunta film", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removeMovie(movie: Movie) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Rimuovi Film")
            .setMessage("Rimuovere \"${movie.title}\" dalla lista?")
            .setPositiveButton("Rimuovi") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val result = ApiService.removeMovieFromList(listId!!, movie.id)

                        if (result.isSuccess) {
                            val updatedList = result.getOrNull()

                            // 🔥 Aggiorna subito il contatore
                            updatedList?.let {
                                binding.textMovieCount.text = "${it.movieCount} film"
                            }

                            Toast.makeText(requireContext(), "Film rimosso", Toast.LENGTH_SHORT).show()

                            // Ricarica la lista per aggiornare l'UI
                            viewModel.loadListDetails(listId!!)
                        } else {
                            Toast.makeText(requireContext(), "Errore rimozione film", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "Errore: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}