package com.example.movieapp.ui.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    //file pickers
    private val imdbWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processImdbWatchedFile(uri) }
        }
    }

    private val imdbWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processImdbWatchlistFile(uri) }
        }
    }

    private val letterboxdWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processLetterboxdWatchedFile(uri) }
        }
    }

    private val letterboxdWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> processLetterboxdWatchlistFile(uri) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupUI()
        setupObservers()
        homeViewModel.initialize(requireContext())

        Log.d(TAG, "homefragment creato")
        return binding.root
    }

    private fun setupUI() {
        binding.buttonImportImdbWatched.setOnClickListener { showImdbWatchedHelp() }
        binding.buttonImportImdbWatchlist.setOnClickListener { showImdbWatchlistHelp() }
        binding.buttonImportLetterboxdWatched.setOnClickListener { showLetterboxdWatchedHelp() }
        binding.buttonImportLetterboxdWatchlist.setOnClickListener { showLetterboxdWatchlistHelp() }
        binding.buttonClearAll.setOnClickListener { showClearAllConfirmation() }

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "refresh manuale")
            homeViewModel.refreshFromBackend()
        }
    }

    private fun setupObservers() {
        //loading
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
        }

        //movies
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCard(movies)
        }

        //messages
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }

        //import in corso
        homeViewModel.isImporting.observe(viewLifecycleOwner) { isImporting ->
            binding.progressBarImport.isVisible = isImporting
            enableImportButtons(!isImporting)
        }

        //progress percentuale
        homeViewModel.importProgress.observe(viewLifecycleOwner) { progress ->
            binding.progressBarImport.progress = progress
        }
    }

    private fun updateStatsCard(movies: List<com.example.movieapp.data.models.Movie>) {
        val total = movies.size
        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }

        binding.textTotalMovies.text = "$total"
        binding.textWatchedMovies.text = "$watched"
        binding.textWatchlistMovies.text = "$watchlist"

        Log.d(TAG, "stats: $total film ($watched visti, $watchlist da vedere)")
    }

    private fun enableImportButtons(enabled: Boolean) {
        binding.buttonImportImdbWatched.isEnabled = enabled
        binding.buttonImportImdbWatchlist.isEnabled = enabled
        binding.buttonImportLetterboxdWatched.isEnabled = enabled
        binding.buttonImportLetterboxdWatchlist.isEnabled = enabled
        binding.buttonClearAll.isEnabled = enabled
    }

    //help dialogs
    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("import imdb watched")
            .setMessage("1. vai su imdb.com/list/ratings\n2. esporta csv\n3. selezionalo qui")
            .setPositiveButton("seleziona") { _, _ -> openImdbWatchedPicker() }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("import imdb watchlist")
            .setMessage("1. vai su imdb.com/list/watchlist\n2. esporta csv\n3. selezionalo qui")
            .setPositiveButton("seleziona") { _, _ -> openImdbWatchlistPicker() }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("import letterboxd watched")
            .setMessage("1. vai su letterboxd.com/settings/data\n2. export data\n3. estrai watched.csv\n4. selezionalo")
            .setPositiveButton("seleziona") { _, _ -> openLetterboxdWatchedPicker() }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("import letterboxd watchlist")
            .setMessage("1. vai su letterboxd.com/settings/data\n2. export data\n3. estrai watchlist.csv\n4. selezionalo")
            .setPositiveButton("seleziona") { _, _ -> openLetterboxdWatchlistPicker() }
            .setNegativeButton("annulla", null)
            .show()
    }

    //file pickers
    private fun openImdbWatchedPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        imdbWatchedPicker.launch(intent)
    }

    private fun openImdbWatchlistPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        imdbWatchlistPicker.launch(intent)
    }

    private fun openLetterboxdWatchedPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        letterboxdWatchedPicker.launch(intent)
    }

    private fun openLetterboxdWatchlistPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "text/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        letterboxdWatchlistPicker.launch(intent)
    }

    //file processing
    private fun processImdbWatchedFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchedCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore", e)
            Toast.makeText(requireContext(), "errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImdbWatchlistFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchlistCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore", e)
            Toast.makeText(requireContext(), "errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processLetterboxdWatchedFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchedCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore", e)
            Toast.makeText(requireContext(), "errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processLetterboxdWatchlistFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchlistCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore", e)
            Toast.makeText(requireContext(), "errore: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showClearAllConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("elimina tutti i film")
            .setMessage("sei sicuro?")
            .setPositiveButton("elimina") { _, _ -> homeViewModel.clearAllMovies() }
            .setNegativeButton("annulla", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}