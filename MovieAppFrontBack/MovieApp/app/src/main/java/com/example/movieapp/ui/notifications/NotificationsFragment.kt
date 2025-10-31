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
 * fragment con grafici interattivi avanzati per analisi cinematografica
 */
class NotificationsFragment : Fragment() {

    private val TAG = "NotificationsFragment"

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationsViewModel: NotificationsViewModel

    //colori vibranti per grafici
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
        }

        //tempo totale visione
        notificationsViewModel.totalWatchTime.observe(viewLifecycleOwner) { timeText ->
            binding.textTotalWatchTime.text = timeText
            binding.textTotalWatchTime.visibility = View.VISIBLE
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

        //grafico generi + testo cliccabile
        notificationsViewModel.genresData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupGenresPieChart(data)
            }
        }

        notificationsViewModel.topGenre.observe(viewLifecycleOwner) { (genre, count) ->
            binding.textTopGenre.text = "🎬 Genere più visto: $genre ($count film)"
            binding.textTopGenre.visibility = View.VISIBLE
            binding.textTopGenre.setOnClickListener {
                showMoviesDialog("Film $genre", notificationsViewModel.getMoviesByGenre(genre))
            }
        }

        //grafico anni + testo cliccabile
        notificationsViewModel.yearsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupYearsBarChart(data)
            }
        }

        notificationsViewModel.topYear.observe(viewLifecycleOwner) { (year, count) ->
            binding.textTopYear.text = "📅 Anno con più film: $year ($count film)"
            binding.textTopYear.visibility = View.VISIBLE
            binding.textTopYear.setOnClickListener {
                showMoviesDialog("Film del $year", notificationsViewModel.getMoviesByYear(year))
            }
        }

        //grafico registi + testo cliccabile
        notificationsViewModel.directorsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupDirectorsBarChart(data)
            }
        }

        notificationsViewModel.topDirector.observe(viewLifecycleOwner) { (director, count) ->
            binding.textTopDirector.text = "🎥 Regista preferito: $director ($count film)"
            binding.textTopDirector.visibility = View.VISIBLE
            binding.textTopDirector.setOnClickListener {
                showMoviesDialog("Film di $director", notificationsViewModel.getMoviesByDirector(director))
            }
        }

        //grafico ratings
        notificationsViewModel.ratingsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupRatingsBarChart(data)
            }
        }

        //grafico paesi + testo cliccabile
        notificationsViewModel.countriesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupCountriesBarChart(data)
            }
        }

        notificationsViewModel.topCountry.observe(viewLifecycleOwner) { (country, count) ->
            binding.textTopCountry.text = "🌍 Paese principale: $country ($count film)"
            binding.textTopCountry.visibility = View.VISIBLE
            binding.textTopCountry.setOnClickListener {
                showMoviesDialog("Film da $country", notificationsViewModel.getMoviesByCountry(country))
            }
        }

        //grafico decenni + testo cliccabile
        notificationsViewModel.decadesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupDecadesBarChart(data)
            }
        }

        notificationsViewModel.topDecade.observe(viewLifecycleOwner) { (decade, count) ->
            binding.textTopDecade.text = "📆 Decennio preferito: $decade ($count film)"
            binding.textTopDecade.visibility = View.VISIBLE
            binding.textTopDecade.setOnClickListener {
                showMoviesDialog("Film degli anni $decade", notificationsViewModel.getMoviesByDecade(decade))
            }
        }

        //punteggio medio per genere
        notificationsViewModel.genreRatingsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupGenreRatingsChart(data)
            }
        }

        //valutazione media per decennio
        notificationsViewModel.decadeRatingsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupDecadeRatingsChart(data)
            }
        }

        //durata vs valutazione
        notificationsViewModel.runtimeVsRatingData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupRuntimeVsRatingScatterChart(data)
            }
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
     * grafico 1: pie chart generi con click
     */
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
            setDrawEntryLabels(false)
            animateY(1000, Easing.EaseInOutQuad)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is PieEntry) {
                        val genre = e.label
                        val movies = notificationsViewModel.getMoviesByGenre(genre)
                        showMoviesDialog("Film $genre", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    /**
     * grafico 2: bar chart anni con click
     */
    private fun setupYearsBarChart(data: Map<Int, Int>) {
        val entries = data.map { BarEntry(it.key.toFloat(), it.value.toFloat()) }

        val dataSet = BarDataSet(entries, "Film per Anno").apply {
            color = Color.parseColor("#667eea")
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
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val year = e.x.toInt()
                        val movies = notificationsViewModel.getMoviesByYear(year)
                        showMoviesDialog("Film del $year", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    /**
     * grafico 3: horizontal bar chart registi con click
     */
    private fun setupDirectorsBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film per Regista").apply {
            color = Color.parseColor("#764ba2")
            valueTextSize = 11f
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
                textSize = 9f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateX(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val director = data.keys.toList()[e.x.toInt()]
                        val movies = notificationsViewModel.getMoviesByDirector(director)
                        showMoviesDialog("Film di $director", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    /**
     * grafico 4: bar chart ratings
     */
    private fun setupRatingsBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Distribuzione Voti").apply {
            color = Color.parseColor("#f093fb")
            valueTextSize = 11f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartRatings.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 5: bar chart paesi con click
     */
    private fun setupCountriesBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film per Paese").apply {
            color = Color.parseColor("#4facfe")
            valueTextSize = 10f
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
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val country = data.keys.toList()[e.x.toInt()]
                        val movies = notificationsViewModel.getMoviesByCountry(country)
                        showMoviesDialog("Film da $country", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    /**
     * grafico 6: bar chart decenni con click
     */
    private fun setupDecadesBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "Film per Decennio").apply {
            color = Color.parseColor("#43e97b")
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
                labelRotationAngle = -45f
            }
            axisLeft.axisMinimum = 0f
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val decade = data.keys.toList()[e.x.toInt()]
                        val movies = notificationsViewModel.getMoviesByDecade(decade)
                        showMoviesDialog("Film degli anni $decade", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    /**
     * grafico 7: punteggio medio per genere
     */
    private fun setupGenreRatingsChart(data: Map<String, Float>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value)
        }

        val dataSet = BarDataSet(entries, "Valutazione Media per Genere").apply {
            color = Color.parseColor("#38f9d7")
            valueTextSize = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f", value)
                }
            }
        }

        binding.chartGenreRatings.apply {
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
                axisMaximum = 10f
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    /**
     * grafico 8: valutazione media per decennio
     */
    private fun setupDecadeRatingsChart(data: Map<String, Float>) {
        val entries = data.entries.mapIndexed { index, entry ->
            Entry(index.toFloat(), entry.value)
        }

        val dataSet = LineDataSet(entries, "Valutazione Media per Decennio").apply {
            color = Color.parseColor("#fa709a")
            setCircleColor(Color.parseColor("#fa709a"))
            lineWidth = 3f
            circleRadius = 5f
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = Color.parseColor("#fa709a")
            fillAlpha = 50
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f", value)
                }
            }
        }

        binding.chartDecadeRatings.apply {
            this.data = LineData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 10f
            }
            axisRight.isEnabled = false
            legend.textSize = 11f
            animateX(1000)
            invalidate()
        }
    }

    /**
     * grafico 9: scatter plot durata vs valutazione
     */
    private fun setupRuntimeVsRatingScatterChart(data: List<Pair<Int, Float>>) {
        val entries = data.map { ScatterChart.ScatterShape.CIRCLE
            Entry(it.first.toFloat(), it.second)
        }

        val dataSet = ScatterDataSet(entries, "Durata vs Valutazione").apply {
            color = Color.parseColor("#fee140")
            scatterShapeSize = 8f
            valueTextSize = 0f
        }

        binding.chartRuntimeVsRating.apply {
            this.data = ScatterData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}min"
                    }
                }
            }
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 10f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return "${value.toInt()}★"
                    }
                }
            }
            axisRight.isEnabled = false
            legend.textSize = 11f
            animateXY(1000, 1000)
            invalidate()
        }
    }

    /**
     * dialog per mostrare lista film
     */
    private fun showMoviesDialog(title: String, movies: List<com.example.movieapp.data.models.Movie>) {
        if (movies.isEmpty()) {
            AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage("Nessun film trovato in questa categoria.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val movieTitles = movies.map { movie ->
            val year = movie.year?.let { " ($it)" } ?: ""
            val rating = movie.userRating?.let { " - ⭐${String.format("%.1f", it)}" } ?: ""
            "${movie.title}$year$rating"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("$title - ${movies.size} film")
            .setItems(movieTitles, null)
            .setPositiveButton("Chiudi", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        notificationsViewModel.refreshData()
        Log.d(TAG, "Fragment resumed - dati statistiche ricaricati")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}