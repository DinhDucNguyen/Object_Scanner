package com.duc.objectlanguage.utils

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.duc.objectlanguage.R

object DefinitionFormatter {
    /**
     * Formats definition text by bolding and coloring the label before the colon (':').
     * If no colon is present, returns the original text.
     */
    fun formatDefinition(context: Context, text: String?): CharSequence {
        if (text.isNullOrEmpty()) return ""
        val colonIndex = text.indexOf(':')
        if (colonIndex != -1) {
            val ssb = SpannableStringBuilder(text)
            // Bold style span for the label before the colon
            ssb.setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                colonIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            // Foreground color span (primary brand color) for the label
            ssb.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(context, R.color.primary)),
                0,
                colonIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            return ssb
        }
        return text
    }
}
