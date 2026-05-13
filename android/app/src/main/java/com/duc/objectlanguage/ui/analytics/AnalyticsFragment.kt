package com.duc.objectlanguage.ui.analytics

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.duc.objectlanguage.data.model.AnalyticsResponse
import com.duc.objectlanguage.databinding.FragmentAnalyticsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private var _binding: FragmentAnalyticsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AnalyticsViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[AnalyticsViewModel::class.java]
        setupObservers()
        viewModel.loadAnalytics()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.stats.observe(viewLifecycleOwner) { stats ->
            stats ?: return@observe
            binding.tvTotalWords.text   = "${stats.totalLearned}"
            binding.tvMasteredWords.text = "${stats.mastered}"
            binding.tvReviewsToday.text  = "${stats.dueToday}"
            binding.tvCurrentStreak.text = "${stats.currentStreak}"
        }

        viewModel.analytics.observe(viewLifecycleOwner) { data ->
            data ?: return@observe
            setupLineChart(data)
            setupPieChart(data)
            setupBarChart(data)
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let { viewModel.clearError() }
        }
    }

    // ── LineChart: ôn tập 7 ngày gần nhất ───────────────────────────────────
    private fun setupLineChart(data: AnalyticsResponse) {
        val dayLabels = getLast7DayLabels()           // ["yyyy-MM-dd", ...]
        val weeklyMap = data.weeklyReviews.associateBy { it.date }

        val entries = dayLabels.mapIndexed { idx, day ->
            Entry(idx.toFloat(), weeklyMap[day]?.count?.toFloat() ?: 0f)
        }

        val dataSet = LineDataSet(entries, "Lượt ôn").apply {
            color         = Color.parseColor("#6750A4")
            setCircleColor(Color.parseColor("#6750A4"))
            lineWidth     = 2f
            circleRadius  = 4f
            setDrawValues(false)
            mode          = LineDataSet.Mode.CUBIC_BEZIER
            fillAlpha     = 60
            fillColor     = Color.parseColor("#6750A4")
            setDrawFilled(true)
        }

        binding.lineChartProgress.apply {
            this.data = LineData(dataSet)
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(dayLabels.map { it.substring(5) }) // "MM-dd"
                position       = XAxis.XAxisPosition.BOTTOM
                granularity    = 1f
                setDrawGridLines(false)
                labelCount     = 7
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
            }
            axisRight.isEnabled    = false
            description.isEnabled  = false
            legend.isEnabled       = false
            animateXY(800, 600)
            invalidate()
        }
    }

    // ── PieChart: phân bố mức độ thành thạo ─────────────────────────────────
    private fun setupPieChart(data: AnalyticsResponse) {
        val m = data.mastery
        if (m.newCount + m.learning + m.mastered == 0) return

        val entries = buildList {
            if (m.newCount  > 0) add(PieEntry(m.newCount.toFloat(),  "Mới"))
            if (m.learning  > 0) add(PieEntry(m.learning.toFloat(),  "Đang học"))
            if (m.mastered  > 0) add(PieEntry(m.mastered.toFloat(),  "Thành thạo"))
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = listOf(
                Color.parseColor("#90CAF9"),   // Mới       — xanh nhạt
                Color.parseColor("#FFCC80"),   // Đang học  — cam nhạt
                Color.parseColor("#A5D6A7"),   // Thành thạo — xanh lá nhạt
            )
            sliceSpace    = 2f
            valueTextSize = 12f
            valueTextColor = Color.DKGRAY
        }

        binding.pieChartMastery.apply {
            this.data = PieData(dataSet).also { it.setValueFormatter(
                com.github.mikephil.charting.formatter.PercentFormatter(this)
            ) }
            setUsePercentValues(true)
            description.isEnabled  = false
            isDrawHoleEnabled      = true
            holeRadius             = 38f
            setHoleColor(Color.TRANSPARENT)
            setCenterText("Từ vựng")
            setCenterTextSize(13f)
            legend.isEnabled       = true
            animateY(900)
            invalidate()
        }
    }

    // ── BarChart: hoạt động mỗi ngày ────────────────────────────────────────
    private fun setupBarChart(data: AnalyticsResponse) {
        val dayLabels = getLast7DayLabels()
        val weeklyMap = data.weeklyReviews.associateBy { it.date }

        val entries = dayLabels.mapIndexed { idx, day ->
            BarEntry(idx.toFloat(), weeklyMap[day]?.count?.toFloat() ?: 0f)
        }

        val dataSet = BarDataSet(entries, "Ôn tập mỗi ngày").apply {
            color = Color.parseColor("#4DB6AC")
            setDrawValues(false)
        }

        binding.barChartActivity.apply {
            this.data = BarData(dataSet).also { it.barWidth = 0.55f }
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(dayLabels.map { it.substring(5) })
                position       = XAxis.XAxisPosition.BOTTOM
                granularity    = 1f
                setDrawGridLines(false)
            }
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
            }
            axisRight.isEnabled   = false
            description.isEnabled = false
            legend.isEnabled      = false
            animateY(700)
            invalidate()
        }
    }

    // ── Helper: danh sách 7 ngày gần nhất dạng "yyyy-MM-dd" ─────────────────
    private fun getLast7DayLabels(): List<String> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        return (6 downTo 0).map { daysAgo ->
            cal.time = Date()
            cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
            sdf.format(cal.time)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
