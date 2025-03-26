package me.effently.moddedmcpe.ui

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton
import androidx.core.graphics.withSave

class GreenButton(context: Context, attrs: AttributeSet) : AppCompatButton(context, attrs) {
    private val shiftY: Float = 6 * context.resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        canvas.withSave() {
            if (isPressed) {
                canvas.translate(0f, shiftY * 0.90f)
            }
            super.onDraw(canvas)
        }
    }
}