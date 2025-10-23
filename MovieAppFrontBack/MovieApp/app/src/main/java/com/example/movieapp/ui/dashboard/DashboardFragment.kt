package com.example.movieapp.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.databinding.FragmentDashboardBinding
import com.example.movieapp.data.network.ApiService
import android.util.Log

/**
 * DashboardFragment con controllo autenticazione
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DashboardViewModel

    private val TAG = "DashboardFragment"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Verifica autenticazione
        if (!ApiService.isAuthenticated() || !ApiService.hasUserId()) {
            Toast.makeText(
                requireContext(),
                "Effettua il login per vedere le statistiche",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Inizializza ViewModel
        viewModel.initialize(requireContext())

        // Observer per stats
        viewModel.statsText.observe(viewLifecycleOwner) { stats ->
            binding.textStats.text = stats
        }

        viewModel.movieCount.observe(viewLifecycleOwner) { count ->
            binding.textMovieCount.text = count
        }

        viewModel.watchHours.observe(viewLifecycleOwner) { hours ->
            binding.textWatchHours.text = hours
        }
    }

    override fun onResume() {
        super.onResume()

        // 🆕 Ricarica stats quando si torna al fragment
        if (viewModel.isUserAuthenticated()) {
            Log.d(TAG, "🔄 onResume - refresh stats")
            viewModel.refreshStats()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}