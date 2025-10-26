// FILE: app/src/main/java/com/example/movieapp/ui/social/FiltersBottomSheet.kt
// Bottom Sheet per filtri avanzati

package com.example.movieapp.ui.social

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.movieapp.R
import com.example.movieapp.databinding.BottomSheetFiltersBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider

class FiltersBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    private var currentFilters: ListFilters = ListFilters.default()
    private var onApplyFilters: ((ListFilters) -> Unit)? = null

    companion object {
        private const val ARG_CURRENT_FILTERS = "current_filters"

        fun newInstance(currentFilters: ListFilters): FiltersBottomSheet {
            return FiltersBottomSheet().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_CURRENT_FILTERS, currentFilters.sortOrder)
                    putInt("minMovies", currentFilters.minMovieCount)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Recupera filtri correnti dagli argomenti
        arguments?.let {
            val sortOrder = it.getSerializable(ARG_CURRENT_FILTERS) as? SortOrder ?: SortOrder.RECENT
            val minMovies = it.getInt("minMovies", 0)
            currentFilters = ListFilters(
                sortOrder = sortOrder,
                minMovieCount = minMovies
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // Imposta ordinamento corrente
        when (currentFilters.sortOrder) {
            SortOrder.RECENT -> binding.chipSortRecent.isChecked = true
            SortOrder.NAME_ASC -> binding.chipSortName.isChecked = true
            SortOrder.MOVIE_COUNT -> binding.chipSortMovies.isChecked = true
            SortOrder.POPULAR -> binding.chipSortPopular.isChecked = true
        }

        // Imposta slider numero minimo film
        binding.sliderMinMovies.value = currentFilters.minMovieCount.toFloat()
        binding.textMinMoviesValue.text = currentFilters.minMovieCount.toString()

        // Listener slider
        binding.sliderMinMovies.addOnChangeListener { slider, value, fromUser ->
            binding.textMinMoviesValue.text = value.toInt().toString()
        }
    }

    private fun setupListeners() {
        // Bottone Reset
        binding.buttonResetFilters.setOnClickListener {
            resetFilters()
        }

        // Bottone Applica
        binding.buttonApply.setOnClickListener {
            applyFilters()
        }

        // Bottone Annulla
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun resetFilters() {
        binding.chipSortRecent.isChecked = true
        binding.sliderMinMovies.value = 0f
        binding.textMinMoviesValue.text = "0"
    }

    private fun applyFilters() {
        val selectedSortOrder = when {
            binding.chipSortRecent.isChecked -> SortOrder.RECENT
            binding.chipSortName.isChecked -> SortOrder.NAME_ASC
            binding.chipSortMovies.isChecked -> SortOrder.MOVIE_COUNT
            binding.chipSortPopular.isChecked -> SortOrder.POPULAR
            else -> SortOrder.RECENT
        }

        val minMovieCount = binding.sliderMinMovies.value.toInt()

        val newFilters = currentFilters.copy(
            sortOrder = selectedSortOrder,
            minMovieCount = minMovieCount
        )

        onApplyFilters?.invoke(newFilters)
        dismiss()
    }

    fun setOnApplyFiltersListener(listener: (ListFilters) -> Unit) {
        onApplyFilters = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}