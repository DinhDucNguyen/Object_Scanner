package com.duc.objectlanguage.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.AnalyticsResponse
import com.duc.objectlanguage.data.model.StatsResponse
import com.duc.objectlanguage.data.model.StreakResponse
import com.duc.objectlanguage.databinding.FragmentAnalyticsBinding
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AnalyticsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prepareEmptyCharts()
        setupObservers()
        animateEntrance()
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAnalytics()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            stats ?: return@observe
            bindStats(stats)
        }

        viewModel.streak.observe(viewLifecycleOwner) { streak ->
            streak ?: return@observe
            bindStreak(streak)
        }

        viewModel.analytics.observe(viewLifecycleOwner) { data ->
            data ?: return@observe
            setupLineChart(data)
            setupBarChart(data)
            setupPieChart(data)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (!error.isNullOrBlank()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }
    }

    private fun bindStats(stats: StatsResponse) {
        binding.tvTotalWords.text = stats.totalLearned.toString()
        binding.tvDueToday.text = stats.dueToday.toString()
        binding.tvMasteredWords.text = stats.mastered.toString()
        binding.tvCurrentStreak.text = stats.currentStreak.toString()
        binding.tvLongestStreak.text = stats.longestStreak.toString()
        binding.tvTotalReviews.text = stats.totalReviews.toString()

        val hasLearningData = stats.totalLearned > 0 || stats.totalReviews > 0
        binding.cardAnalyticsEmpty.visibility = if (hasLearningData) View.GONE else View.VISIBLE
        val chartVisibility = if (hasLearningData) View.VISIBLE else View.GONE
        binding.cardProgress.visibility = chartVisibility
        binding.cardActivity.visibility = chartVisibility
        binding.cardMastery.visibility = chartVisibility
    }

    private fun bindStreak(streak: StreakResponse) {
        binding.tvCurrentStreak.text = streak.streakHienTai.toString()
        binding.tvLongestStreak.text = streak.streakDaiNhat.toString()
        binding.tvReviewsToday.text = streak.luotOnHomNay.toString()
        binding.tvTotalReviews.text = streak.tongLuotOn.toString()
    }

    private fun animateEntrance() {
        val interp = DecelerateInterpolator(1.6f)
        binding.contentAnalytics.apply {
            alpha = 0f
            translationY = 28f
            animate().alpha(1f).translationY(0f).setDuration(340).setInterpolator(interp).start()
        }
    }

    private fun prepareEmptyCharts() {
        val noData = getString(R.string.analytics_no_chart_data)
        listOf(binding.lineChartProgress, binding.barChartActivity, binding.pieChartMastery).forEach { chart ->
            chart.setNoDataText(noData)
            chart.setNoDataTextColor(color(R.color.text_secondary))
        }
    }

    private fun setupLineChart(data: AnalyticsResponse) {
        val dayLabels = getLast7DayLabels()
        val dailyCounts = getDailyReviewCounts(data, dayLabels)
        var runningTotal = 0
        val entries = dailyCounts.mapIndexed { index, count ->
            runningTotal += count
            Entry(index.toFloat(), runningTotal.toFloat())
        }

        if (runningTotal == 0) {
            binding.lineChartProgress.showNoData()
            return
        }

        val dataSet = LineDataSet(entries, "").apply {
            color = color(R.color.primary)
            setCircleColor(color(R.color.primary))
            lineWidth = 2.4f
            circleRadius = 4f
            setDrawValues(true)
            valueTextColor = color(R.color.text_secondary)
            valueTextSize = 10f
            valueFormatter = integerValueFormatter(showZero = false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillAlpha = 45
            fillColor = color(R.color.primary)
            setDrawFilled(true)
        }

        binding.lineChartProgress.applyLineChart(dayLabels.map { it.substring(5) })
        binding.lineChartProgress.axisLeft.axisMaximum = (runningTotal + 1).toFloat()
        binding.lineChartProgress.data = LineData(dataSet)
        binding.lineChartProgress.animateXY(700, 500)
        binding.lineChartProgress.invalidate()
    }

    private fun setupBarChart(data: AnalyticsResponse) {
        val dayLabels = getLast7DayLabels()
        val dailyCounts = getDailyReviewCounts(data, dayLabels)
        val entries = dailyCounts.mapIndexed { index, count ->
            BarEntry(index.toFloat(), count.toFloat())
        }

        val maxCount = dailyCounts.maxOrNull() ?: 0
        if (maxCount == 0) {
            binding.barChartActivity.showNoData()
            return
        }

        val dataSet = BarDataSet(entries, "").apply {
            color = color(R.color.streak_heat_2)
            setDrawValues(true)
            valueTextColor = color(R.color.text_secondary)
            valueTextSize = 10f
            valueFormatter = integerValueFormatter(showZero = false)
        }

        binding.barChartActivity.applyBarChart(dayLabels.map { it.substring(5) })
        binding.barChartActivity.axisLeft.axisMaximum = (maxCount + 1).toFloat()
        binding.barChartActivity.data = BarData(dataSet).also { it.barWidth = 0.55f }
        binding.barChartActivity.animateY(650)
        binding.barChartActivity.invalidate()
    }

    private fun setupPieChart(data: AnalyticsResponse) {
        val mastery = data.mastery
        val total = mastery.newCount + mastery.learning + mastery.mastered
        if (total == 0) {
            binding.pieChartMastery.showNoData()
            return
        }

        val entries = buildList {
            if (mastery.newCount > 0) add(PieEntry(mastery.newCount.toFloat(), getString(R.string.ci_not_started_label)))
            if (mastery.learning > 0) add(PieEntry(mastery.learning.toFloat(), getString(R.string.ci_reviewing_label)))
            if (mastery.mastered > 0) add(PieEntry(mastery.mastered.toFloat(), getString(R.string.ci_mastered_label)))
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                color(R.color.streak_idle),
                color(R.color.streak_heat_1),
                color(R.color.streak_heat_4)
            )
            sliceSpace = 2f
            valueTextSize = 12f
            valueTextColor = color(R.color.text_primary)
        }

        binding.pieChartMastery.apply {
            setUsePercentValues(true)
            description.isEnabled = false
            setDrawEntryLabels(false)
            isDrawHoleEnabled = true
            holeRadius = 44f
            setHoleColor(Color.TRANSPARENT)
            setCenterText(total.toString())
            setCenterTextColor(color(R.color.text_secondary))
            setCenterTextSize(18f)
            legend.apply {
                isEnabled = true
                textColor = color(R.color.text_secondary)
                textSize = 12f
                form = Legend.LegendForm.CIRCLE
                formSize = 9f
                verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                orientation = Legend.LegendOrientation.HORIZONTAL
                setDrawInside(false)
            }
            this.data = PieData(dataSet).also { chartData ->
                chartData.setValueFormatter(PercentFormatter(this))
            }
            animateY(750)
            invalidate()
        }
    }

    private fun LineChart.applyLineChart(labels: List<String>) {
        xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            labelCount = labels.size
            textColor = color(R.color.text_secondary)
            textSize = 10f
            setDrawGridLines(false)
            axisMinimum = -0.2f
            axisMaximum = labels.size - 0.8f
        }
        axisLeft.apply {
            axisMinimum = 0f
            granularity = 1f
            textColor = color(R.color.text_secondary)
            textSize = 10f
            gridColor = color(R.color.divider)
            valueFormatter = integerValueFormatter(showZero = true)
        }
        axisRight.isEnabled = false
        description.isEnabled = false
        legend.isEnabled = false
        setScaleEnabled(false)
        setPinchZoom(false)
        setTouchEnabled(false)
        extraBottomOffset = 6f
    }

    private fun BarChart.applyBarChart(labels: List<String>) {
        xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            labelCount = labels.size
            textColor = color(R.color.text_secondary)
            textSize = 10f
            setDrawGridLines(false)
            axisMinimum = -0.5f
            axisMaximum = labels.size - 0.5f
        }
        axisLeft.apply {
            axisMinimum = 0f
            granularity = 1f
            textColor = color(R.color.text_secondary)
            textSize = 10f
            gridColor = color(R.color.divider)
            valueFormatter = integerValueFormatter(showZero = true)
        }
        axisRight.isEnabled = false
        description.isEnabled = false
        legend.isEnabled = false
        setScaleEnabled(false)
        setPinchZoom(false)
        setTouchEnabled(false)
        extraBottomOffset = 6f
    }

    private fun LineChart.showNoData() {
        clear()
        invalidate()
    }

    private fun BarChart.showNoData() {
        clear()
        invalidate()
    }

    private fun PieChart.showNoData() {
        clear()
        invalidate()
    }

    private fun getLast7DayLabels(): List<String> {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val calendar = Calendar.getInstance()
        return (6 downTo 0).map { daysAgo ->
            calendar.time = Date()
            calendar.add(Calendar.DAY_OF_YEAR, -daysAgo)
            formatter.format(calendar.time)
        }
    }

    private fun getDailyReviewCounts(data: AnalyticsResponse, dayLabels: List<String>): List<Int> {
        val weeklyMap = data.weeklyReviews.associateBy { it.date }
        return dayLabels.map { day -> weeklyMap[day]?.count ?: 0 }
    }

    private fun integerValueFormatter(showZero: Boolean): ValueFormatter {
        return object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                if (!showZero && value == 0f) return ""
                return value.toInt().toString()
            }
        }
    }

    private fun color(resId: Int): Int = resources.getColor(resId, null)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
