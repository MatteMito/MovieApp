package com.example.movieapp.ui.charts

import android.graphics.Color
import com.example.movieapp.data.models.Movie
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Manager per configurare grafici MPAndroidChart
 *
 * Nota: Questo file è mantenuto per compatibilità,
 * ma i grafici sono configurati direttamente in NotificationsFragment
 * per avere più controllo sulle animazioni e interazioni
 */
object ChartManager {

    fun setupPieChart(chart: PieChart, data: Map<String, Int>) {
        val entries = data.map { (label, value) ->
            PieEntry(value.toFloat(), label)
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = getDefaultColors()
            valueTextSize = 12f
            valueTextColor = Color.WHITE
            sliceSpace = 3f
        }

        chart.apply {
            this.data = PieData(dataSet)
            description.isEnabled = false
            isDrawHoleEnabled = true
            setDrawEntryLabels(true)
            animateY(1000)
            invalidate()
        }
    }

    fun setupBarChart(chart: BarChart, data: Map<String, Int>) {
        val entries = data.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = Color.parseColor("#2196F3")
            valueTextSize = 10f
        }

        chart.apply {
            this.data = BarData(dataSet)
            description.isEnabled = false
            animateY(1000)
            invalidate()
        }
    }

    private fun getDefaultColors(): List<Int> {
        return listOf(
            Color.parseColor("#2196F3"),
            Color.parseColor("#4CAF50"),
            Color.parseColor("#FFC107"),
            Color.parseColor("#FF5722"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#00BCD4"),
            Color.parseColor("#FF9800"),
            Color.parseColor("#E91E63")
        )
    }
}