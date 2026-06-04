package com.duc.objectlanguage.ui.collection

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.duc.objectlanguage.R
import com.duc.objectlanguage.databinding.FragmentCollectionInsightsBinding
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*

/**
 * Fragment showing collection analytics and smart suggestions
 * Features: Per-collection stats, progress visualization, AI suggestions
 */
class CollectionInsightsFragment : Fragment() {
    
    private var _binding: FragmentCollectionInsightsBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: CollectionInsightsViewModel by viewModels()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCollectionInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Đọc collectionId được truyền từ CollectionListFragment
        val initialCollectionId = arguments?.getInt("collectionId", 0) ?: 0
        if (initialCollectionId > 0) viewModel.setInitialCollection(initialCollectionId)
        setupCollectionSpinner()
        observeViewModel()
    }
    
    private fun setupCollectionSpinner() {
        viewModel.collections.observe(viewLifecycleOwner) { collections ->
            val collectionNames = collections.map { it.name }
            val adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                collectionNames
            )
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.collectionSpinner.adapter = adapter

            // Scroll Spinner đến đúng collection đang được chọn
            val selectedId = viewModel.selectedCollectionId.value
            if (selectedId != null) {
                val pos = collections.indexOfFirst { it.id == selectedId }
                if (pos >= 0) binding.collectionSpinner.setSelection(pos, false)
            }

            binding.collectionSpinner.setOnItemSelectedListener(
                object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: android.widget.AdapterView<*>?,
                        view: View?, position: Int, id: Long
                    ) {
                        viewModel.selectCollection(collections[position].id)
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
            )
        }
    }
    
    private fun observeViewModel() {
        // Insights data
        viewModel.insights.observe(viewLifecycleOwner) { insights ->
            insights?.let {
                updateStats(insights)
                updateCharts(insights)
            }
        }
        
        // Smart suggestions
        viewModel.suggestions.observe(viewLifecycleOwner) { suggestions ->
            displaySuggestions(suggestions)
        }
        
        // Loading state
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
        
        // Error messages
        viewModel.error.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }
    
    private fun updateStats(insights: com.duc.objectlanguage.data.model.CollectionInsights) {
        binding.totalItemsText.text = insights.totalItems.toString()
        binding.reviewedItemsText.text = insights.reviewedItems.toString()
        binding.masteredItemsText.text = insights.masteredItems.toString()
        binding.successRateText.text = getString(R.string.format_percent_int, (insights.successRate * 100).toInt())
        binding.avgQualityText.text = String.format("%.1f", insights.averageQuality)
        binding.totalReviewsText.text = insights.totalReviews.toString()
    }
    
    private fun updateCharts(insights: com.duc.objectlanguage.data.model.CollectionInsights) {
        // Progress Pie Chart
        setupProgressPieChart(insights)
        
        // Mastery Bar Chart
        setupMasteryBarChart(insights)
    }
    
    private fun setupProgressPieChart(insights: com.duc.objectlanguage.data.model.CollectionInsights) {
        val pieChart: PieChart = binding.progressPieChart
        
        val reviewed = insights.reviewedItems.coerceAtLeast(0)
        val unreviewed = (insights.totalItems - insights.reviewedItems).coerceAtLeast(0)
        
        val entries = if (insights.totalItems > 0) {
            listOf(
                PieEntry(reviewed.toFloat(), getString(R.string.ci_reviewed)),
                PieEntry(unreviewed.toFloat(), getString(R.string.ci_not_started_label))
            )
        } else {
            listOf(PieEntry(1f, getString(R.string.ci_no_data_label)))
        }
        
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = if (insights.totalItems > 0) listOf(
            ContextCompat.getColor(requireContext(), R.color.primary),
            ContextCompat.getColor(requireContext(), R.color.surface_variant)
        ) else listOf(
            ContextCompat.getColor(requireContext(), R.color.surface_variant)
        )
        dataSet.sliceSpace = 2f
        dataSet.valueTextColor = textColor
        dataSet.valueTextSize = 12f
        dataSet.setDrawValues(insights.totalItems > 0)
        
        val data = PieData(dataSet)
        pieChart.data = data
        pieChart.description.isEnabled = false
        pieChart.setUsePercentValues(false)
        pieChart.setDrawEntryLabels(false)
        pieChart.legend.textColor = mutedColor
        pieChart.legend.textSize = 12f
        pieChart.centerText = getString(R.string.ci_progress_chart)
        pieChart.setCenterTextColor(textColor)
        pieChart.setCenterTextSize(13f)
        pieChart.holeRadius = 60f
        pieChart.transparentCircleRadius = 64f
        pieChart.animateY(1000)
        pieChart.invalidate()
    }
    
    private fun setupMasteryBarChart(insights: com.duc.objectlanguage.data.model.CollectionInsights) {
        val barChart: BarChart = binding.masteryBarChart
        
        val mastered = insights.masteredItems.coerceAtLeast(0)
        val reviewed = (insights.reviewedItems - insights.masteredItems).coerceAtLeast(0)
        val unreviewed = (insights.totalItems - insights.reviewedItems).coerceAtLeast(0)
        
        val entries = listOf(
            BarEntry(0f, mastered.toFloat()),
            BarEntry(1f, reviewed.toFloat()),
            BarEntry(2f, unreviewed.toFloat())
        )
        
        val textColor = ContextCompat.getColor(requireContext(), R.color.text_primary)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)

        val dataSet = BarDataSet(entries, "")
        dataSet.colors = listOf(
            ContextCompat.getColor(requireContext(), R.color.success),
            ContextCompat.getColor(requireContext(), R.color.warning),
            ContextCompat.getColor(requireContext(), R.color.surface_variant)
        )
        dataSet.valueTextColor = textColor
        dataSet.valueTextSize = 12f
        
        val data = BarData(dataSet)
        data.barWidth = 0.56f
        barChart.data = data
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        barChart.xAxis.textColor = mutedColor
        barChart.xAxis.setDrawGridLines(false)
        barChart.xAxis.granularity = 1f
        barChart.axisLeft.textColor = mutedColor
        barChart.axisLeft.setDrawGridLines(true)
        barChart.axisRight.isEnabled = false
        barChart.animateY(1000)
        barChart.invalidate()
    }
    
    private fun displaySuggestions(suggestions: List<String>) {
        // Clear previous suggestions
        binding.suggestionsContainer.removeAllViews()
        
        // Add each suggestion as a card
        suggestions.forEach { suggestion ->
            val itemView = layoutInflater.inflate(
                R.layout.item_suggestion,
                binding.suggestionsContainer,
                false
            )
            val textView = itemView.findViewById<TextView>(R.id.suggestionText)
            textView.text = suggestion
            binding.suggestionsContainer.addView(itemView)
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
