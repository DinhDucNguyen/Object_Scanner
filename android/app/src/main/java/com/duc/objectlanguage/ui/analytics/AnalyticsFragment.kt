package com.duc.objectlanguage.ui.analytics

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate

class AnalyticsFragment : Fragment() {

    private lateinit var viewModel: AnalyticsViewModel
    private lateinit var cardProgress: CardView
    private lateinit var lineChartProgress: LineChart
    private lateinit var cardMastery: CardView
    private lateinit var pieChartMastery: PieChart
    private lateinit var cardActivity: CardView
    private lateinit var barChartActivity: BarChart
    private lateinit var cardStats: CardView
    private lateinit var tvTotalWords: TextView
    private lateinit var tvMasteredWords: TextView
    private lateinit var tvReviewsToday: TextView
    private lateinit var tvCurrentStreak: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_analytics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]

        initViews(view)
        setupCharts()
        setupObservers()

        viewModel.loadAnalytics()
    }

    private fun initViews(view: View) {
        cardProgress = view.findViewById(R.id.cardProgress)
        lineChartProgress = view.findViewById(R.id.lineChartProgress)
        cardMastery = view.findViewById(R.id.cardMastery)
        pieChartMastery = view.findViewById(R.id.pieChartMastery)
        cardActivity = view.findViewById(R.id.cardActivity)
        barChartActivity = view.findViewById(R.id.barChartActivity)
        cardStats = view.findViewById(R.id.cardStats)
        tvTotalWords = view.findViewById(R.id.tvTotalWords)
        tvMasteredWords = view.findViewById(R.id.tvMasteredWords)
        tvReviewsToday = view.findViewById(R.id.tvReviewsToday)
        tvCurrentStreak = view.findViewById(R.id.tvCurrentStreak)
        progressBar = view.findViewById(R.id.progressBar)
    }

    private fun setupCharts() {
        // Line Chart - Progress over time
        lineChartProgress.apply {
            description = Description().apply { text = "" }
            setDrawGridBackground(false)
            setTouchEnabled(true)
            setPinchZoom(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
            }
            axisRight.isEnabled = false
            legend.isEnabled = true
        }

        // Pie Chart - Mastery distribution
        pieChartMastery.apply {
            description = Description().apply { text = "" }
            setUsePercentValues(true)
            setDrawHoleEnabled(true)
            setHoleColor(android.R.color.white)
            setTransparentCircleRadius(58f)
            setDrawEntryLabels(true)
            legend.isEnabled = true
        }

        // Bar Chart - Daily activity
        barChartActivity.apply {
            description = Description().apply { text = "" }
            setDrawGridBackground(false)
            setTouchEnabled(true)
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
            }
            axisRight.isEnabled = false
            legend.isEnabled = false
        }
    }

    private fun setupObservers() {
        viewModel.progressData.observe(viewLifecycleOwner) { data ->
            updateLineChart(data)
        }

        viewModel.masteryData.observe(viewLifecycleOwner) { data ->
            updatePieChart(data)
        }

        viewModel.activityData.observe(viewLifecycleOwner) { data ->
            updateBarChart(data)
        }

        viewModel.statsData.observe(viewLifecycleOwner) { stats ->
            tvTotalWords.text = "${stats.totalWords}"
            tvMasteredWords.text = "${stats.masteredWords}"
            tvReviewsToday.text = "${stats.reviewsToday}"
            tvCurrentStreak.text = "${stats.currentStreak} days"
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                showError(it)
                viewModel.clearError()
            }
        }
    }

    private fun updateLineChart(data: List<ProgressDataPoint>) {
        if (data.isEmpty()) {
            lineChartProgress.clear()
            return
        }

        val entries = data.mapIndexed { index, point ->
            Entry(index.toFloat(), point.wordsLearned.toFloat())
        }

        val dataSet = LineDataSet(entries, "Words Learned").apply {
            color = resources.getColor(android.R.color.holo_blue_bright, null)
            setCircleColor(resources.getColor(android.R.color.holo_blue_dark, null))
            lineWidth = 2f
            circleRadius = 4f
            setDrawValues(true)
            valueTextSize = 10f
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        lineChartProgress.data = LineData(dataSet)
        lineChartProgress.xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.date })
        lineChartProgress.invalidate()
    }

    private fun updatePieChart(data: MasteryDistribution) {
        val entries = mutableListOf<PieEntry>()
        
        if (data.mastered > 0) entries.add(PieEntry(data.mastered.toFloat(), "Mastered"))
        if (data.learning > 0) entries.add(PieEntry(data.learning.toFloat(), "Learning"))
        if (data.newWords > 0) entries.add(PieEntry(data.newWords.toFloat(), "New"))
        if (data.difficult > 0) entries.add(PieEntry(data.difficult.toFloat(), "Difficult"))

        if (entries.isEmpty()) {
            pieChartMastery.clear()
            return
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                android.R.color.holo_green_light,
                android.R.color.holo_blue_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light
            ).map { resources.getColor(it, null) }
            valueTextSize = 12f
            valueTextColor = android.R.color.black
        }

        pieChartMastery.data = PieData(dataSet).apply {
            setValueFormatter(PercentFormatter(pieChartMastery))
        }
        pieChartMastery.invalidate()
    }

    private fun updateBarChart(data: List<ActivityDay>) {
        if (data.isEmpty()) {
            barChartActivity.clear()
            return
        }

        val entries = data.mapIndexed { index, day ->
            BarEntry(index.toFloat(), day.reviewCount.toFloat())
        }

        val dataSet = BarDataSet(entries, "Reviews").apply {
            color = resources.getColor(android.R.color.holo_blue_light, null)
            valueTextSize = 10f
            valueTextColor = android.R.color.black
        }

        barChartActivity.data = BarData(dataSet)
        barChartActivity.xAxis.valueFormatter = IndexAxisValueFormatter(data.map { it.dayName })
        barChartActivity.invalidate()
    }

    private fun showError(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
