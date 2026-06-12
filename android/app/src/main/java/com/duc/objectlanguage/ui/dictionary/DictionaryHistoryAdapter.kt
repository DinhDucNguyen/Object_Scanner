package com.duc.objectlanguage.ui.dictionary

import android.animation.ObjectAnimator
import androidx.core.animation.doOnEnd
import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.duc.objectlanguage.R
import com.duc.objectlanguage.data.model.DictionaryHistoryItem
import com.duc.objectlanguage.databinding.ItemDictionaryHistoryBinding
import com.duc.objectlanguage.utils.LocaleHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DictionaryHistoryAdapter(
    private val onItemClick: (DictionaryHistoryItem) -> Unit,
    val onSwipeDelete: (DictionaryHistoryItem) -> Unit = {}
) : ListAdapter<DictionaryHistoryItem, DictionaryHistoryAdapter.HistoryViewHolder>(DiffCallback) {

    private var openedHolder: HistoryViewHolder? = null

    inner class HistoryViewHolder(val binding: ItemDictionaryHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var isSwiped = false
        private val revealWidth = binding.root.context.resources.displayMetrics.density * 80

        @SuppressLint("ClickableViewAccessibility")
        fun bind(item: DictionaryHistoryItem) {
            binding.tvWord.text = item.tuTra

            if (!item.phienAm.isNullOrBlank()) {
                binding.tvPhonetic.text = item.phienAm
                binding.tvPhonetic.visibility = View.VISIBLE
            } else {
                binding.tvPhonetic.visibility = View.GONE
            }

            binding.tvTranslation.text = item.ketQuaDich ?: ""
            binding.tvTime.text = formatRelativeTime(item.lanTraCuoi, binding.root.context)

            if (!item.imageUrl.isNullOrBlank()) {
                binding.ivObjectImage.visibility = View.VISIBLE
                binding.ivPlaceholder.visibility = View.GONE
                Glide.with(binding.root.context)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(binding.ivObjectImage)
            } else {
                binding.ivObjectImage.visibility = View.GONE
                binding.ivPlaceholder.visibility = View.VISIBLE
            }

            resetSwipe(animate = false)

            binding.layoutDeleteBg.setOnClickListener {
                // Hiện confirm dialog, card giữ nguyên vị trí mở
                // onSwipeDelete sẽ tự animate card ra ngoài trước khi xóa
                onSwipeDelete(item)
            }

            var startX = 0f
            var startY = 0f
            var isDragging = false
            var isVertical = false
            var startTranslationX = 0f

            binding.cardForeground.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        // Nếu item khác đang mở thì đóng lại
                        if (openedHolder != null && openedHolder != this) {
                            openedHolder?.resetSwipe(animate = true)
                        }
                        startX = event.rawX
                        startY = event.rawY
                        isDragging = false
                        isVertical = false
                        startTranslationX = binding.cardForeground.translationX
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startX
                        val dy = event.rawY - startY
                        if (!isDragging && !isVertical) {
                            if (Math.abs(dx) > 8 || Math.abs(dy) > 8) {
                                if (Math.abs(dy) > Math.abs(dx)) {
                                    isVertical = true
                                    return@setOnTouchListener false
                                } else if (dx < 0) {
                                    isDragging = true
                                    v.isPressed = false
                                    v.parent?.requestDisallowInterceptTouchEvent(true)
                                }
                            }
                        }
                        if (isDragging) {
                            val newX = (startTranslationX + dx).coerceIn(-revealWidth, 0f)
                            binding.cardForeground.translationX = newX
                            true
                        } else false
                    }
                    MotionEvent.ACTION_UP -> {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        when {
                            isDragging -> {
                                val tx = binding.cardForeground.translationX
                                if (tx < -revealWidth / 2) openSwiped()
                                else resetSwipe(animate = true)
                            }
                            isSwiped -> resetSwipe(animate = true)
                            !isVertical -> onItemClick(item)
                        }
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                            resetSwipe(animate = true)
                        }
                        false
                    }
                    else -> false
                }
            }
        }

        fun openSwiped() {
            if (openedHolder != null && openedHolder != this) {
                openedHolder?.resetSwipe(animate = true)
            }
            isSwiped = true
            openedHolder = this
            ObjectAnimator.ofFloat(binding.cardForeground, "translationX",
                binding.cardForeground.translationX, -revealWidth)
                .apply { duration = 200; start() }
        }

        fun resetSwipe(animate: Boolean) {
            isSwiped = false
            if (openedHolder == this) openedHolder = null
            if (animate) {
                ObjectAnimator.ofFloat(binding.cardForeground, "translationX",
                    binding.cardForeground.translationX, 0f)
                    .apply { duration = 200; start() }
            } else {
                binding.cardForeground.translationX = 0f
            }
        }

        fun slideOutThenDelete(onDone: () -> Unit) {
            ObjectAnimator.ofFloat(
                binding.cardForeground, "translationX",
                binding.cardForeground.translationX, -binding.root.width.toFloat()
            ).apply {
                duration = 200
                doOnEnd {
                    if (openedHolder == this@HistoryViewHolder) openedHolder = null
                    onDone()
                }
                start()
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemDictionaryHistoryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: HistoryViewHolder) {
        if (openedHolder == holder) openedHolder = null
        holder.resetSwipe(animate = false)
        super.onViewRecycled(holder)
    }

    fun closeOpenedItem() {
        openedHolder?.resetSwipe(animate = true)
    }

    fun slideOutOpenedItem(onDone: () -> Unit) {
        openedHolder?.slideOutThenDelete(onDone) ?: onDone()
    }

    private fun formatRelativeTime(isoTime: String?, context: Context): String {
        if (isoTime.isNullOrBlank()) return ""
        return try {
            val dt = parseIsoLikeTime(isoTime) ?: return ""
            val minutes = ((Date().time - dt.time) / 60000L).coerceAtLeast(0L)
            when {
                minutes < 1 -> LocaleHelper.applyLocale(context).getString(R.string.dictionary_time_just_now)
                minutes < 60 -> "${minutes}p"
                minutes < 1440 -> "${minutes / 60}h"
                minutes < 10080 -> "${minutes / 1440}n"
                else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(dt)
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseIsoLikeTime(value: String): Date? {
        val normalized = value.take(19).replace(' ', 'T')
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(normalized)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DictionaryHistoryItem>() {
        override fun areItemsTheSame(old: DictionaryHistoryItem, new: DictionaryHistoryItem) =
            old.id == new.id

        override fun areContentsTheSame(old: DictionaryHistoryItem, new: DictionaryHistoryItem) =
            old == new
    }
}
