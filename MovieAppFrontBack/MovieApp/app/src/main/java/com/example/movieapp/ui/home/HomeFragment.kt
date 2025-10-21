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
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * fragment home con import csv e gestione film con ui ottimizzata con material design 3
 */
class HomeFragment : Fragment() {

    private val TAG = "HomeFragment"

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeViewModel: HomeViewModel

    //file pickers per tutti i tipi csv
    private val imdbWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "selezionato imdb ratings.csv")
                processImdbWatchedFile(uri)
            }
        }
    }

    private val imdbWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "selezionato imdb watchlist.csv")
                processImdbWatchlistFile(uri)
            }
        }
    }

    private val letterboxdWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "selezionato letterboxd diary.csv")
                processLetterboxdWatchedFile(uri)
            }
        }
    }

    private val letterboxdWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "selezionato letterboxd watchlist.csv")
                processLetterboxdWatchlistFile(uri)
            }
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

        //inizializza viewmodel con context
        homeViewModel.initialize(requireContext())

        Log.d(TAG, "homefragment creato")
        return binding.root
    }

    private fun setupUI() {
        //bottoni import imdb
        binding.buttonImportImdbWatched.setOnClickListener {
            showImdbWatchedHelp()
        }

        binding.buttonImportImdbWatchlist.setOnClickListener {
            showImdbWatchlistHelp()
        }

        //bottoni import letterboxd
        binding.buttonImportLetterboxdWatched.setOnClickListener {
            showLetterboxdWatchedHelp()
        }

        binding.buttonImportLetterboxdWatchlist.setOnClickListener {
            showLetterboxdWatchlistHelp()
        }

        //bottone clear all
        binding.buttonClearAll.setOnClickListener {
            showClearAllConfirmation()
        }

        //swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "refresh richiesto")
            homeViewModel.forceBackendSync()
        }
    }

    private fun setupObservers() {
        //observer loading state
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            updateLoadingUI(isLoading)
        }

        // Observer enrichment progress
        homeViewModel.enrichmentProgress.observe(viewLifecycleOwner) { (processed, total) ->
            Log.d(TAG, "📊 Progress update: $processed/$total")

            when {
                total == 0 -> {
                    // Nessun enrichment in corso
                    binding.layoutProgress.visibility = View.GONE
                    Log.d(TAG, "Progress hidden (no enrichment)")
                }
                processed < total -> {
                    // DURANTE enrichment: MOSTRA progress
                    val percentage = (processed * 100) / total

                    binding.layoutProgress.visibility = View.VISIBLE
                    binding.progressEnrichment.progress = percentage
                    binding.textProgressLabel.text = "elaborazione: $processed/$total ($percentage%)"

                    Log.d(TAG, "Progress visible: $percentage%")
                }
                else -> {
                    // COMPLETATO: mostra 100% per 2 secondi poi nascondi
                    binding.layoutProgress.visibility = View.VISIBLE
                    binding.progressEnrichment.progress = 100
                    binding.textProgressLabel.text = "✅ completato: $total/$total (100%)"

                    Log.d(TAG, "Progress complete, hiding in 2s")

                    binding.layoutProgress.postDelayed({
                        binding.layoutProgress.visibility = View.GONE
                        Log.d(TAG, "Progress hidden after completion")
                    }, 2000)
                }
            }
        }

        //observer message
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                binding.textHomeMessage.text = message
                binding.textHomeMessage.visibility = View.VISIBLE
            } else {
                binding.textHomeMessage.visibility = View.GONE
            }
        }

        //observer film count
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsUI(movies.size)
        }
    }

    //IMDB IMPORT DIALOGS

    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📊 importa da imdb - film visti")
            .setMessage(buildString {
                appendLine("come ottenere il file:")
                appendLine()
                appendLine("1. vai su imdb.com")
                appendLine("2. clicca sul tuo profilo")
                appendLine("3. vai su 'Your Ratings'")
                appendLine("4. clicca sui 3 puntini")
                appendLine("5. seleziona 'Export'")
                appendLine("6. scarica 'ratings.csv'")
                appendLine()
                appendLine("il file conterrà tutti i film")
                appendLine("che hai valutato su imdb")
            })
            .setPositiveButton("seleziona file") { _, _ ->
                openImdbWatchedPicker()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📝 importa da imdb - watchlist")
            .setMessage(buildString {
                appendLine("come ottenere il file:")
                appendLine()
                appendLine("1. vai su imdb.com")
                appendLine("2. clicca sul tuo profilo")
                appendLine("3. vai su 'Your Watchlist'")
                appendLine("4. clicca sui 3 puntini")
                appendLine("5. seleziona 'Export'")
                appendLine("6. scarica 'watchlist.csv'")
                appendLine()
                appendLine("il file conterrà tutti i film")
                appendLine("nella tua watchlist imdb")
            })
            .setPositiveButton("seleziona file") { _, _ ->
                openImdbWatchlistPicker()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    //LETTERBOXD IMPORT DIALOGS

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("🎬 importa da letterboxd - diary")
            .setMessage(buildString {
                appendLine("come ottenere il file:")
                appendLine()
                appendLine("1. vai su letterboxd.com")
                appendLine("2. clicca sul tuo profilo")
                appendLine("3. vai su 'Settings'")
                appendLine("4. vai su 'Import & Export'")
                appendLine("5. clicca 'Export Your Data'")
                appendLine("6. attendi email con link")
                appendLine("7. scarica e apri il file zip")
                appendLine("8. trova 'diary.csv'")
                appendLine()
                appendLine("il file conterrà tutti i film")
                appendLine("che hai registrato su letterboxd")
            })
            .setPositiveButton("seleziona file") { _, _ ->
                openLetterboxdWatchedPicker()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⭐ importa da letterboxd - watchlist")
            .setMessage(buildString {
                appendLine("come ottenere il file:")
                appendLine()
                appendLine("1. vai su letterboxd.com")
                appendLine("2. clicca sul tuo profilo")
                appendLine("3. vai su 'Settings'")
                appendLine("4. vai su 'Import & Export'")
                appendLine("5. clicca 'Export Your Data'")
                appendLine("6. attendi email con link")
                appendLine("7. scarica e apri il file zip")
                appendLine("8. trova 'watchlist.csv'")
                appendLine()
                appendLine("il file conterrà tutti i film")
                appendLine("nella tua watchlist letterboxd")
            })
            .setPositiveButton("seleziona file") { _, _ ->
                openLetterboxdWatchlistPicker()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    //FILE PICKERS

    private fun openImdbWatchedPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        imdbWatchedPicker.launch(intent)
    }

    private fun openImdbWatchlistPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        imdbWatchlistPicker.launch(intent)
    }

    private fun openLetterboxdWatchedPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        letterboxdWatchedPicker.launch(intent)
    }

    private fun openLetterboxdWatchlistPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/comma-separated-values"))
        }
        letterboxdWatchlistPicker.launch(intent)
    }

    //FILE PROCESSING

    private fun processImdbWatchedFile(uri: Uri) {
        try {
            Log.d(TAG, "processamento imdb ratings: $uri")

            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchedCsv(inputStream)
            } else {
                showError("impossibile leggere il file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore processing imdb watched", e)
            showError("errore: ${e.message}")
        }
    }

    private fun processImdbWatchlistFile(uri: Uri) {
        try {
            Log.d(TAG, "processamento imdb watchlist: $uri")

            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchlistCsv(inputStream)
            } else {
                showError("impossibile leggere il file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore processing imdb watchlist", e)
            showError("errore: ${e.message}")
        }
    }

    private fun processLetterboxdWatchedFile(uri: Uri) {
        try {
            Log.d(TAG, "processamento letterboxd diary: $uri")

            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchedCsv(inputStream)
            } else {
                showError("impossibile leggere il file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore processing letterboxd watched", e)
            showError("errore: ${e.message}")
        }
    }

    private fun processLetterboxdWatchlistFile(uri: Uri) {
        try {
            Log.d(TAG, "processamento letterboxd watchlist: $uri")

            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchlistCsv(inputStream)
            } else {
                showError("impossibile leggere il file")
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore processing letterboxd watchlist", e)
            showError("errore: ${e.message}")
        }
    }
    //UI HELPERS

    private fun updateLoadingUI(isLoading: Boolean) {
        binding.swipeRefresh.isRefreshing = isLoading

        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.layoutButtons.alpha = 0.5f
            binding.layoutButtons.isEnabled = false
            setButtonsEnabled(false)
        } else {
            binding.progressBar.visibility = View.GONE
            binding.layoutButtons.alpha = 1f
            binding.layoutButtons.isEnabled = true
            setButtonsEnabled(true)
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.buttonImportImdbWatched.isEnabled = enabled
        binding.buttonImportImdbWatchlist.isEnabled = enabled
        binding.buttonImportLetterboxdWatched.isEnabled = enabled
        binding.buttonImportLetterboxdWatchlist.isEnabled = enabled
        binding.buttonClearAll.isEnabled = enabled
    }

    private fun updateStatsUI(movieCount: Int) {
        val stats = homeViewModel.getStats()

        binding.textMovieCount.text = movieCount.toString()
        binding.textWatchedCount.text = stats["watched"]?.toString() ?: "0"
        binding.textWatchlistCount.text = stats["watchlist"]?.toString() ?: "0"
        binding.textEnrichedCount.text = stats["enriched"]?.toString() ?: "0"

        //mostra stats card solo se ci sono film
        if (movieCount > 0) {
            binding.cardStats.visibility = View.VISIBLE

            //calcola percentuale enrichment
            val enrichedCount = stats["enriched"] ?: 0
            val enrichmentPercentage = if (movieCount > 0) {
                (enrichedCount * 100) / movieCount
            } else {
                0
            }

            binding.textEnrichmentPercentage.text = "$enrichmentPercentage%"
            binding.progressEnrichmentStats.progress = enrichmentPercentage
        } else {
            binding.cardStats.visibility = View.GONE
        }
    }

    //DIALOGS

    private fun showBackendInfoDialog() {
        val backendInfo = homeViewModel.getCurrentBackendInfo()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ℹ️ info backend")
            .setMessage(buildString {
                appendLine("configurazione backend:")
                appendLine()
                backendInfo.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
                appendLine()
                appendLine("features:")
                appendLine("• import csv automatico")
                appendLine("• enrichment tmdb")
                appendLine("• cache intelligente")
                appendLine("• sync automatico")
                appendLine("• analytics avanzate")
            })
            .setPositiveButton("ok", null)
            .show()
    }

    private fun showClearAllConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ elimina tutto")
            .setMessage(buildString {
                val movieCount = homeViewModel.movies.value?.size ?: 0
                appendLine("sei sicuro di voler eliminare")
                appendLine("tutti i $movieCount film?")
                appendLine()
                appendLine("questa operazione:")
                appendLine("• rimuove tutti i film")
                appendLine("• pulisce la cache")
                appendLine("• elimina le statistiche")
                appendLine()
                appendLine("⚠️ questa azione è irreversibile!")
            })
            .setPositiveButton("elimina tutto") { _, _ ->
                homeViewModel.clearAllData()
                Toast.makeText(requireContext(), "tutti i dati eliminati", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("annulla", null)
            .setIcon(R.drawable.ic_warning)
            .show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("❌ errore")
            .setMessage(message)
            .setPositiveButton("ok", null)
            .show()
    }

    private fun showSuccess(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    //LIFECYCLE

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "homefragment resumed")

        //aggiorna stats quando fragment torna visibile
        val movieCount = homeViewModel.movies.value?.size ?: 0
        updateStatsUI(movieCount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "homefragment destroyed")
    }
}