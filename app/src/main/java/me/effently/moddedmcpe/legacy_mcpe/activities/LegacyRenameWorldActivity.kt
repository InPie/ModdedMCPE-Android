package me.effently.moddedmcpe.legacy_mcpe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import me.effently.moddedmcpe.R
import me.effently.moddedmcpe.legacy_mcpe.LegacyMcpe

class LegacyRenameWorldActivity : AppCompatActivity() {
    private lateinit var worldNameInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.legacy_rename_world_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(createContent())
    }

    override fun onSupportNavigateUp(): Boolean {
        finishCancelled()
        return true
    }

    override fun onBackPressed() {
        finishCancelled()
    }

    private fun createContent(): View {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }

        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.legacy_world_name)
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        worldNameInput = TextInputEditText(inputLayout.context).apply {
            setText(getString(R.string.legacy_saved_world_default_name))
            setSingleLine(true)
        }
        inputLayout.addView(worldNameInput)
        root.addView(inputLayout)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        root.addView(actions, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(24)
        })

        actions.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.btn_cancel)
            setOnClickListener { finishCancelled() }
        })
        actions.addView(MaterialButton(this).apply {
            text = getString(android.R.string.ok)
            setOnClickListener { finishRenamed() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(12)
        })

        return root
    }

    private fun finishRenamed() {
        val values = arrayOf(worldNameInput.text?.toString().orEmpty())
        setResult(Activity.RESULT_OK, Intent().putExtra(LegacyMcpe.EXTRA_INPUT_VALUES, values))
        finish()
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
