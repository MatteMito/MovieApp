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
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.ScatterChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.github.mikephil.charting.highlight.Highlight

//fragment con grafici interattivi avanzati per analisi cinematografica
class NotificationsFragment : Fragment() {

    private val TAG = "NotificationsFragment"

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var notificationsViewModel: NotificationsViewModel

    //colori vibranti per grafici
    private val chartColors = listOf(
        Color.parseColor("#667eea"), Color.parseColor("#764ba2"), Color.parseColor("#f093fb"),
        Color.parseColor("#4facfe"), Color.parseColor("#00f2fe"), Color.parseColor("#43e97b"),
        Color.parseColor("#38f9d7"), Color.parseColor("#fa709a"), Color.parseColor("#fee140"),
        Color.parseColor("#30cfd0"), Color.parseColor("#a8edea"), Color.parseColor("#fed6e3"),
        Color.parseColor("#fbc2eb"), Color.parseColor("#a6c1ee"), Color.parseColor("#ffecd2"),
        Color.parseColor("#89f7fe"), Color.parseColor("#66a6ff"), Color.parseColor("#f8b195"),
        Color.parseColor("#c06c84"), Color.parseColor("#6c5ce7")
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
        //gestisce visualizzazione messaggio quando non ci sono film
        notificationsViewModel.movies.observe(viewLifecycleOwner) { movies ->
            if (movies.isEmpty()) {
                binding.textNoData.visibility = View.VISIBLE
                binding.cardCharts.visibility = View.GONE
                binding.cardChartsLoading.visibility = View.GONE
                Log.d(TAG, "nessun film, mostra messaggio")
            } else {
                binding.textNoData.visibility = View.GONE
            }
        }

        //contatori per card riepilogo
        notificationsViewModel.totalMovies.observe(viewLifecycleOwner) { count ->
            binding.textTotalMovies.text = count.toString()
        }

        notificationsViewModel.watchedMovies.observe(viewLifecycleOwner) { count ->
            binding.textWatchedMovies.text = count.toString()
        }

        notificationsViewModel.watchlistMovies.observe(viewLifecycleOwner) { count ->
            binding.textWatchlistMovies.text = count.toString()
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
                binding.textNoData.visibility = View.GONE
            } else {
                val movies = notificationsViewModel.movies.value ?: emptyList()

                if (movies.isEmpty()) {
                    binding.textNoData.visibility = View.VISIBLE
                    binding.cardCharts.visibility = View.GONE
                } else {
                    binding.cardChartsLoading.visibility = View.VISIBLE
                    binding.cardCharts.visibility = View.GONE
                }
            }
        }

