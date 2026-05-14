package com.duc.objectlanguage.ui.common

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.Window
import android.widget.TextView
import com.duc.objectlanguage.R
import com.google.android.material.button.MaterialButton

object GuestUpsellDialog {

    enum class Reason { SCAN_LIMIT, OTHER_FEATURE }

    fun show(
        context: Context,
        reason: Reason,
        onLogin: () -> Unit,
        onRegister: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_guest_upsell, null)

        val title = if (reason == Reason.SCAN_LIMIT)
            context.getString(R.string.guest_scan_limit_title)
        else
            context.getString(R.string.guest_feature_title)

        val message = if (reason == Reason.SCAN_LIMIT)
            context.getString(R.string.guest_scan_limit_msg)
        else
            context.getString(R.string.guest_feature_msg)

        view.findViewById<TextView>(R.id.tvDialogTitle).text = title
        view.findViewById<TextView>(R.id.tvDialogMessage).text = message

        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(view)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                (context.resources.displayMetrics.widthPixels * 0.88).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        dialog.setCancelable(true)

        view.findViewById<MaterialButton>(R.id.btnDialogLogin).setOnClickListener {
            dialog.dismiss()
            onLogin()
        }
        view.findViewById<MaterialButton>(R.id.btnDialogRegister).setOnClickListener {
            dialog.dismiss()
            onRegister()
        }
        view.findViewById<TextView>(R.id.tvDialogLater).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
