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
 * HomeFragment FIXED
 */
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
            result.data?.data?.let { uri ->
                Log.d(TAG, "IMDB Watched selezionato")
                processImdbWatchedFile(uri)
            }
        }
    }

    private val imdbWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "IMDB Watchlist selezionato")
                processImdbWatchlistFile(uri)
            }
        }
    }

    private val letterboxdWatchedPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "Letterboxd Watched selezionato")
                processLetterboxdWatchedFile(uri)
            }
        }
    }

    private val letterboxdWatchlistPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                Log.d(TAG, "Letterboxd Watchlist selezionato")
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

        homeViewModel.initialize(requireContext())

        Log.d(TAG, "✅ HomeFragment creato FIXED")
        return binding.root
    }

    private fun setupUI() {
        //bottoni import
        binding.buttonImportImdbWatched.setOnClickListener {
            showImdbWatchedHelp()
        }

        binding.buttonImportImdbWatchlist.setOnClickListener {
            showImdbWatchlistHelp()
        }

        binding.buttonImportLetterboxdWatched.setOnClickListener {
            showLetterboxdWatchedHelp()
        }

        binding.buttonImportLetterboxdWatchlist.setOnClickListener {
            showLetterboxdWatchlistHelp()
        }

        binding.buttonClearAll.setOnClickListener {
            showClearAllConfirmation()
        }

        //swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "🔄 Refresh")
            homeViewModel.refreshFromBackend()  // ✅ CORRETTO

            // Aggiorna stats dopo sync
            binding.swipeRefresh.postDelayed({
                updatePermanentStatsCard()
            }, 500)
        }
    }

    private fun setupObservers() {
        //observer loading state
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            updateLoadingUI(isLoading)
        }

        // ✅ OBSERVER MOVIES: Aggiorna stats SEMPRE quando cambiano i film
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updatePermanentStatsCard()
            Log.d(TAG, "📊 Movies aggiornati: ${movies.size} film")
        }

        // 🆕 NUOVO: Observer contatori dal file importato
        homeViewModel.fileCounters.observe(viewLifecycleOwner) { (watched, watchlist) ->
            Log.d(TAG, "📥 Contatori file: $watched visti, $watchlist da vedere")
        }

        // 🆕 NUOVO: Observer contatori totali (dopo refresh)
        homeViewModel.totalCounters.observe(viewLifecycleOwner) { (watched, watchlist) ->
            Log.d(TAG, "📊 Contatori totali: $watched visti, $watchlist da vedere")

            // ✅ Aggiorna le stats card con i contatori corretti
            binding.textWatchedMovies.text = watched.toString()
            binding.textWatchlistMovies.text = watchlist.toString()
            binding.textTotalMovies.text = (watched + watchlist).toString()
        }

        // ✅ OBSERVER ENRICHMENT PROGRESS: Con CAP per evitare >100%
        homeViewModel.enrichmentProgress.observe(viewLifecycleOwner) { (processed, total) ->
            when {
                total == 0 -> {
                    // Nessun import in corso
                    binding.layoutImportProgress.visibility = View.GONE
                }
                processed < total -> {
                    // ✅ IMPORT IN CORSO con CAP
                    binding.layoutImportProgress.visibility = View.VISIBLE

                    // ✅ CAP: Se processed > total, usa total come max
                    val cappedProcessed = if (processed > total) total else processed

                    val percentage = if (total > 0) {
                        (cappedProcessed * 100) / total
                    } else {
                        0
                    }

                    // ✅ CAP percentuale a 100
                    val cappedPercentage = if (percentage > 100) 100 else percentage

                    binding.progressBarImport.isIndeterminate = false
                    binding.progressBarImport.progress = cappedPercentage
                    binding.textProgressImport.text = "$cappedProcessed / $total ($cappedPercentage%)"
                    binding.textImportTitle.text = "✨ Import in corso..."

                    Log.d(TAG, "📊 Progress: $cappedProcessed/$total ($cappedPercentage%) [original: $processed]")
                }
                else -> {
                    // ✅ COMPLETATO
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false
                    binding.progressBarImport.progress = 100
                    binding.textProgressImport.text = "$total / $total (100%)"
                    binding.textImportTitle.text = "✅ Completato!"

                    Log.d(TAG, "✅ Import completato!")

                    // ✅ CRITICO: Aggiorna stats SUBITO dopo completamento
                    updatePermanentStatsCard()

                    // Nascondi dopo 2 secondi
                    binding.layoutImportProgress.postDelayed({
                        binding.layoutImportProgress.visibility = View.GONE

                        // ✅ Aggiorna di nuovo per sicurezza
                        updatePermanentStatsCard()
                    }, 2000)
                }
            }
        }

        //Observer import status
        homeViewModel.importStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is ImportStatus.IDLE -> {
                    binding.layoutImportProgress.visibility = View.GONE
                }

                is ImportStatus.PARSING -> {
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.textImportTitle.text = "📖 Lettura file..."
                    binding.progressBarImport.isIndeterminate = true
                    binding.textProgressImport.text = "Analisi in corso"
                    binding.textCurrentMovieImport.text = ""
                }

                is ImportStatus.SENDING_TO_BACKEND -> {
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.textImportTitle.text = "📤 Invio dati..."
                    binding.progressBarImport.isIndeterminate = true
                    binding.textProgressImport.text = "${status.totalMovies} film"  // ✅ CORRETTO
                    binding.textCurrentMovieImport.text = ""
                }

                is ImportStatus.ENRICHING -> {
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false

                    // ✅ CAP: processed non può superare total
                    val cappedProcessed = if (status.processed > status.total) status.total else status.processed

                    val percentage = if (status.total > 0) {
                        (cappedProcessed * 100) / status.total
                    } else 0

                    // ✅ CAP percentuale
                    val cappedPercentage = if (percentage > 100) 100 else percentage

                    binding.progressBarImport.max = 100
                    binding.progressBarImport.progress = cappedPercentage
                    binding.textProgressImport.text = "$cappedProcessed / ${status.total} ($cappedPercentage%)"
                    binding.textImportTitle.text = "✨ Import in corso..."

                    if (status.currentMovie.isNotEmpty()) {
                        binding.textCurrentMovieImport.text = status.currentMovie
                    } else {
                        binding.textCurrentMovieImport.text = ""
                    }
                }

                is ImportStatus.COMPLETED -> {
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false
                    binding.progressBarImport.progress = 100
                    binding.textProgressImport.text = "${status.totalMovies} / ${status.totalMovies} (100%)"  // ✅ CORRETTO
                    binding.textImportTitle.text = "✅ Completato!"
                    binding.textCurrentMovieImport.text = ""

                    Log.d(TAG, "✅ Import COMPLETED")

                    // ✅ CRITICO: Forza aggiornamento stats
                    updatePermanentStatsCard()

                    // Nascondi dopo 2 secondi
                    binding.layoutImportProgress.postDelayed({
                        binding.layoutImportProgress.visibility = View.GONE

                        // ✅ Aggiorna ancora per sicurezza
                        updatePermanentStatsCard()
                    }, 2000)
                }

                is ImportStatus.ERROR -> {
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false
                    binding.textImportTitle.text = "❌ Errore"
                    binding.textProgressImport.text = "Operazione fallita"
                    binding.textCurrentMovieImport.text = ""

                    Log.e(TAG, "❌ Errore: ${status.message}")

                    // Nascondi dopo 3 secondi
                    binding.layoutImportProgress.postDelayed({
                        binding.layoutImportProgress.visibility = View.GONE
                    }, 3000)
                }
            }
        }
    }

    /**
     * ✅ Aggiorna card statistiche permanente
     * Chiamato SEMPRE dopo ogni modifica ai film
     */
    private fun updatePermanentStatsCard() {
        val movies = homeViewModel.movies.value ?: emptyList()  // ✅ CORRETTO

        val total = movies.size
        val watched = movies.count { it.isWatched }  // ✅ CORRETTO
        val watchlist = movies.count { !it.isWatched }  // ✅ CORRETTO

        binding.textTotalMovies.text = total.toString()
        binding.textWatchedMovies.text = watched.toString()
        binding.textWatchlistMovies.text = watchlist.toString()

        Log.d(TAG, "📊 Stats aggiornate: $total totali ($watched visti, $watchlist da vedere)")
    }

    //DIALOG HELPERS

    private fun showImdbWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 Importa Watched IMDB")
            .setMessage(buildString {
                appendLine("Come ottenere il file:")
                appendLine()
                appendLine("1. Vai su imdb.com")
                appendLine("2. Accedi al tuo account")
                appendLine("3. Vai su 'Your Ratings'")
                appendLine("4. Clicca sui 3 puntini (⋮)")
                appendLine("5. Seleziona 'Export'")
                appendLine("6. Scarica ratings.csv")
            })
            .setPositiveButton("Seleziona file") { _, _ ->
                openImdbWatchedPicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showImdbWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 Importa Watchlist IMDB")
            .setMessage(buildString {
                appendLine("Come ottenere il file:")
                appendLine()
                appendLine("1. Vai su imdb.com")
                appendLine("2. Accedi al tuo account")
                appendLine("3. Vai su 'Your Watchlist'")
                appendLine("4. Clicca sui 3 puntini (⋮)")
                appendLine("5. Seleziona 'Export'")
                appendLine("6. Scarica watchlist.csv")
            })
            .setPositiveButton("Seleziona file") { _, _ ->
                openImdbWatchlistPicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchedHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 Importa Watched Letterboxd")
            .setMessage(buildString {
                appendLine("Come ottenere il file:")
                appendLine()
                appendLine("1. Vai su letterboxd.com")
                appendLine("2. Accedi al tuo account")
                appendLine("3. Settings > Import & Export")
                appendLine("4. Clicca 'Export Your Data'")
                appendLine("5. Scarica e estrai diary.csv")
            })
            .setPositiveButton("Seleziona file") { _, _ ->
                openLetterboxdWatchedPicker()
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun showLetterboxdWatchlistHelp() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("📥 Importa Watchlist Letterboxd")
            .setMessage(buildString {
                appendLine("Come ottenere il file:")
                appendLine()
                appendLine("1. Vai su letterboxd.com")
                appendLine("2. Accedi al tuo account")
                appendLine("3. Settings > Import & Export")
                appendLine("4. Clicca 'Export Your Data'")
                appendLine("5. Scarica e estrai watchlist.csv")
            })
            .setPositiveButton("Seleziona file") { _, _ ->
                openLetterboxdWatchlistPicker()
            }
            .setNegativeButton("Annulla", null)
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
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchedCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore processing IMDB watched", e)
            Toast.makeText(requireContext(), "Errore durante l'import", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processImdbWatchlistFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processImdbWatchlistCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore processing IMDB watchlist", e)
            Toast.makeText(requireContext(), "Errore durante l'import", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processLetterboxdWatchedFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchedCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore processing Letterboxd watched", e)
            Toast.makeText(requireContext(), "Errore durante l'import", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processLetterboxdWatchlistFile(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream != null) {
                homeViewModel.processLetterboxdWatchlistCsv(inputStream)
            } else {
                Toast.makeText(requireContext(), "Impossibile leggere il file", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Errore processing Letterboxd watchlist", e)
            Toast.makeText(requireContext(), "Errore durante l'import", Toast.LENGTH_SHORT).show()
        }
    }

    //UI HELPERS

    private fun updateLoadingUI(isLoading: Boolean) {
        binding.swipeRefresh.isRefreshing = isLoading

        if (isLoading) {
            setButtonsEnabled(false)
        } else {
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

    //DIALOGS

    private fun showClearAllConfirmation() {
        val movieCount = homeViewModel.movies.value?.size ?: 0

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ Elimina tutto")
            .setMessage(
                if (movieCount > 0) {
                    "Vuoi eliminare tutti i $movieCount film?\n\n" +
                            "Questa azione è irreversibile."
                } else {
                    "Non ci sono film da eliminare."
                }
            )
            .setPositiveButton("Elimina") { _, _ ->
                homeViewModel.clearAllData()
                Toast.makeText(requireContext(), "Dati eliminati", Toast.LENGTH_SHORT).show()

                // ✅ Aggiorna stats dopo clear
                updatePermanentStatsCard()
            }
            .setNegativeButton("Annulla", null)
            .setIcon(R.drawable.ic_warning)
            .show()
    }

    //LIFECYCLE

    override fun onResume() {
        super.onResume()
        // ✅ Aggiorna stats quando il fragment torna visibile
        updatePermanentStatsCard()
        Log.d(TAG, "Fragment resumed - stats aggiornate")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}