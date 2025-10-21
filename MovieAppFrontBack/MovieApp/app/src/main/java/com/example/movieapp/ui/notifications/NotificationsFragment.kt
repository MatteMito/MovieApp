package com.example.movieapp.ui.notifications

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentNotificationsBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * fragment con 8 grafici interattivi per analisi cinematografica
 */
class NotificationsFragment : Fragment() {

    private val TAG = "NotificationsFragment"

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationsViewModel: NotificationsViewModel

    //colori vibranti per grafici
    private val chartColors = listOf(
        Color.parseColor("#667eea"), //purple blue
        Color.parseColor("#764ba2"), //deep purple
        Color.parseColor("#f093fb"), //pink
        Color.parseColor("#4facfe"), //sky blue
        Color.parseColor("#00f2fe"), //cyan
        Color.parseColor("#43e97b"), //green
        Color.parseColor("#38f9d7"), //turquoise
        Color.parseColor("#fa709a"), //rose
        Color.parseColor("#fee140"), //yellow
        Color.parseColor("#30cfd0"), //teal
        Color.parseColor("#a8edea"), //light cyan
        Color.parseColor("#fed6e3"), //light pink
        Color.parseColor("#fbc2eb"), //lavender
        Color.parseColor("#a6c1ee"), //light blue
        Color.parseColor("#ffecd2")  //peach
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        notificationsViewModel = ViewModelProvider(this)[NotificationsViewModel::class.java]
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)

        setupObservers()

        //inizializza viewmodel
        notificationsViewModel.initialize(requireContext())

        Log.d(TAG, "notificationsfragment creato")
        return binding.root
    }

    private fun setupObservers() {
        //stats cards
        notificationsViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCards(movies.size)
        }

        //charts loading state
        notificationsViewModel.chartsReady.observe(viewLifecycleOwner) { ready ->
            if (ready) {
                binding.cardChartsLoading.visibility = View.GONE
                binding.cardCharts.visibility = View.VISIBLE
            } else {
                binding.cardChartsLoading.visibility = View.VISIBLE
                binding.cardCharts.visibility = View.GONE
            }
        }

        //grafico generi
        notificationsViewModel.genresData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupGenresPieChart(data)
            }
        }

        //grafico anni
        notificationsViewModel.yearsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupYearsBarChart(data)
            }
        }

        //grafico registi
        notificationsViewModel.directorsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupDirectorsBarChart(data)
            }
        }

        //grafico ratings
        notificationsViewModel.ratingsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupRatingsBarChart(data)
            }
        }

        //grafico paesi
        notificationsViewModel.countriesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupCountriesBarChart(data)
            }
        }

        //nuovo: grafico decadi
        notificationsViewModel.decadesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupDecadesBarChart(data)
            }
        }

        //nuovo: grafico durata
        notificationsViewModel.runtimeDistributionData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupRuntimeBarChart(data)
            }
        }

        //nuovo: grafico timeline watched
        notificationsViewModel.watchedByMonthData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupWatchedTimelineChart(data)
            }
        }

        //testo analytics
        notificationsViewModel.analyticsText.observe(viewLifecycleOwner) { text ->
            binding.textDashboard.text = text
        }
    }

    private fun updateStatsCards(movieCount: Int) {
        val movies = notificationsViewModel.movies.value ?: emptyList()

        val watched = movies.count { it.isWatched }
        val watchlist = movies.count { !it.isWatched }

        binding.textTotalMovies.text = movieCount.toString()
        binding.textWatchedMovies.text = watched.toString()
        binding.textWatchlistMovies.text = watchlist.toString()
    }

    /**
     * grafico 1: pie chart generi
     */
    private fun setupGenresPieChart(data: Map<String, Int>) {
        val entries = data.map { PieEntry(it.value.toFloat(), it.key) }

        val dataSet = PieDataSet(entries, "").apply {
            colors = chartColors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
        }

        binding.chartGenres.apply {
            this.data = PieData(dataSet)
            description.isEnabled = false
            legend.textSize = 11f
            setDrawEntryLabels(false)
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    /**
     * grafico 2: bar chart anni
     */
    private fun setupYearsBarChart(data: Map<Int, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film").apply {
            color = chartColors[0]
            valueTextSize = 10f
        }

        binding.chartYears.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.map { it.toString() })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 9f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 3: bar chart registi
     */
    private fun setupDirectorsBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film").apply {
            color = chartColors[3]
            valueTextSize = 10f
        }

        binding.chartDirectors.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(
                    data.keys.map {
                        if (it.length > 15) it.substring(0, 15) + "..." else it
                    }
                )
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 9f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 4: bar chart ratings
     */
    private fun setupRatingsBarChart(data: Map<String, Int>) {
        val entries = data.entries
            .sortedBy { it.key.toInt() }
            .mapIndexed { index, entry ->
                BarEntry(index.toFloat(), entry.value.toFloat())
            }

        val dataSet = BarDataSet(entries, "Film").apply {
            color = chartColors[7]
            valueTextSize = 10f
        }

        binding.chartRatings.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(
                    data.keys.sortedBy { it.toInt() }.map { "⭐$it" }
                )
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 10f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 5: bar chart paesi (FIXED)
     */
    private fun setupCountriesBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film").apply {
            color = chartColors[5]
            valueTextSize = 10f
        }

        binding.chartCountries.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 9f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 6: bar chart decadi (NUOVO)
     */
    private fun setupDecadesBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film").apply {
            color = chartColors[1]
            valueTextSize = 10f
        }

        binding.chartDecades.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 10f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 7: bar chart durata (NUOVO)
     */
    private fun setupRuntimeBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film").apply {
            color = chartColors[4]
            valueTextSize = 10f
        }

        binding.chartRuntime.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -30f
                textSize = 9f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 8: line chart timeline watched (NUOVO)
     */
    private fun setupWatchedTimelineChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            Entry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = LineDataSet(entries, "Film visti").apply {
            color = chartColors[5]
            setCircleColor(chartColors[5])
            lineWidth = 3f
            circleRadius = 5f
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            cubicIntensity = 0.2f
        }

        binding.chartWatchedTimeline.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(
                    data.keys.map {
                        //converti "2024-01" in "Gen 24"
                        try {
                            val parts = it.split("-")
                            val year = parts[0].substring(2)
                            val monthNames = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu",
                                "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
                            val month = monthNames[parts[1].toInt() - 1]
                            "$month $year"
                        } catch (e: Exception) {
                            it
                        }
                    }
                )
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 9f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.textSize = 11f
            animateX(1000)
            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}