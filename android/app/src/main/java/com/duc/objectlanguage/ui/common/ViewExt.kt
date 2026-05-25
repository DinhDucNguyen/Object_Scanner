package com.duc.objectlanguage.ui.common

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import com.duc.objectlanguage.R

fun View.addScaleFeedback(scale: Float = 0.93f, duration: Long = 100) {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(duration).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(duration).start()
        }
        false
    }
}

fun View.addPulseFeedback(scale: Float = 0.93f, scaleDuration: Long = 100) {
    setOnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().scaleX(scale).scaleY(scale).setDuration(scaleDuration).start()
                v.startPulseRing()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                v.animate().scaleX(1f).scaleY(1f).setDuration(scaleDuration).start()
            }
        }
        false
    }
}

private fun View.startPulseRing() {
    val root = rootView as? ViewGroup ?: return
    val overlay = root.overlay

    val loc = IntArray(2)
    getLocationInWindow(loc)
    val rootLoc = IntArray(2)
    root.getLocationInWindow(rootLoc)

    val cx = loc[0] - rootLoc[0] + width / 2f
    val cy = loc[1] - rootLoc[1] + height / 2f
    val size = maxOf(width, height) * 3

    val strokePx = (context.resources.displayMetrics.density * 2.5f).toInt()
    val pulseView = View(context).apply {
        layout(0, 0, size, size)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(strokePx, ContextCompat.getColor(context, R.color.primary))
        }
        translationX = cx - size / 2f
        translationY = cy - size / 2f
        alpha = 0.7f
        scaleX = 0.25f
        scaleY = 0.25f
    }

    overlay.add(pulseView)
    pulseView.animate()
        .scaleX(1f).scaleY(1f)
        .alpha(0f)
        .setDuration(500)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction { overlay.remove(pulseView) }
        .start()
}

// Navigate với slide animation — dùng khi push screen mới vào back stack
fun NavController.navigateWithSlide(destinationId: Int, args: android.os.Bundle? = null) {
    val options = NavOptions.Builder()
        .setEnterAnim(R.anim.nav_slide_in_right)
        .setExitAnim(R.anim.nav_slide_out_left)
        .setPopEnterAnim(R.anim.nav_slide_in_left)
        .setPopExitAnim(R.anim.nav_slide_out_right)
        .build()
    navigate(destinationId, args, options)
}

// Haptic rung nhẹ — dùng cho nút Capture
fun View.performLightHaptic() {
    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
}

// Animation "tick" xác nhận — scale bounce sau khi nhấn Lưu
fun View.playSuccessBounce() {
    animate().cancel()
    scaleX = 1f; scaleY = 1f
    animate()
        .scaleX(1.18f).scaleY(1.18f)
        .setDuration(120)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator(2.5f))
                .start()
        }
        .start()
}
