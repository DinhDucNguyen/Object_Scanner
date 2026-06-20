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
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.duc.objectlanguage.utils.resolveMediaUrl
import android.widget.ImageView
import android.widget.TextView
import android.widget.BaseAdapter
import androidx.appcompat.widget.ListPopupWindow

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HistoryViewModel by viewModels()
    private lateinit var adapter: HistoryAdapter
    private var currentPeriod = "all"
    private var currentStatusFilter = "all"
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
        binding.btnEmptyScan.setOnClickListener {
            findNavController().navigate(R.id.scanFragment)
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
        binding.recyclerView.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator()
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState != RecyclerView.SCROLL_STATE_IDLE) return
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
                binding.recyclerView.scrollToPosition(0)
                viewModel.setPeriod(period)
            }
        }
        updateFilterChips()

        binding.btnStatusFilter.setOnClickListener { view ->
            showStatusFilterDropdown(view)
        }
        styleStatusFilterButton(currentStatusFilter != "all")
    }

    private fun setupDateRange() {
        binding.btnFromDate.setOnClickListener {
            showDatePicker(R.string.history_filter_from_date, selectedFromDate) { date ->
                selectedFromDate = date
                currentPeriod = "custom"
                updateDateButtons()
                updateFilterChips()
                viewModel.setDateRange(selectedFromDate, selectedToDate)
            }
        }
        binding.btnToDate.setOnClickListener {
            showDatePicker(R.string.history_filter_to_date, selectedToDate) { date ->
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

    private fun updateEmptyState() {
        val items = viewModel.items.value ?: emptyList()
        val loading = viewModel.isLoading.value ?: false
        val empty = items.isEmpty() && !loading
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        if (empty) {
            updateEmptyStateView()
        }
    }

    private fun setupObservers() {
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            if (loading && adapter.itemCount == 0) {
                binding.shimmerHistory.visibility = View.VISIBLE
                binding.shimmerHistory.startShimmer()
                binding.tvEmpty.visibility = View.GONE
            } else {
                binding.shimmerHistory.stopShimmer()
                binding.shimmerHistory.visibility = View.GONE
                if (!loading) binding.swipeRefresh.isRefreshing = false
            }
            updateEmptyState()
        }
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.load()
            binding.swipeRefresh.post {
                Snackbar.make(binding.root, R.string.refresh_success, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.items.observe(viewLifecycleOwner) { items ->
            val wasEmpty = adapter.itemCount == 0
            adapter.submitList(items) {
                if (wasEmpty && items.isNotEmpty()) binding.recyclerView.scheduleLayoutAnimation()
            }
            updateEmptyState()
            if (items.isNotEmpty()) {
                binding.tvResultCount.text = resources.getQuantityString(
                    R.plurals.history_result_count,
                    items.size,
                    items.size,
                )
                binding.tvResultCount.visibility = View.VISIBLE
            } else {
                binding.tvResultCount.visibility = View.GONE
            }
        }

        viewModel.message.observe(viewLifecycleOwner) { message ->
            if (message != null) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                viewModel.clearMessage()
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                    .setAction(getString(R.string.btn_retry)) { viewModel.load() }
                    .show()
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

    private fun styleStatusFilterButton(active: Boolean) {
        val textColor = if (active) {
            android.graphics.Color.WHITE
        } else {
            ContextCompat.getColor(requireContext(), R.color.primary)
        }
        binding.btnStatusFilter.setBackgroundResource(
            if (active) R.drawable.bg_chip_primary else R.drawable.bg_chip_surface
        )
        binding.tvStatusFilterText.setTextColor(textColor)
        binding.ivStatusFilterArrow.imageTintList = ColorStateList.valueOf(textColor)
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

    private fun showDatePicker(titleResId: Int, currentValue: String?, onSelected: (String) -> Unit) {
        val calendar = Calendar.getInstance()
        currentValue?.let {
            runCatching {
                val parsed = isoDateFormat.parse(it)
                if (parsed != null) calendar.time = parsed
            }
        }

        val picker = com.google.android.material.datepicker.MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(titleResId))
            .setSelection(calendar.timeInMillis)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = selection
            }
            val localCalendar = Calendar.getInstance().apply {
                set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
                set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
                set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
            }
            onSelected(isoDateFormat.format(localCalendar.time))
        }
        picker.show(childFragmentManager, "MATERIAL_DATE_PICKER")
    }

    private fun updateEmptyStateView() {
        if (_binding == null) return

        when (currentStatusFilter) {
            "approved" -> {
                binding.flEmptyIconContainer.setBackgroundResource(R.drawable.bg_icon_success)
                binding.ivEmptyIcon.setImageResource(R.drawable.ic_verified)
                binding.ivEmptyIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.success)
                )
                binding.tvEmptyTitle.setText(R.string.history_empty_approved_title)
                binding.tvEmptySubtitle.setText(R.string.history_empty_approved_subtitle)
                binding.btnEmptyScan.visibility = View.GONE
            }
            "pending" -> {
                binding.flEmptyIconContainer.setBackgroundResource(R.drawable.bg_icon_primary)
                binding.ivEmptyIcon.setImageResource(R.drawable.ic_sync)
                binding.ivEmptyIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.primary)
                )
                binding.tvEmptyTitle.setText(R.string.history_empty_pending_title)
                binding.tvEmptySubtitle.setText(R.string.history_empty_pending_subtitle)
                binding.btnEmptyScan.visibility = View.GONE
            }
            else -> {
                binding.flEmptyIconContainer.setBackgroundResource(R.drawable.bg_icon_warning)
                binding.ivEmptyIcon.setImageResource(R.drawable.ic_camera)
                binding.ivEmptyIcon.imageTintList = android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.warning)
                )
                binding.tvEmptyTitle.setText(R.string.history_empty_title)
                binding.tvEmptySubtitle.setText(R.string.history_empty)
                binding.btnEmptyScan.visibility = View.VISIBLE
            }
        }
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_confirm, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteTitle)
            .text = getString(R.string.history_delete_title)
        dialogView.findViewById<android.widget.TextView>(R.id.tvDeleteMessage)
            .text = getString(R.string.history_delete_message)
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmDelete)
            .setOnClickListener {
                viewModel.deleteHistory(item)
                dialog.dismiss()
            }
        dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelDelete)
            .setOnClickListener { dialog.dismiss() }
        dialog.show()
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

    private data class FilterOption(
        val status: String,
        val text: String,
        val iconRes: Int,
        val isSelected: Boolean
    )

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showStatusFilterDropdown(anchor: View) {
        val options = listOf(
            FilterOption("all", getString(R.string.history_filter_status_all), R.drawable.ic_history, currentStatusFilter == "all"),
            FilterOption("approved", getString(R.string.history_filter_status_approved), R.drawable.ic_verified, currentStatusFilter == "approved"),
            FilterOption("pending", getString(R.string.history_filter_status_pending), R.drawable.ic_sync, currentStatusFilter == "pending")
        )

        val listPopupWindow = ListPopupWindow(requireContext())
        listPopupWindow.anchorView = anchor

        val adapter = object : BaseAdapter() {
            override fun getCount(): Int = options.size
            override fun getItem(position: Int): FilterOption = options[position]
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_dropdown_menu, parent, false)
                val option = getItem(position)

                val ivIcon = row.findViewById<ImageView>(R.id.ivIcon)
                val tvTitle = row.findViewById<TextView>(R.id.tvTitle)
                val ivCheck = row.findViewById<ImageView>(R.id.ivCheck)

                tvTitle.text = option.text
                ivIcon.setImageResource(option.iconRes)

                val tintColor = if (option.status == "approved") {
                    ContextCompat.getColor(requireContext(), R.color.success)
                } else if (option.status == "pending") {
                    ContextCompat.getColor(requireContext(), R.color.secondary)
                } else {
                    ContextCompat.getColor(requireContext(), R.color.text_primary)
                }
                ivIcon.imageTintList = ColorStateList.valueOf(tintColor)

                if (option.isSelected) {
                    ivCheck.visibility = View.VISIBLE
                    tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                } else {
                    ivCheck.visibility = View.GONE
                    tvTitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                }

                return row
            }
        }

        listPopupWindow.setAdapter(adapter)
        listPopupWindow.setContentWidth(dpToPx(175))
        listPopupWindow.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_popup_menu))
        listPopupWindow.isModal = true

        listPopupWindow.setOnItemClickListener { _, _, position, _ ->
            val selected = options[position]
            currentStatusFilter = selected.status

            val textRes = when (selected.status) {
                "all" -> R.string.history_filter_status_all
                "approved" -> R.string.history_filter_status_approved
                else -> R.string.history_filter_status_pending
            }
            binding.tvStatusFilterText.text = getString(textRes)
            styleStatusFilterButton(selected.status != "all")
            binding.recyclerView.scrollToPosition(0)
            viewModel.setStatusFilter(selected.status)
            listPopupWindow.dismiss()
        }
        listPopupWindow.show()
    }

    companion object {
        private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
