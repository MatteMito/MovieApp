package com.example.movieapp.ui.notifications

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.movieapp.R
import com.example.movieapp.databinding.FragmentNotificationsBinding
import com.github.mikephil.charting.animation.Easing
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight

/**
 * fragment con grafici interattivi
 */
class NotificationsFragment : Fragment() {

    private val TAG = "NotificationsFragment"

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationsViewModel: NotificationsViewModel

    //colori vibranti
    private val chartColors = listOf(
        Color.parseColor("#667eea"),
        Color.parseColor("#764ba2"),
        Color.parseColor("#f093fb"),
        Color.parseColor("#4facfe"),
        Color.parseColor("#00f2fe"),
        Color.parseColor("#43e97b"),
        Color.parseColor("#38f9d7"),
        Color.parseColor("#fa709a"),
        Color.parseColor("#fee140"),
        Color.parseColor("#30cfd0"),
        Color.parseColor("#a8edea"),
        Color.parseColor("#fed6e3"),
        Color.parseColor("#fbc2eb"),
        Color.parseColor("#a6c1ee"),
        Color.parseColor("#ffecd2")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        notificationsViewModel = ViewModelProvider(this)[NotificationsViewModel::class.java]
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)

        setupObservers()
        notificationsViewModel.initialize(requireContext())

