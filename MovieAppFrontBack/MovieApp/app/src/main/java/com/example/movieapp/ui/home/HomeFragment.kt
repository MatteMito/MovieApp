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
 * HomeFragment MINIMALISTA
 *
 * ✅ UI pulita con solo info essenziali
 * ✅ Stats sempre visibili (Totali | Visti | Da vedere)
 * ✅ Progress bar accurata (0-100%)
 * ✅ Testi semplici e chiari
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

        Log.d(TAG, "✅ HomeFragment creato")
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
            homeViewModel.forceBackendSync()
        }
    }

    private fun setupObservers() {
        //observer loading state
        homeViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            updateLoadingUI(isLoading)
        }

        // ✅ OBSERVER MOVIES: Aggiorna stats permanenti
        homeViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updatePermanentStatsCard()
            Log.d(TAG, "📊 ${movies.size} film")
        }

        // ✅ OBSERVER ENRICHMENT PROGRESS: Gestisce progress bar
        homeViewModel.enrichmentProgress.observe(viewLifecycleOwner) { (processed, total) ->
            when {
                total == 0 -> {
                    // Nessun import in corso
                    binding.layoutImportProgress.visibility = View.GONE
                }
                processed < total -> {
                    // ✅ IMPORT IN CORSO
                    binding.layoutImportProgress.visibility = View.VISIBLE

                    val percentage = if (total > 0) {
                        (processed * 100) / total
                    } else {
                        0
                    }

                    binding.progressBarImport.isIndeterminate = false
                    binding.progressBarImport.progress = percentage
                    binding.textProgressImport.text = "$processed / $total ($percentage%)"
                    binding.textImportTitle.text = "✨ Import in corso..."

                    Log.d(TAG, "📊 $processed/$total ($percentage%)")
                }
                else -> {
                    // ✅ COMPLETATO
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false
                    binding.progressBarImport.progress = 100
                    binding.textProgressImport.text = "$total / $total (100%)"
                    binding.textImportTitle.text = "✅ Completato!"

                    Log.d(TAG, "✅ Import completato!")

                    // Aggiorna stats
                    updatePermanentStatsCard()

                    // Nascondi dopo 2 secondi
                    binding.layoutImportProgress.postDelayed({
                        binding.layoutImportProgress.visibility = View.GONE
                    }, 2000)
                }
            }
        }

        //Observer import status (per stati speciali)
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
                    binding.textProgressImport.text = "${status.count} film"
                    binding.textCurrentMovieImport.text = ""
                }

                is ImportStatus.ENRICHING -> {
                    binding.layoutImportProgress.visibility = View.VISIBLE
                    binding.progressBarImport.isIndeterminate = false

                    val percentage = if (status.total > 0) {
                        (status.processed * 100) / status.total
                    } else 0

                    binding.progressBarImport.max = 100
                    binding.progressBarImport.progress = percentage
                    binding.textProgressImport.text = "${status.processed} / ${status.total} ($percentage%)"
                    binding.textImportTitle.text = "✨ Import in corso..."

                    // Mostra titolo film in modo minimale (solo titolo, no emoji)
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
                    binding.textProgressImport.text = "${status.total} / ${status.total} (100%)"
                    binding.textImportTitle.text = "✅ Completato!"
                    binding.textCurrentMovieImport.text = ""

                    // Aggiorna stats
                    updatePermanentStatsCard()

                    // Nascondi dopo 2 secondi
                    binding.layoutImportProgress.postDelayed({
                        binding.layoutImportProgress.visibility = View.GONE
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

        //observer message (solo per info importanti/errori)
        homeViewModel.message.observe(viewLifecycleOwner) { message ->
            // Mostra solo se è un messaggio importante
            if (message.isNotEmpty() && (message.contains("❌") || message.contains("⚠️"))) {
                binding.textMessage.text = message
                binding.cardMessage.visibility = View.VISIBLE
            } else {
                binding.cardMessage.visibility = View.GONE
            }
        }
    }

    /**
     * ✅ Aggiorna card statistiche permanente
     */
    private fun updatePermanentStatsCard() {
        val stats = homeViewModel.getStats()

        val total = stats["total"] ?: 0
        val watched = stats["watched"] ?: 0
        val watchlist = stats["watchlist"] ?: 0

        binding.textTotalMovies.text = total.toString()
        binding.textWatchedMovies.text = watched.toString()
        binding.textWatchlistMovies.text = watchlist.toString()

        Log.d(TAG, "📊 Stats: $total totali ($watched visti, $watchlist da vedere)")
    }

    //DIALOG HELPERS - VERSIONE MINIMALE

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
                updatePermanentStatsCard()
            }
            .setNegativeButton("Annulla", null)
            .setIcon(R.drawable.ic_warning)
            .show()
    }

    //LIFECYCLE

    override fun onResume() {
        super.onResume()
        // Aggiorna stats quando il fragment torna visibile
        updatePermanentStatsCard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}