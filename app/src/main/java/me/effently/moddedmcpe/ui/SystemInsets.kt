package me.effently.moddedmcpe.ui

import android.view.View
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

// System bars, display cutouts and the keyboard
val WindowInsetsCompat.systemInsets: Insets
    get() = getInsets(
        WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
    )

fun View.applySystemInsetsAsPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val insets = windowInsets.systemInsets
        view.updatePadding(left = insets.left, top = insets.top, right = insets.right, bottom = insets.bottom)
        WindowInsetsCompat.CONSUMED
    }
}
