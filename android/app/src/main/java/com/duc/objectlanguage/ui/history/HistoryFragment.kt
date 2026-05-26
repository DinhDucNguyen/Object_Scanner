package com.duc.objectlanguage.ui.history

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.HistoryItem
import com.duc.objectlanguage.databinding.FragmentHistoryBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.duc.objectlanguage.data.local.ApiConfig

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    private var currentPeriod = "all"
    private var selectedFromDate: String? = null
    private var selectedToDate: String? = null
    private var skipNextResume = true 

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        setupList()
        setupFilters()
        setupDateRange()
        setupSearch()
        setupObservers()
        viewModel.load()
        viewModel.loadAvatarUrl()
        animateEntrance()
    }

    private fun animateEntrance() {
        val interp = DecelerateInterpolator(1.6f)
        binding.customToolbar.apply {
            alpha = 0f; translationY = -20f
            animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(interp).start()
        }
        binding.recyclerView.apply {
            alpha = 0f; translationY = 40f
            animate().alpha(1f).translationY(0f).setDuration(360).setInterpolator(interp).setStartDelay(120).start()
        }
    }

    private fun setupList() {
        adapter = HistoryAdapter(
            onItemClick = { item -> openDetail(item) },
            onDeleteClick = { item -> confirmDelete(item) },
        )
        val layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (lastVisible >= adapter.itemCount - 4) {
                    viewModel.loadMore()
                }
            }
        })
    }

    private fun setupSearch() {
        binding.etSearchHistory.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setKeyword(s?.toString().orEmpty())
            }
        })
    }

    private fun setupFilters() {
        val filters = mapOf(
            binding.btnFilterAll to "all",
            binding.btnFilterToday to "today",
            binding.btnFilterWeek to "week",
            binding.btnFilterMonth to "month",
        )
        filters.forEach { (button, period) ->
            button.setOnClickListener {
                currentPeriod = period
                selectedFromDate = null
                selectedToDate = null
                updateDateButtons()
                updateFilterChips()
                viewModel.setPeriod(period)
            }
        }
        updateFilterChips()
    }

    private fun setupDateRange() {
        binding.btnFromDate.setOnClickListener {
            showDatePicker(selectedFromDate) { date ->
                selectedFromDate = date
                currentPeriod = "custom"
                updateDateButtons()
                updateFilterChips()
                viewModel.setDateRange(selectedFromDate, selectedToDate)
            }
        }
        binding.btnToDate.setOnClickListener {
            showDatePicker(selectedToDate) { date ->
                selectedToDate = date
                currentPeriod = "custom"
                updateDateButtons()
                updateFilterChips()
                viewModel.setDateRange(selectedFromDate, selectedToDate)
            }
        }
        binding.btnClearDateRange.setOnClickListener {
            selectedFromDate = null
            selectedToDate = null
            currentPeriod = "all"
            updateDateButtons()
            updateFilterChips()
            viewModel.clearDateRange()
        }
        updateDateButtons()
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading && adapter.itemCount == 0) View.VISIBLE else View.GONE
            if (!loading) binding.swipeRefresh.isRefreshing = false
        }
        binding.swipeRefresh.setOnRefreshListener { viewModel.load() }

        viewModel.items.observe(viewLifecycleOwner) { items ->
            val wasEmpty = adapter.itemCount == 0
            adapter.submitList(items) {
                if (wasEmpty && items.isNotEmpty()) binding.recyclerView.scheduleLayoutAnimation()
            }
            val empty = items.isEmpty() && viewModel.isLoading.value != true
            binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
            }
        }

        viewModel.streakValue.observe(viewLifecycleOwner) { streak ->
            binding.tvStreakBadgeValue.text = (streak ?: 0).toString()
        }

        viewModel.avatarUrl.observe(viewLifecycleOwner) { url ->
            if (!url.isNullOrBlank()) {
                Glide.with(this)
                    .load(resolveMediaUrl(url))
                    .transform(CircleCrop())
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivAvatar)
                binding.ivAvatar.setPadding(0, 0, 0, 0)
                binding.ivAvatar.imageTintList = null
            }
        }
    }

    private fun resolveMediaUrl(path: String): String {
        val trimmed = path.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed
        else "${ApiConfig.baseUrl.trimEnd('/')}/${trimmed.trimStart('/')}"
    }

    private fun updateFilterChips() {
        listOf(
            binding.btnFilterAll to "all",
            binding.btnFilterToday to "today",
            binding.btnFilterWeek to "week",
            binding.btnFilterMonth to "month",
        ).forEach { (chip, period) ->
            styleFilterChip(chip, period == currentPeriod)
        }
    }

    private fun styleFilterChip(chip: Chip, active: Boolean) {
        val primary = ContextCompat.getColor(requireContext(), R.color.primary)
        val surfaceSoft = ContextCompat.getColor(requireContext(), R.color.surface_soft)
        val textSecondary = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        chip.chipBackgroundColor = ColorStateList.valueOf(if (active) primary else surfaceSoft)
        chip.setTextColor(if (active) android.graphics.Color.WHITE else textSecondary)
        chip.chipStrokeWidth = 0f
    }

    private fun updateDateButtons() {
        binding.btnFromDate.text = selectedFromDate ?: getString(R.string.history_filter_from_date)
        binding.btnToDate.text = selectedToDate ?: getString(R.string.history_filter_to_date)
        val hasDateRange = selectedFromDate != null || selectedToDate != null
        binding.btnClearDateRange.visibility = if (hasDateRange) View.VISIBLE else View.GONE
        styleFilterButton(binding.btnFromDate, selectedFromDate != null)
        styleFilterButton(binding.btnToDate, selectedToDate != null)
    }

    private fun styleFilterButton(button: MaterialButton, active: Boolean) {
        val secondary = ContextCompat.getColor(requireContext(), R.color.secondary)
        val surface = ContextCompat.getColor(requireContext(), R.color.surface)
        button.backgroundTintList = ColorStateList.valueOf(if (active) secondary else surface)
        button.setTextColor(if (active) android.graphics.Color.WHITE else secondary)
        button.strokeColor = ColorStateList.valueOf(secondary)
        button.iconTint = ColorStateList.valueOf(if (active) android.graphics.Color.WHITE else secondary)
    }

    private fun showDatePicker(currentValue: String?, onSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        currentValue?.let {
            runCatching {
                val parsed = isoDateFormat.parse(it)
                if (parsed != null) calendar.time = parsed
            }
        }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val selected = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                onSelected(isoDateFormat.format(selected.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private fun openDetail(item: HistoryItem) {
        val targetCollectionId = arguments?.getInt("targetCollectionId") ?: 0
        val targetCollectionName = arguments?.getString("targetCollectionName")
        val bundle = Bundle().apply {
            putInt("scanId", item.id)
            putString("objectCode", item.objectCode ?: "")
            putString("imageUrl", item.imageUrl)
            putString("scanDate", item.scanDate)
            putInt("translationId", item.translationId ?: 0)
            putInt("targetCollectionId", targetCollectionId)
            if (targetCollectionName != null) putString("targetCollectionName", targetCollectionName)
        }
        findNavController().navigate(R.id.action_history_to_historyDetail, bundle)
    }

    private fun confirmDelete(item: HistoryItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.history_delete_title))
            .setMessage(getString(R.string.history_delete_message))
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                viewModel.deleteHistory(item)
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (skipNextResume) {
            skipNextResume = false
        } else if (_binding != null) {
            viewModel.load()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
