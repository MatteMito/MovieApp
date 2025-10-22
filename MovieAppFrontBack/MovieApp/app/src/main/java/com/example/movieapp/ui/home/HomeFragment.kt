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
                    // Nessun enrichment in corso - nascondi card
                    binding.cardImportStatus.visibility = View.GONE
                    Log.d(TAG, "Progress hidden (no enrichment)")
                }
                processed < total -> {
                    // DURANTE enrichment: MOSTRA progress nella card
                    val percentage = (processed * 100) / total

                    binding.cardImportStatus.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false
                    binding.progressBarImport.progress = percentage
                    binding.textProgressImport.text = "$processed / $total ($percentage%)"
                    binding.textImportStatusTitle.text = "✨ Arricchimento in corso..."
                    binding.textImportStatusDescription.text = "Recupero dati TMDB"

                    Log.d(TAG, "Progress visible: $percentage%")
                }
                else -> {
                    // COMPLETATO: mostra 100% per 2 secondi poi nascondi
                    binding.cardImportStatus.visibility = View.VISIBLE
                    binding.progressBarImport.progress = 100
                    binding.textProgressImport.text = "$total / $total (100%)"
                    binding.textImportStatusTitle.text = "✅ Completato!"
                    binding.textImportStatusDescription.text = "$total film arricchiti"

                    Log.d(TAG, "Progress complete, hiding in 2s")

                    binding.cardImportStatus.postDelayed({
                        binding.cardImportStatus.visibility = View.GONE
                        Log.d(TAG, "Progress hidden after completion")
                    }, 2000)
                }
            }
        }

        //Observer import status
        homeViewModel.importStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is ImportStatus.IDLE -> {
                    // Nasconde card import
                    binding.cardImportStatus.visibility = View.GONE
                }

                is ImportStatus.PARSING -> {
                    // Mostra che sta leggendo il CSV
                    binding.cardImportStatus.visibility = View.VISIBLE
                    binding.textImportStatusTitle.text = "📖 Lettura CSV..."
                    binding.textImportStatusDescription.text = "Analisi file in corso"
                    binding.progressBarImport.isIndeterminate = true
                }

                is ImportStatus.SENDING_TO_BACKEND -> {
                    // Mostra che sta inviando al backend
                    binding.cardImportStatus.visibility = View.VISIBLE
                    binding.textImportStatusTitle.text = "📤 Invio dati..."
                    binding.textImportStatusDescription.text = "${status.count} film al backend"
                    binding.progressBarImport.isIndeterminate = true
                }

                is ImportStatus.ENRICHING -> {
                    // Mostra progress dettagliato con percentuale
                    binding.cardImportStatus.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false

                    val percentage = if (status.total > 0) {
                        (status.processed * 100) / status.total
                    } else 0

                    binding.progressBarImport.max = 100
                    binding.progressBarImport.progress = percentage
                    binding.textProgressImport.text = "${status.processed} / ${status.total} ($percentage%)"
                    binding.textImportStatusTitle.text = "✨ Arricchimento in corso..."
                    binding.textImportStatusDescription.text = "Recupero dati TMDB"

                    if (status.currentMovie.isNotEmpty()) {
                        binding.textCurrentMovieImport.text = "📽️ ${status.currentMovie}"
                        binding.textCurrentMovieImport.visibility = View.VISIBLE
                    } else {
                        binding.textCurrentMovieImport.visibility = View.GONE
                    }

                    Log.d(TAG, "📊 Progress: ${status.processed}/${status.total} ($percentage%)")
                }

                is ImportStatus.COMPLETED -> {
                    // Mostra completamento e poi nasconde dopo 2 secondi
                    binding.cardImportStatus.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false
                    binding.progressBarImport.progress = 100
                    binding.textProgressImport.text = "${status.total} / ${status.total} (100%)"
                    binding.textImportStatusTitle.text = "✅ Completato!"
                    binding.textImportStatusDescription.text = "${status.total} film importati"
                    binding.textCurrentMovieImport.visibility = View.GONE

                    Log.d(TAG, "✅ Import completato: ${status.total} film")

                    // Nasconde dopo 2 secondi
                    binding.cardImportStatus.postDelayed({
                        binding.cardImportStatus.visibility = View.GONE
                    }, 2000)
                }

                is ImportStatus.ERROR -> {
                    // Mostra errore nella card
                    binding.cardImportStatus.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false
                    binding.textImportStatusTitle.text = "❌ Errore"
                    binding.textImportStatusDescription.text = status.message
                    binding.textCurrentMovieImport.visibility = View.GONE

                    Log.e(TAG, "❌ Import error: ${status.message}")

                    // Nasconde dopo 5 secondi
                    binding.cardImportStatus.postDelayed({
                        binding.cardImportStatus.visibility = View.GONE
                    }, 5000)
                }
            }
        }

        //observer message
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                binding.textMessage.text = message
                binding.cardMessage.visibility = View.VISIBLE
                Log.d(TAG, "messaggio mostrato: $message")
            } else {
                binding.cardMessage.visibility = View.GONE
            }
        }

        //observer movies
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            val movieCount = movies.size
            Log.d(TAG, "aggiornamento lista film: $movieCount film totali")
            updateStatsUI(movieCount)
        }
    }

    //DIALOG HELPERS

    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 importa film visti imdb")
            .setMessage(buildString {
                appendLine("come ottenere ratings.csv:")
                appendLine()
                appendLine("1. vai su imdb.com")
                appendLine("2. accedi al tuo account")
                appendLine("3. vai su 'Your Ratings'")
                appendLine("4. clicca sui 3 puntini (⋮)")
                appendLine("5. seleziona 'Export'")
                appendLine("6. scarica ratings.csv")
                appendLine()
                appendLine("✨ il file contiene:")
                appendLine("• tutti i film che hai valutato")
                appendLine("• le tue votazioni")
                appendLine("• date di visione")
            })
            .setPositiveButton("seleziona file") { _, _ ->
                openImdbWatchedPicker()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 importa watchlist imdb")
            .setMessage(buildString {
                appendLine("come ottenere watchlist.csv:")
                appendLine()
                appendLine("1. vai su imdb.com")
                appendLine("2. accedi al tuo account")
                appendLine("3. vai su 'Your Watchlist'")
                appendLine("4. clicca sui 3 puntini (⋮)")
                appendLine("5. seleziona 'Export'")
                appendLine("6. scarica watchlist.csv")
                appendLine()
                appendLine("✨ il file contiene:")
                appendLine("• film che vuoi vedere")
                appendLine("• ordine della watchlist")
            })
            .setPositiveButton("seleziona file") { _, _ ->
                openImdbWatchlistPicker()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 importa diary letterboxd")
            .setMessage(buildString {
                appendLine("come ottenere diary.csv:")
                appendLine()
                appendLine("1. vai su letterboxd.com")
                appendLine("2. accedi al tuo account")
                appendLine("3. vai su Settings > Import & Export")
                appendLine("4. clicca 'Export Your Data'")
                appendLine("5. scarica diary.zip")
                appendLine("6. estrai diary.csv")
                appendLine()
                appendLine("✨ il file contiene:")
                appendLine("• tutti i film visti")
                appendLine("• date e votazioni")
                appendLine("• recensioni")
            })
            .setPositiveButton("seleziona file") { _, _ ->
                openLetterboxdWatchedPicker()
            }
            .setNegativeButton("annulla", null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 importa watchlist letterboxd")
            .setMessage(buildString {
                appendLine("come ottenere watchlist.csv:")
                appendLine()
                appendLine("1. vai su letterboxd.com")
                appendLine("2. accedi al tuo account")
                appendLine("3. vai su Settings > Import & Export")
                appendLine("4. clicca 'Export Your Data'")
                appendLine("5. scarica watchlist.zip")
                appendLine("6. estrai watchlist.csv")
                appendLine()
                appendLine("✨ il file contiene:")
                appendLine("• film da vedere")
                appendLine("• ordine personalizzato")
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
            // Disabilita pulsanti durante loading
            setButtonsEnabled(false)
            Log.d(TAG, "⏳ Loading ON")
        } else {
            // Riabilita pulsanti
            setButtonsEnabled(true)
            Log.d(TAG, "✅ Loading OFF")
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

        Log.d(TAG, "📊 Stats aggiornate: $movieCount film totali")
        Log.d(TAG, "  - Watched: ${stats["watched"]}")
        Log.d(TAG, "  - Watchlist: ${stats["watchlist"]}")
        Log.d(TAG, "  - Enriched: ${stats["enriched"]}")

        // Mostra stats nel messaggio se ci sono film
        if (movieCount > 0) {
            val watchedCount = stats["watched"] ?: 0
            val watchlistCount = stats["watchlist"] ?: 0
            val enrichedCount = stats["enriched"] ?: 0
            val enrichmentPercentage = if (movieCount > 0) {
                (enrichedCount * 100) / movieCount
            } else {
                0
            }

            val statsMessage = buildString {
                append("📊 $movieCount film totali")
                append(" • 👁️ $watchedCount visti")
                append(" • 📝 $watchlistCount watchlist")
                append(" • ✨ $enrichedCount arricchiti ($enrichmentPercentage%)")
            }

            binding.textMessage.text = statsMessage
            binding.cardMessage.visibility = View.VISIBLE
        } else {
            binding.cardMessage.visibility = View.GONE
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