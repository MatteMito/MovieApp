package com.example.movieapp.ui.home

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * homefragment con import in background e notifiche
 */
class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    private enum class ImportType {
        IMDB_WATCHED,
        IMDB_WATCHLIST,
        LETTERBOXD_WATCHED,
        LETTERBOXD_WATCHLIST
    }

    //permission launcher per notifiche
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d(TAG, "notifiche permesse")
            Toast.makeText(
                requireContext(),
                "l'import continuera in background",
                Toast.LENGTH_LONG
            ).show()
            pendingImport?.let { (uri, type) ->
                processFile(uri, type)
                pendingImport = null
            }
        }
    }

    private var pendingImport: Pair<Uri, ImportType>? = null

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
        binding.buttonImportImdbWatched.setOnClickListener {
            Log.d(TAG, "click: import imdb watched")
            showImdbWatchedHelp()
        }
        binding.buttonImportImdbWatchlist.setOnClickListener {
            Log.d(TAG, "click: import imdb watchlist")
            showImdbWatchlistHelp()
        }
        binding.buttonImportLetterboxdWatched.setOnClickListener {
            Log.d(TAG, "click: import letterboxd watched")
            showLetterboxdWatchedHelp()
        }
        binding.buttonImportLetterboxdWatchlist.setOnClickListener {
            Log.d(TAG, "click: import letterboxd watchlist")
            showLetterboxdWatchlistHelp()
        }
        binding.buttonClearAll.setOnClickListener {
            Log.d(TAG, "click: clear all")
            showClearAllConfirmation()
        }

        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "swipe refresh")
            homeViewModel.refreshFromBackend()
        }
    }

    private fun setupObservers() {
        //loading generale
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefresh.isRefreshing = isLoading
            Log.d(TAG, "loading: $isLoading")
        }

        //movies - aggiorna card statistiche
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCard(movies)
            Log.d(TAG, "movies aggiornati: ${movies.size} film")
        }

        //messages
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                Log.d(TAG, "message: $message")
            }
        }

        //import in corso
        homeViewModel.isImporting.observe(viewLifecycleOwner) { isImporting ->
            binding.progressBarImport.isVisible = isImporting
            enableImportButtons(!isImporting)

            Log.d(TAG, "isImporting: $isImporting")

            if (!isImporting) {
                binding.progressBarImport.progress = 0
            }
        }

        //progress percentuale
        homeViewModel.importProgress.observe(viewLifecycleOwner) { progress ->
            if (progress > 0) {
                binding.progressBarImport.progress = progress
                Log.d(TAG, "import progress: $progress%")
            }
        }
    }

    /**
     * aggiorna card statistiche
     */
    private fun updateStatsCard(movies: List<com.example.movieapp.data.models.Movie>) {
        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }

        binding.textTotalMovies.text = movies.size.toString()
        binding.textWatchedMovies.text = watched.toString()
        binding.textWatchlistMovies.text = watchlist.toString()

        //calcola e aggiorna la percentuale di completamento (film visti)
        val percentage = if (movies.isNotEmpty()) {
            (watched.toFloat() / movies.size * 100).toInt()
        } else {
            0
        }
        binding.progressStats.progress = percentage
        binding.textProgressPercentage.text = getString(R.string.stats_completed_percentage, percentage)

        Log.d(TAG, "stats aggiornate: total=${movies.size}, watched=$watched, watchlist=$watchlist, %=$percentage")
    }

    /**
     * abilita/disabilita pulsanti import
     */
    private fun enableImportButtons(enabled: Boolean) {
        binding.buttonImportImdbWatched.isEnabled = enabled
        binding.buttonImportImdbWatchlist.isEnabled = enabled
        binding.buttonImportLetterboxdWatched.isEnabled = enabled
        binding.buttonImportLetterboxdWatchlist.isEnabled = enabled
        binding.buttonClearAll.isEnabled = enabled
    }

    //=== DIALOG HELP E CONFERME ===

    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.imdb_help_title))
            .setMessage(getString(R.string.imdb_watched_help))
            .setPositiveButton(getString(R.string.select_file)) { _, _ ->
                pickFile(ImportType.IMDB_WATCHED)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.imdb_help_title))
            .setMessage(getString(R.string.imdb_watchlist_help))
            .setPositiveButton(getString(R.string.select_file)) { _, _ ->
                pickFile(ImportType.IMDB_WATCHLIST)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.letterboxd_help_title))
            .setMessage(getString(R.string.letterboxd_watched_help))
            .setPositiveButton(getString(R.string.select_file)) { _, _ ->
                pickFile(ImportType.LETTERBOXD_WATCHED)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.letterboxd_help_title))
            .setMessage(getString(R.string.letterboxd_watchlist_help))
            .setPositiveButton(getString(R.string.select_file)) { _, _ ->
                pickFile(ImportType.LETTERBOXD_WATCHLIST)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showClearAllConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.clear_all_title))
            .setMessage(getString(R.string.clear_all_message))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                homeViewModel.clearAllMovies()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    //=== FILE PICKER E PROCESSING ===

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentImportType?.let { type ->
                    checkPermissionsAndProcess(uri, type)
                }
            }
        }
    }

    private var currentImportType: ImportType? = null

    private fun pickFile(type: ImportType) {
        currentImportType = type
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            this.type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        filePickerLauncher.launch(intent)
    }

    private fun checkPermissionsAndProcess(uri: Uri, type: ImportType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    processFile(uri, type)
                }
                else -> {
                    pendingImport = Pair(uri, type)
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            processFile(uri, type)
        }
    }

    private fun processFile(uri: Uri, type: ImportType) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
                ?: throw Exception("impossibile leggere file")

            val filename = uri.lastPathSegment ?: "unknown.csv"

            val csvType = when (type) {
                ImportType.IMDB_WATCHED -> com.example.movieapp.data.repository.CsvType.IMDB_WATCHED
                ImportType.IMDB_WATCHLIST -> com.example.movieapp.data.repository.CsvType.IMDB_WATCHLIST
                ImportType.LETTERBOXD_WATCHED -> com.example.movieapp.data.repository.CsvType.LETTERBOXD_WATCHED
                ImportType.LETTERBOXD_WATCHLIST -> com.example.movieapp.data.repository.CsvType.LETTERBOXD_WATCHLIST
            }

            homeViewModel.startImport(requireContext(), inputStream, filename, csvType)

            Log.d(TAG, "import avviato: $filename ($csvType)")

        } catch (e: Exception) {
            Log.e(TAG, "errore processing file: ${e.message}", e)
            Toast.makeText(
                requireContext(),
                "errore: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}