        Log.d(TAG, "notificationsfragment creato")
        return binding.root
    }

    private fun setupObservers() {
        //stats cards
        notificationsViewModel.movies.observe(viewLifecycleOwner) { movies ->
            updateStatsCards(movies.size)

            //gestione visualizzazione quando non ci sono film
            if (movies.isEmpty()) {
                showEmptyState()
            } else {
                hideEmptyState()
            }
        }

        //charts loading
        notificationsViewModel.chartsReady.observe(viewLifecycleOwner) { ready ->
            val movies = notificationsViewModel.movies.value ?: emptyList()

            if (movies.isEmpty()) {
                //nessun film: mostra messaggio vuoto
                showEmptyState()
            } else if (ready) {
                //film presenti e grafici pronti
                binding.cardChartsLoading.visibility = View.GONE
                binding.cardCharts.visibility = View.VISIBLE
                hideEmptyState()
            } else {
                //film presenti ma grafici in caricamento
                binding.cardChartsLoading.visibility = View.VISIBLE
                binding.cardCharts.visibility = View.GONE
                hideEmptyState()
            }
        }

        //grafici esistenti
        notificationsViewModel.genresData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupGenresPieChart(data)
        }

        notificationsViewModel.topGenre.observe(viewLifecycleOwner) { (genre, count) ->
            binding.textTopGenre.text = "🎬 genere preferito: $genre ($count film)"
        }

        notificationsViewModel.yearsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupYearsBarChart(data)
        }

        notificationsViewModel.topYear.observe(viewLifecycleOwner) { (year, count) ->
            binding.textTopYear.text = "📅 anno preferito: $year ($count film)"
        }

        notificationsViewModel.directorsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupDirectorsBarChart(data)
        }

        notificationsViewModel.topDirector.observe(viewLifecycleOwner) { (director, count) ->
            binding.textTopDirector.text = "🎥 regista preferito: $director ($count film)"
        }

        notificationsViewModel.ratingsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupRatingsPieChart(data)
        }

        notificationsViewModel.countriesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupCountriesChart(data)
        }

        notificationsViewModel.topCountry.observe(viewLifecycleOwner) { (country, count) ->
            binding.textTopCountry.text = "🌍 paese preferito: $country ($count film)"
        }

        notificationsViewModel.decadesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupDecadesBarChart(data)
        }

        notificationsViewModel.topDecade.observe(viewLifecycleOwner) { (decade, count) ->
            binding.textTopDecade.text = "📆 decade preferita: $decade ($count film)"
        }

        notificationsViewModel.runtimeDistributionData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupRuntimeDistributionChart(data)
        }

        notificationsViewModel.watchedByMonthData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupWatchedTimelineChart(data)
        }

        //nuovi grafici - fix conversione Double -> Float
        notificationsViewModel.totalWatchTimeText.observe(viewLifecycleOwner) { text ->
            binding.textTotalWatchTime.text = "⏱️ tempo totale di visione: $text"
        }

        notificationsViewModel.averageRatingByGenre.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                //converte da Double a Float
                val floatData = data.mapValues { it.value.toFloat() }
                setupAverageRatingByGenreChart(floatData)
            }
        }

        notificationsViewModel.actorsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupActorsBarChart(data)
        }

        notificationsViewModel.topActor.observe(viewLifecycleOwner) { (actor, count) ->
            binding.textTopActor.text = "🌟 attore preferito: $actor ($count film)"
        }

        notificationsViewModel.productionCompaniesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupProductionCompaniesChart(data)
        }

        notificationsViewModel.topProductionCompany.observe(viewLifecycleOwner) { (company, count) ->
            binding.textTopCompany.text = "🏢 studio preferito: $company ($count film)"
        }

        notificationsViewModel.runtimeVsRatingData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                //converte da Double a Float
                val floatData = data.map { it.first to it.second.toFloat() }
                setupRuntimeVsRatingScatter(floatData)
            }
        }

        notificationsViewModel.genreCombinationsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) setupGenreCombinationsChart(data)
        }
    }

    /**
     * mostra stato vuoto quando non ci sono film
     */
    private fun showEmptyState() {
        binding.progressBar.visibility = View.GONE
        binding.textNoData.visibility = View.VISIBLE
        binding.cardCharts.visibility = View.GONE
        binding.cardChartsLoading.visibility = View.GONE
        binding.statsCard.visibility = View.VISIBLE
    }

    /**
     * nasconde stato vuoto quando ci sono film
     */
    private fun hideEmptyState() {
        binding.progressBar.visibility = View.GONE
        binding.textNoData.visibility = View.GONE
    }

    private fun updateStatsCards(movieCount: Int) {
        val movies = notificationsViewModel.movies.value ?: emptyList()

        val watchedCount = movies.count { it.isWatched }
        val watchlistCount = movies.count { !it.isWatched }

        val totalWatchTimeMinutes = movies
            .filter { it.isWatched && it.runtime != null }
            .sumOf { it.runtime ?: 0 }
        val totalWatchTimeHours = (totalWatchTimeMinutes / 60.0)

        binding.textTotalMovies.text = movieCount.toString()
        binding.textWatchedMovies.text = watchedCount.toString()
        binding.textWatchlistMovies.text = watchlistCount.toString()
        binding.textWatchHours.text = String.format("%.0f", totalWatchTimeHours)
    }

    //tutti i metodi setup grafici

    private fun setupGenresPieChart(data: Map<String, Int>) {
        val entries = data.map { PieEntry(it.value.toFloat(), it.key) }

        val dataSet = PieDataSet(entries, "").apply {
            colors = chartColors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartGenres.apply {
            this.data = PieData(dataSet)
            description.isEnabled = false
            legend.textSize = 10f
            legend.formSize = 10f
            legend.xEntrySpace = 7f
            legend.yEntrySpace = 5f
            setDrawEntryLabels(false)
            animateY(1000, Easing.EaseInOutQuad)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is PieEntry) {
                        val genre = e.label
                        showMoviesDialog(
                            title = "film del genere: $genre",
                            movies = notificationsViewModel.getMoviesByGenre(genre)
                        )
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    private fun setupYearsBarChart(data: Map<Int, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[0]
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartYears.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            setVisibleXRangeMaximum(10f)
            moveViewToX(data.size.toFloat())

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.map { it.toString() })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 9f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val yearsList = data.keys.toList()
                        val year = yearsList[e.x.toInt()]
                        showMoviesDialog(
                            title = "film del $year",
                            movies = notificationsViewModel.getMoviesByYear(year)
                        )
                    }
                }
                override fun onNothingSelected() {}
            })

            animateX(1000)
            invalidate()
        }
    }

    private fun setupDirectorsBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[1]
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartDirectors.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 8f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val directorsList = data.keys.toList()
                        val director = directorsList[e.x.toInt()]
                        showMoviesDialog(
                            title = "film di $director",
                            movies = notificationsViewModel.getMoviesByDirector(director)
                        )
                    }
                }
                override fun onNothingSelected() {}
            })

            animateX(1000)
            invalidate()
        }
    }

    private fun setupRatingsPieChart(data: Map<String, Int>) {
        val entries = data.map { PieEntry(it.value.toFloat(), it.key) }

        val dataSet = PieDataSet(entries, "").apply {
            colors = chartColors
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartRatings.apply {
            this.data = PieData(dataSet)
            description.isEnabled = false
            legend.textSize = 10f
            setDrawEntryLabels(false)
            animateY(1000, Easing.EaseInOutQuad)
            invalidate()
        }
    }

    private fun setupCountriesChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[5]
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
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
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            animateX(1000)
            invalidate()
        }
    }

    private fun setupDecadesBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[4]
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
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
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val decadesList = data.keys.toList()
                        val decade = decadesList[e.x.toInt()]
                        showMoviesDialog(
                            title = "film degli anni $decade",
                            movies = notificationsViewModel.getMoviesByDecade(decade)
                        )
                    }
                }
                override fun onNothingSelected() {}
            })

            animateX(1000)
            invalidate()
        }
    }

    private fun setupRuntimeDistributionChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[5]
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartRuntime.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 10f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            animateY(1000)
            invalidate()
        }
    }

    private fun setupWatchedTimelineChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            Entry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = LineDataSet(entries, "film visti").apply {
            color = chartColors[6]
            setCircleColor(chartColors[6])
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 9f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartWatchedTimeline.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 7f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            animateY(1000)
            invalidate()
        }
    }

    private fun setupAverageRatingByGenreChart(data: Map<String, Float>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value)
        }

        val dataSet = BarDataSet(entries, "valutazione media").apply {
            color = chartColors[7]
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f", value)
                }
            }
        }

        binding.chartAverageRatingByGenre.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 8f
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            animateY(1000)
            invalidate()
        }
    }

    private fun setupActorsBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[8]
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartActors.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 8f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val actorsList = data.keys.toList()
                        val actor = actorsList[e.x.toInt()]
                        showMoviesDialog(
                            title = "film con $actor",
                            movies = notificationsViewModel.getMoviesByActor(actor)
                        )
                    }
                }
                override fun onNothingSelected() {}
            })

            animateX(1000)
            invalidate()
        }
    }

    private fun setupProductionCompaniesChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[9]
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartProductionCompanies.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 8f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val companiesList = data.keys.toList()
                        val company = companiesList[e.x.toInt()]
                        showMoviesDialog(
                            title = "film di $company",
                            movies = notificationsViewModel.getMoviesByCompany(company)
                        )
                    }
                }
                override fun onNothingSelected() {}
            })

            animateX(1000)
            invalidate()
        }
    }

    private fun setupRuntimeVsRatingScatter(data: List<Pair<Int, Float>>) {
        val entries = data.map { (runtime, rating) ->
            Entry(runtime.toFloat(), rating)
        }

        val dataSet = ScatterDataSet(entries, "film").apply {
            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
            color = chartColors[10]
            scatterShapeSize = 12f
            setDrawValues(false)
        }

        binding.chartRuntimeVsRating.apply {
            this.data = ScatterData(dataSet)
            description.text = "durata vs valutazione"
            description.textSize = 11f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textSize = 10f
                axisMinimum = 0f
                granularity = 20f
            }
            axisLeft.apply {
                textSize = 10f
                axisMinimum = 0f
                axisMaximum = 10f
            }
            axisRight.isEnabled = false
            legend.textSize = 10f

            animateXY(1000, 1000)
            invalidate()
        }
    }

    private fun setupGenreCombinationsChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "film").apply {
            color = chartColors[11]
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartGenreCombinations.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 7f
            }
            axisLeft.apply {
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisRight.isEnabled = false
            legend.isEnabled = false

            animateY(1000)
            invalidate()
        }
    }

    private fun showMoviesDialog(title: String, movies: List<com.example.movieapp.data.models.Movie>) {
        if (movies.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage("nessun film trovato")
                .setPositiveButton("ok", null)
                .show()
            return
        }

        val movieTitles = movies.map { movie ->
            val year = movie.year?.let { " ($it)" } ?: ""
            val rating = movie.userRating?.let { " - ⭐${it.toInt()}" } ?: ""
            "${movie.title}$year$rating"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("$title - ${movies.size} film")
            .setItems(movieTitles, null)
            .setPositiveButton("chiudi", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        notificationsViewModel.refreshData()
        Log.d(TAG, "fragment resumed")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}