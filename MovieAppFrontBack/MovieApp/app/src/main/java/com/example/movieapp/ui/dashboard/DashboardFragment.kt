package com.example.movieapp.ui.dashboard

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.databinding.FragmentDashboardBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * fragment dashboard con statistiche dettagliate con ui pulita e leggibile
 */
class DashboardFragment : Fragment() {

    private val TAG = "DashboardFragment"

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var dashboardViewModel: DashboardViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dashboardViewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        setupUI()
        setupObservers()

        //inizializza viewmodel
        dashboardViewModel.initialize(requireContext())

        Log.d(TAG, "dashboardfragment creato")
        return binding.root
    }

    private fun setupUI() {
        //swipe refresh
        binding.swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "refresh richiesto")
            dashboardViewModel.refreshStats()
            binding.swipeRefresh.isRefreshing = false
        }

        //bottone info
        binding.buttonInfo.setOnClickListener {
            showInfoDialog()
        }
    }

    private fun setupObservers() {
        //observer stats text
        dashboardViewModel.statsText.observe(viewLifecycleOwner) { stats ->
            binding.textStats.text = stats
        }

        //observer movie count
        dashboardViewModel.movieCount.observe(viewLifecycleOwner) { count ->
            binding.textMovieCount.text = count
        }

        //observer watch hours
        dashboardViewModel.watchHours.observe(viewLifecycleOwner) { hours ->
            binding.textWatchHours.text = hours
        }

        //observer movies
        dashboardViewModel.movies.observe(viewLifecycleOwner) { movies ->
            //mostra empty state se nessun film
            if (movies.isEmpty()) {
                binding.layoutEmptyState.visibility = View.VISIBLE
                binding.scrollViewStats.visibility = View.GONE
            } else {
                binding.layoutEmptyState.visibility = View.GONE
                binding.scrollViewStats.visibility = View.VISIBLE
            }
        }
    }

    private fun showInfoDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("ℹ️ info statistiche")
            .setMessage(buildString {
                appendLine("questa sezione mostra:")
                appendLine()
                appendLine("📊 panoramica generale")
                appendLine("• film totali")
                appendLine("• film visti")
                appendLine("• film da vedere")
                appendLine("• tempo totale visione")
                appendLine()
                appendLine("⭐ valutazioni")
                appendLine("• voto medio")
                appendLine("• distribuzione voti")
                appendLine()
                appendLine("🎭 generi preferiti")
                appendLine("• top 5 generi")
                appendLine("• percentuali")
                appendLine()
                appendLine("🎬 registi più visti")
                appendLine("• top 3 registi")
                appendLine("• numero film")
                appendLine()
                appendLine("💡 vai su 'statistiche' per")
                appendLine("grafici interattivi avanzati!")
            })
            .setPositiveButton("ok", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "dashboardfragment resumed")

        //refresh stats quando torna visibile
        dashboardViewModel.refreshStats()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        Log.d(TAG, "dashboardfragment destroyed")
    }
}