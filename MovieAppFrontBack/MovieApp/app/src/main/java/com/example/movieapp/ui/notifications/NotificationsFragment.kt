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

    //colori originali del progetto
    private val chartColors = listOf(
        Color.parseColor("#2196F3"), Color.parseColor("#4CAF50"), Color.parseColor("#FFC107"),
        Color.parseColor("#FF5722"), Color.parseColor("#9C27B0"), Color.parseColor("#00BCD4"),
        Color.parseColor("#FF9800"), Color.parseColor("#E91E63"), Color.parseColor("#8BC34A"),
        Color.parseColor("#03A9F4"), Color.parseColor("#CDDC39"), Color.parseColor("#FF6F00"),
        Color.parseColor("#673AB7"), Color.parseColor("#009688"), Color.parseColor("#FFEB3B"),
        Color.parseColor("#795548"), Color.parseColor("#607D8B"), Color.parseColor("#F44336"),
        Color.parseColor("#3F51B5"), Color.parseColor("#FFCA28")
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
                binding.statsCard.visibility = View.GONE
                binding.cardCharts.visibility = View.GONE
                binding.cardChartsLoading.visibility = View.GONE
                Log.d(TAG, "nessun film, mostra messaggio")
            } else {
                binding.textNoData.visibility = View.GONE
                binding.statsCard.visibility = View.VISIBLE
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
                showMoviesDialogSimple("Film $genre", notificationsViewModel.getMoviesByGenre(genre))
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
                showMoviesDialogSimple("Film del $year", notificationsViewModel.getMoviesByYear(year))
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
                val lastName = director.split(" ").last()
                showMoviesDialogSimple("Film di $director", notificationsViewModel.getMoviesByDirector(lastName))
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
                val lastName = actor.split(" ").last()
                showMoviesDialogSimple("Film con $actor", notificationsViewModel.getMoviesByActor(lastName))
            }
        }

        //grafico rating tmdb
        notificationsViewModel.tmdbRatingsData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupTmdbRatingsBarChart(data)
            }
        }

        //grafico paesi
        notificationsViewModel.countriesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                setupCountriesBarChart(data)
                binding.textCountriesEmpty.visibility = View.GONE
                binding.chartCountries.visibility = View.VISIBLE
            } else {
                binding.textCountriesEmpty.visibility = View.VISIBLE
                binding.chartCountries.visibility = View.GONE
            }
        }

        notificationsViewModel.topCountry.observe(viewLifecycleOwner) { (country, count) ->
            binding.textTopCountry.text = "🌍 paese principale: $country ($count film)"
            binding.textTopCountry.visibility = View.VISIBLE
            binding.textTopCountry.setOnClickListener {
                showMoviesDialogSimple("Film da $country", notificationsViewModel.getMoviesByCountry(country))
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
                showMoviesDialogSimple("Film degli anni $decade", notificationsViewModel.getMoviesByDecade(decade))
            }
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
                showMoviesDialogSimple("Film ${combo.first} + ${combo.second}",
                    notificationsViewModel.getMoviesByGenreCombination(combo))
            }
        }

        //grafico range runtime
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
                    showMoviesDialogWithRuntime("Film più lunghi", movies)
                }
            }
        }

        //grafico lingue originali
        notificationsViewModel.originalLanguagesData.observe(viewLifecycleOwner) { data ->
            if (data.isNotEmpty()) {
                binding.textOriginalLanguagesEmpty.visibility = View.GONE
                binding.chartOriginalLanguages.visibility = View.VISIBLE
                setupOriginalLanguagesPieChart(data)
            } else {
                binding.textOriginalLanguagesEmpty.visibility = View.VISIBLE
                binding.chartOriginalLanguages.visibility = View.GONE
            }
        }

        notificationsViewModel.topOriginalLanguage.observe(viewLifecycleOwner) { (language, count) ->
            binding.textTopOriginalLanguage.text = "🌐 lingua originale principale: $language ($count film)"
            binding.textTopOriginalLanguage.visibility = View.VISIBLE
            binding.textTopOriginalLanguage.setOnClickListener {
                showMoviesDialogSimple("Film in $language", notificationsViewModel.getMoviesByOriginalLanguage(language))
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

        notificationsViewModel.mostPopularDecade.observe(viewLifecycleOwner) { stat ->
            binding.textMostPopularDecade.text = stat
            binding.textMostPopularDecade.visibility = View.VISIBLE
        }
    }

    //grafico generi - SOLO NUMERI con legenda completa
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
                        showMoviesDialogSimple("Film $genre", notificationsViewModel.getMoviesByGenre(genre))
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    //grafico anni - tutti gli anni visibili, formato corretto, no valori
    private fun setupYearsBarChart(data: Map<Int, Int>) {
        val sortedData = data.toSortedMap()
        val entries = sortedData.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#667eea")
            valueTextSize = 0f
            setDrawValues(false)
        }

        binding.chartYears.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(10f)
            moveViewToX(entries.size.toFloat() - 10f)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedData.keys.map { it.toString() })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 9f
                setDrawGridLines(false)
                setLabelCount(sortedData.size, false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val year = sortedData.keys.toList()[it.x.toInt()]
                        showMoviesDialogSimple("Film del $year", notificationsViewModel.getMoviesByYear(year))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico registi - con rotazione etichette ridotta per maggiore leggibilità
    private fun setupDirectorsBarChart(data: Map<String, Int>) {
        val sortedDirectors = data.entries.sortedByDescending { it.value }
        val entries = sortedDirectors.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#764ba2")
            valueTextSize = 0f
            setDrawValues(false)
        }

        binding.chartDirectors.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(8f)
            moveViewToX(0f)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedDirectors.map { it.key })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -30f
                textSize = 10f
                setDrawGridLines(false)
                setLabelCount(sortedDirectors.size, false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        val directorLastName = sortedDirectors[index].key
                        val fullName = notificationsViewModel.getDirectorFullName(directorLastName)
                        showMoviesDialogSimple("Film di $fullName",
                            notificationsViewModel.getMoviesByDirector(directorLastName))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico attori - con rotazione etichette ridotta per maggiore leggibilità
    private fun setupActorsBarChart(data: Map<String, Int>) {
        val sortedActors = data.entries.sortedByDescending { it.value }
        val entries = sortedActors.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#43e97b")
            valueTextSize = 0f
            setDrawValues(false)
        }

        binding.chartActors.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(8f)
            moveViewToX(0f)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedActors.map { it.key })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -30f
                textSize = 10f
                setDrawGridLines(false)
                setLabelCount(sortedActors.size, false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        val actorLastName = sortedActors[index].key
                        val fullName = notificationsViewModel.getActorFullName(actorLastName)
                        showMoviesDialogSimple("Film con $fullName",
                            notificationsViewModel.getMoviesByActor(actorLastName))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico valutazioni tmdb - tutti i voti visibili 0-10, no valori
    private fun setupTmdbRatingsBarChart(data: Map<Int, Int>) {
        val sortedRatings = (0..10).toList()
        val entries = sortedRatings.mapIndexed { index, rating ->
            BarEntry(index.toFloat(), (data[rating] ?: 0).toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#F44336"), Color.parseColor("#E91E63"),
                Color.parseColor("#FF5722"), Color.parseColor("#FF9800"),
                Color.parseColor("#FFC107"), Color.parseColor("#FFEB3B"),
                Color.parseColor("#CDDC39"), Color.parseColor("#8BC34A"),
                Color.parseColor("#4CAF50"), Color.parseColor("#009688"),
                Color.parseColor("#00BCD4")
            )
            valueTextSize = 0f
            setDrawValues(false)
        }

        binding.chartTmdbRatings.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedRatings.map { it.toString() })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 10f
                setDrawGridLines(false)
                setLabelCount(sortedRatings.size, false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val rating = it.x.toInt()
                        showMoviesDialogWithRating("Film con rating $rating",
                            notificationsViewModel.getMoviesByRating(rating))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico paesi - tutti con scroll, no valori
    private fun setupCountriesBarChart(data: Map<String, Int>) {
        val sortedCountries = data.entries.sortedByDescending { it.value }
        val entries = sortedCountries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#4facfe")
            valueTextSize = 0f
            setDrawValues(false)
        }

        binding.chartCountries.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(10f)
            moveViewToX(0f)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedCountries.map { it.key })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -45f
                textSize = 8f
                setDrawGridLines(false)
                setLabelCount(sortedCountries.size, false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        val country = sortedCountries[index].key
                        showMoviesDialogSimple("Film da $country",
                            notificationsViewModel.getMoviesByCountry(country))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico decenni - tutti con scroll, no valori
    private fun setupDecadesBarChart(data: Map<String, Int>) {
        val sortedDecades = data.keys.sortedBy { it.replace("s", "").toIntOrNull() ?: 0 }
        val entries = sortedDecades.mapIndexed { index, decade ->
            BarEntry(index.toFloat(), (data[decade] ?: 0).toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#fa709a")
            valueTextSize = 0f
            setDrawValues(false)
        }

        binding.chartDecades.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(10f)
            moveViewToX(0f)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedDecades)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 9f
                setDrawGridLines(false)
                setLabelCount(sortedDecades.size, false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        val decade = sortedDecades[index]
                        showMoviesDialogSimple("Film degli anni $decade",
                            notificationsViewModel.getMoviesByDecade(decade))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico combinazioni generi - con acronimi per leggibilità
    private fun setupGenreCombinationsChart(data: Map<Pair<String, String>, Int>) {
        val sortedCombos = data.entries.sortedByDescending { it.value }
        val entries = sortedCombos.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#f093fb")
            valueTextSize = 0f
            setDrawValues(false)
        }

        //funzione per creare acronimi
        fun getAcronym(genre: String): String {
            return when (genre.lowercase()) {
                "action" -> "AC"
                "adventure" -> "AV"
                "animation" -> "AN"
                "comedy" -> "CO"
                "crime" -> "CR"
                "documentary" -> "DO"
                "drama" -> "DR"
                "family" -> "FA"
                "fantasy" -> "FN"
                "history" -> "HI"
                "horror" -> "HO"
                "music" -> "MU"
                "mystery" -> "MY"
                "romance" -> "RO"
                "science fiction" -> "SF"
                "thriller" -> "TH"
                "war" -> "WA"
                "western" -> "WE"
                "tv movie" -> "TV"
                else -> genre.take(2).uppercase()
            }
        }

        val labels = sortedCombos.map { "${getAcronym(it.key.first)}+${getAcronym(it.key.second)}" }

        binding.chartGenreCombinations.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            setVisibleXRangeMaximum(8f)
            moveViewToX(0f)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                labelRotationAngle = -30f
                textSize = 9f
                setDrawGridLines(false)
                setLabelCount(labels.size, false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        val combo = sortedCombos[index].key
                        //mostra nomi completi nel dialog
                        showMoviesDialogSimple("Film ${combo.first} + ${combo.second}",
                            notificationsViewModel.getMoviesByGenreCombination(combo))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico durata film - no valori
    private fun setupRuntimeRangesChart(data: Map<String, Int>) {
        val sortedRanges = listOf("0-60", "60-90", "90-120", "120-150", "150-180", "180+")
        val entries = sortedRanges.mapIndexed { index, range ->
            BarEntry(index.toFloat(), (data[range] ?: 0).toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#30cfd0")
            valueTextSize = 0f
            setDrawValues(false)
        }

        binding.chartRuntimeRanges.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedRanges)
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 9f
                setDrawGridLines(false)
            }

            axisLeft.apply {
                granularity = 1f
                textSize = 9f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        val index = it.x.toInt()
                        val range = sortedRanges[index]
                        showMoviesDialogWithRuntime("Film durata $range min",
                            notificationsViewModel.getMoviesByRuntimeRange(range))
                    }
                }
                override fun onNothingSelected() {}
            })

            animateY(1000)
            invalidate()
        }
    }

    //grafico lingue originali - SOLO NUMERI con legenda completa
    private fun setupOriginalLanguagesPieChart(data: Map<String, Int>) {
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
                        showMoviesDialogSimple("Film in $language",
                            notificationsViewModel.getMoviesByOriginalLanguage(language))
                    }
                }
                override fun onNothingSelected() {}
            })

            invalidate()
        }
    }

    //scatter plot: film popolari vs film di qualità
    private fun setupPopularityVsRatingScatter(data: List<Pair<Double, Double>>) {
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
            description.isEnabled = false

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
            legend.isEnabled = false

            animateXY(1000, 1000)
            invalidate()
        }
    }

    //line chart: trend popolarità per decade
    private fun setupPopularityTrendLineChart(data: Map<String, Double>) {
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
            description.isEnabled = false

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(sortedData.map { it.first })
                position = XAxis.XAxisPosition.BOTTOM
                granularity = 1f
                textSize = 9f
                labelRotationAngle = -45f
                setDrawGridLines(false)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                gridColor = Color.parseColor("#E0E0E0")
                textSize = 9f
            }

            axisRight.isEnabled = false
            legend.isEnabled = false

            animateX(1000)
            invalidate()
        }
    }

    //dialog semplice: nome - anno
    private fun showMoviesDialogSimple(title: String, movies: List<com.example.movieapp.data.models.Movie>) {
        if (movies.isEmpty()) {
            Log.w(TAG, "showMoviesDialog chiamato con lista vuota per: $title")
            return
        }

        val movieTitles = movies.map { movie ->
            val year = movie.year?.let { " - $it" } ?: ""
            "${movie.title}$year"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("$title - ${movies.size} film")
            .setItems(movieTitles, null)
            .setPositiveButton("chiudi", null)
            .show()
    }

    //dialog con rating: nome - anno - rating
    private fun showMoviesDialogWithRating(title: String, movies: List<com.example.movieapp.data.models.Movie>) {
        if (movies.isEmpty()) {
            Log.w(TAG, "showMoviesDialog chiamato con lista vuota per: $title")
            return
        }

        val movieTitles = movies.map { movie ->
            val year = movie.year?.let { " - $it" } ?: ""
            val rating = movie.tmdbRating?.let { " - ⭐${String.format("%.1f", it)}" } ?: ""
            "${movie.title}$year$rating"
        }.toTypedArray()

        AlertDialog.Builder(requireContext())
            .setTitle("$title - ${movies.size} film")
            .setItems(movieTitles, null)
            .setPositiveButton("chiudi", null)
            .show()
    }

    //dialog con durata: nome - anno - durata con emoji
    private fun showMoviesDialogWithRuntime(title: String, movies: List<com.example.movieapp.data.models.Movie>) {
        if (movies.isEmpty()) {
            Log.w(TAG, "showMoviesDialog chiamato con lista vuota per: $title")
            return
        }

        val movieTitles = movies.map { movie ->
            val year = movie.year?.let { " - $it" } ?: ""
            val runtime = movie.runtime?.let { " - ⏱️ ${it} min" } ?: ""
            "${movie.title}$year$runtime"
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