        //grafico generi
        notificationsViewModel.genresData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupGenresPieChart(data)
            }
        }

        notificationsViewModel.topGenre.observe(viewLifecycleOwner) { (genre, count) ->
            binding.textTopGenre.text = "🎬 genere più visto: $genre ($count film)"
            binding.textTopGenre.visibility = View.VISIBLE
            binding.textTopGenre.setOnClickListener {
                showMoviesDialog("Film $genre", notificationsViewModel.getMoviesByGenre(genre))
            }
        }

        //grafico anni
        notificationsViewModel.yearsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupYearsBarChart(data)
            }
        }

        notificationsViewModel.topYear.observe(viewLifecycleOwner) { (year, count) ->
            binding.textTopYear.text = "📅 anno con più film: $year ($count film)"
            binding.textTopYear.visibility = View.VISIBLE
            binding.textTopYear.setOnClickListener {
                showMoviesDialog("Film del $year", notificationsViewModel.getMoviesByYear(year))
            }
        }

        //grafico registi
        notificationsViewModel.directorsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupDirectorsBarChart(data)
            }
        }

        notificationsViewModel.topDirector.observe(viewLifecycleOwner) { (director, count) ->
            binding.textTopDirector.text = "🎥 regista preferito: $director ($count film)"
            binding.textTopDirector.visibility = View.VISIBLE
            binding.textTopDirector.setOnClickListener {
                showMoviesDialog("Film di $director", notificationsViewModel.getMoviesByDirector(director))
            }
        }

        //grafico attori
        notificationsViewModel.actorsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupActorsBarChart(data)
            }
        }

        notificationsViewModel.topActor.observe(viewLifecycleOwner) { (actor, count) ->
            binding.textTopActor.text = "⭐ attore preferito: $actor ($count film)"
            binding.textTopActor.visibility = View.VISIBLE
            binding.textTopActor.setOnClickListener {
                showMoviesDialog("Film con $actor", notificationsViewModel.getMoviesByActor(actor))
            }
        }

        //grafico tmdb ratings
        notificationsViewModel.tmdbRatingsData.observe(viewLifecycleOwner) { data ->
            Log.d(TAG, "tmdbRatingsData observer: size = ${data.size}, data = $data")
            if (data.isNotEmpty()) {
                binding.textRatingsEmpty.visibility = View.GONE
                binding.chartRatings.visibility = View.VISIBLE
                setupTmdbRatingsPieChart(data)
            } else {
                Log.w(TAG, "tmdbRatingsData vuoto, mostra messaggio")
                binding.textRatingsEmpty.visibility = View.VISIBLE
                binding.chartRatings.visibility = View.GONE
            }
        }

        //grafico paesi
        notificationsViewModel.countriesData.observe(viewLifecycleOwner) { data ->
            Log.d(TAG, "countriesData observer: size = ${data.size}, data = $data")
            if (data.isNotEmpty()) {
                binding.textCountriesEmpty.visibility = View.GONE
                binding.chartCountries.visibility = View.VISIBLE
                setupCountriesBarChart(data)
            } else {
                Log.w(TAG, "countriesData vuoto, mostra messaggio")
                binding.textCountriesEmpty.visibility = View.VISIBLE
                binding.chartCountries.visibility = View.GONE
            }
        }

        notificationsViewModel.topCountry.observe(viewLifecycleOwner) { (country, count) ->
            binding.textTopCountry.text = "🌍 paese principale: $country ($count film)"
            binding.textTopCountry.visibility = View.VISIBLE
            binding.textTopCountry.setOnClickListener {
                showMoviesDialog("Film da $country", notificationsViewModel.getMoviesByCountry(country))
            }
        }

        //grafico decenni
        notificationsViewModel.decadesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupDecadesBarChart(data)
            }
        }

        notificationsViewModel.topDecade.observe(viewLifecycleOwner) { (decade, count) ->
            binding.textTopDecade.text = "📆 decennio preferito: $decade ($count film)"
            binding.textTopDecade.visibility = View.VISIBLE
            binding.textTopDecade.setOnClickListener {
                showMoviesDialog("Film degli anni $decade", notificationsViewModel.getMoviesByDecade(decade))
            }
        }

        notificationsViewModel.decadeDescription.observe(viewLifecycleOwner) { description ->
            binding.textDecadeDescription.text = description
            binding.textDecadeDescription.visibility = View.VISIBLE
        }

        //grafico combinazioni generi
        notificationsViewModel.genreCombinationsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupGenreCombinationsChart(data)
            }
        }

        notificationsViewModel.topGenreCombination.observe(viewLifecycleOwner) { (combo, count) ->
            binding.textTopGenreCombination.text = "🎭 combinazione più frequente: ${combo.first} + ${combo.second} ($count film)"
            binding.textTopGenreCombination.visibility = View.VISIBLE
            binding.textTopGenreCombination.setOnClickListener {
                showMoviesDialog("Film ${combo.first} + ${combo.second}",
                    notificationsViewModel.getMoviesByGenreCombination(combo))
            }
        }

        //grafico fasce durata
        notificationsViewModel.runtimeRangesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupRuntimeRangesChart(data)
            }
        }

        notificationsViewModel.longestMovies.observe(viewLifecycleOwner) { movies ->
            if (movies.isNotEmpty()) {
                val longest = movies.first()
                binding.textLongestMovie.text = "⏱️ film più lungo: ${longest.title} (${longest.runtime} min)"
                binding.textLongestMovie.visibility = View.VISIBLE
                binding.textLongestMovie.setOnClickListener {
                    showMoviesDialog("Film più lunghi", movies)
                }
            }
        }

        //grafico lingue originali
        notificationsViewModel.originalLanguagesData.observe(viewLifecycleOwner) { data ->
            Log.d(TAG, "originalLanguagesData observer: size = ${data.size}, data = $data")
            if (data.isNotEmpty()) {
                binding.textOriginalLanguagesEmpty.visibility = View.GONE
                binding.chartOriginalLanguages.visibility = View.VISIBLE
                setupOriginalLanguagesPieChart(data)
            } else {
                Log.w(TAG, "originalLanguagesData vuoto, mostra messaggio")
                binding.textOriginalLanguagesEmpty.visibility = View.VISIBLE
                binding.chartOriginalLanguages.visibility = View.GONE
            }
        }

        notificationsViewModel.topOriginalLanguage.observe(viewLifecycleOwner) { (language, count) ->
            binding.textTopOriginalLanguage.text = "🌐 lingua originale principale: $language ($count film)"
            binding.textTopOriginalLanguage.visibility = View.VISIBLE
            binding.textTopOriginalLanguage.setOnClickListener {
                showMoviesDialog("Film in $language", notificationsViewModel.getMoviesByOriginalLanguage(language))
            }
        }

        //grafico scatter popolarità vs rating
        notificationsViewModel.popularityVsRatingData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupPopularityVsRatingScatter(data)
            }
        }

        //grafico trend popolarità
        notificationsViewModel.popularityTrendData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupPopularityTrendLineChart(data)
            }
        }
    }

    //grafico generi - solo numeri senza etichette
    private fun setupGenresPieChart(data: Map<String, Int>) {
        val entries = data.map { PieEntry(it.value.toFloat(), "") }

        val dataSet = PieDataSet(entries, "").apply {
            colors = chartColors
            valueTextSize = 14f
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
            legend.apply {
                textSize = 10f
                isWordWrapEnabled = true
                setCustom(data.keys.mapIndexed { index, genre ->
                    com.github.mikephil.charting.components.LegendEntry().apply {
                        label = genre
                        formColor = chartColors[index % chartColors.size]
                    }
                })
            }
            setDrawEntryLabels(false)
            animateY(1000, Easing.EaseInOutQuad)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is PieEntry) {
                        val index = entries.indexOf(e)
                        val genre = data.keys.toList()[index]
                        val movies = notificationsViewModel.getMoviesByGenre(genre)
                        showMoviesDialog("Film $genre", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    //grafico anni
    private fun setupYearsBarChart(data: Map<Int, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#667eea")
            valueTextSize = 10f
            valueTextColor = Color.WHITE
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
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.map { it.toString() })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = data.size
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setDrawValueAboveBar(true)
            setFitBars(true)
            animateY(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val year = data.keys.toList()[e.x.toInt()]
                        val movies = notificationsViewModel.getMoviesByYear(year)
                        showMoviesDialog("Film del $year", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    //grafico registi
    private fun setupDirectorsBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#764ba2")
            valueTextSize = 10f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartDirectors.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(10f)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = data.size
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setDrawValueAboveBar(true)
            setFitBars(true)
            animateY(1000)

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

    //grafico attori
    private fun setupActorsBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#f093fb")
            valueTextSize = 10f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartActors.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(10f)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = data.size
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setDrawValueAboveBar(true)
            setFitBars(true)
            animateY(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val actor = data.keys.toList()[e.x.toInt()]
                        val movies = notificationsViewModel.getMoviesByActor(actor)
                        showMoviesDialog("Film con $actor", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    //grafico tmdb ratings - SOLO NUMERI come generi
    private fun setupTmdbRatingsPieChart(data: Map<String, Int>) {
        Log.d(TAG, "setupTmdbRatingsPieChart chiamato con data: $data")

        val orderedRatings = (1..10).map { it.toString() }
        val orderedData = orderedRatings.mapNotNull { rating ->
            data[rating]?.let { rating to it }
        }

        val entries = orderedData.map { (_, count) ->
            PieEntry(count.toFloat(), "")
        }

        Log.d(TAG, "entries create: ${entries.size}")

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
            legend.apply {
                textSize = 10f
                isWordWrapEnabled = true
                setCustom(orderedData.mapIndexed { index, (rating, _) ->
                    com.github.mikephil.charting.components.LegendEntry().apply {
                        label = "$rating⭐"
                        formColor = chartColors[index % chartColors.size]
                    }
                })
            }
            setDrawEntryLabels(false)
            animateY(1000, Easing.EaseInOutQuad)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is PieEntry) {
                        val index = entries.indexOf(e)
                        val rating = orderedData[index].first.toInt()
                        val movies = notificationsViewModel.getMoviesByTmdbRating(rating)
                        showMoviesDialog("Film valutati $rating⭐", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            Log.d(TAG, "chart tmdb ratings configurato")
            invalidate()
        }
    }

    //grafico paesi
    private fun setupCountriesBarChart(data: Map<String, Int>) {
        Log.d(TAG, "setupCountriesBarChart chiamato con data: $data")

        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#00f2fe")
            valueTextSize = 10f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartCountries.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(10f)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(data.keys.toList())
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = data.size
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setDrawValueAboveBar(true)
            setFitBars(true)
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

            Log.d(TAG, "chart countries configurato")
            invalidate()
        }
    }

    //grafico decenni
    private fun setupDecadesBarChart(data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#43e97b")
            valueTextSize = 10f
            valueTextColor = Color.WHITE
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
                labelCount = data.size
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setDrawValueAboveBar(true)
            setFitBars(true)
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

    //grafico combinazioni generi
    private fun setupGenreCombinationsChart(data: Map<Pair<String, String>, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val labels = data.keys.map { "${it.first} + ${it.second}" }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#38f9d7")
            valueTextSize = 10f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartGenreCombinations.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(10f)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = labels.size
                labelRotationAngle = -45f
                textSize = 8f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setDrawValueAboveBar(true)
            setFitBars(true)
            animateY(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val combo = data.keys.toList()[e.x.toInt()]
                        val movies = notificationsViewModel.getMoviesByGenreCombination(combo)
                        showMoviesDialog("Film ${combo.first} + ${combo.second}", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    //grafico fasce durata
    private fun setupRuntimeRangesChart(data: Map<String, Int>) {
        val orderedData = listOf("< 90 min", "90-120 min", "120-150 min", "> 150 min")
            .mapNotNull { range -> data[range]?.let { range to it } }

        val entries = orderedData.mapIndexed { index, (_, count) ->
            BarEntry(index.toFloat(), count.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#fa709a")
            valueTextSize = 10f
            valueTextColor = Color.WHITE
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartRuntimeRanges.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(orderedData.map { it.first })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = orderedData.size
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(value: Float): String {
                        return value.toInt().toString()
                    }
                }
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
            setDrawValueAboveBar(true)
            setFitBars(true)
            animateY(1000)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is BarEntry) {
                        val range = orderedData[e.x.toInt()].first
                        val movies = notificationsViewModel.getMoviesByRuntimeRange(range)
                        showMoviesDialog("Film $range", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    //grafico lingue originali - SOLO NUMERI come generi
    private fun setupOriginalLanguagesPieChart(data: Map<String, Int>) {
        Log.d(TAG, "setupOriginalLanguagesPieChart: data size = ${data.size}")

        val entries = data.map { PieEntry(it.value.toFloat(), "") }

        val dataSet = PieDataSet(entries, "").apply {
            colors = chartColors
            valueTextSize = 11f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        binding.chartOriginalLanguages.apply {
            this.data = PieData(dataSet)
            description.isEnabled = false
            legend.apply {
                textSize = 10f
                isWordWrapEnabled = true
                setCustom(data.keys.mapIndexed { index, language ->
                    com.github.mikephil.charting.components.LegendEntry().apply {
                        label = language
                        formColor = chartColors[index % chartColors.size]
                    }
                })
            }
            setDrawEntryLabels(false)
            animateY(1000, Easing.EaseInOutQuad)

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    if (e is PieEntry) {
                        val index = entries.indexOf(e)
                        val language = data.keys.toList()[index]
                        val movies = notificationsViewModel.getMoviesByOriginalLanguage(language)
                        showMoviesDialog("Film in $language", movies)
                    }
                }
                override fun onNothingSelected() {}
            })

            Log.d(TAG, "chart lingue originali configurato con ${entries.size} entries")
            invalidate()
        }
    }

    //scatter plot: film popolari vs film di qualità
    private fun setupPopularityVsRatingScatter(data: List<Pair<Double, Double>>) {
        Log.d(TAG, "setupPopularityVsRatingScatter: ${data.size} punti")

        val entries = data.map { (popularity, rating) ->
            Entry(popularity.toFloat(), rating.toFloat())
        }

        val dataSet = ScatterDataSet(entries, "Film").apply {
            setScatterShape(ScatterChart.ScatterShape.CIRCLE)
            scatterShapeSize = 10f
            color = Color.parseColor("#667eea")
            setDrawValues(false)
        }

        binding.chartPopularityVsRating.apply {
            this.data = ScatterData(dataSet)
            description.text = "Scopri se i film più popolari sono anche i migliori"
            description.textSize = 9f

            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                textSize = 9f
            }

            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 10f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.textSize = 10f

            animateXY(1000, 1000)
            invalidate()
        }
    }

    //line chart: trend popolarità per decade
    private fun setupPopularityTrendLineChart(data: Map<String, Double>) {
        Log.d(TAG, "setupPopularityTrendLineChart: $data")

        val sortedData = data.toList().sortedBy { it.first }
        val entries = sortedData.mapIndexed { index, (_, avgPopularity) ->
            Entry(index.toFloat(), avgPopularity.toFloat())
        }

        val dataSet = LineDataSet(entries, "Popolarità Media").apply {
            color = Color.parseColor("#667eea")
            setCircleColor(Color.parseColor("#667eea"))
            lineWidth = 3f
            circleRadius = 5f
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = Color.parseColor("#667eea")
            mode = LineDataSet.Mode.CUBIC_BEZIER
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return String.format("%.1f", value)
                }
            }
        }

        binding.chartPopularityTrend.apply {
            this.data = LineData(dataSet)
            description.text = "Come è cambiata la popolarità dei film nel tempo"
            description.textSize = 9f

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedData.map { it.first })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelCount = sortedData.size
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                textSize = 9f
            }

            axisRight.isEnabled = false
            legend.textSize = 10f

            animateX(1500)
            invalidate()
        }
    }

    //mostra dialog con lista film
    private fun showMoviesDialog(title: String, movies: List<com.example.movieapp.data.models.Movie>) {
        if (movies.isEmpty()) {
            Log.w(TAG, "showMoviesDialog chiamato con lista vuota per: $title")
            return
        }

        val movieTitles = movies.map { movie ->
            val year = movie.year?.let { " ($it)" } ?: ""
            val rating = movie.tmdbRating?.let { " - ⭐${String.format("%.1f", it)}" } ?: ""
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
        Log.d(TAG, "fragment resumed - dati statistiche ricaricati")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}