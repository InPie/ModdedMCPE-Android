package me.effently.moddedmcpe.legacy_mcpe.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import me.effently.moddedmcpe.R
import me.effently.moddedmcpe.legacy_mcpe.LegacyMcpe
import me.effently.moddedmcpe.legacy_mcpe.LegacyMcpeProfile

class LegacyCreateWorldActivity : AppCompatActivity() {
    private lateinit var worldNameInput: TextInputEditText
    private lateinit var seedInput: TextInputEditText
    private var gameModeGroup: MaterialButtonToggleGroup? = null
    private var profile = LegacyMcpeProfile.MCPE_061

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val instanceId = LegacyMcpe.instanceId(this)
        profile = LegacyMcpe.resolveProfile(instanceId)
        title = getString(R.string.legacy_create_world_title)
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

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(32), dp(24), dp(32), dp(24))
        }
        scroll.addView(root)

        worldNameInput = addTextInput(root, getString(R.string.legacy_world_name), getString(R.string.legacy_world_default_name))
        seedInput = addTextInput(root, getString(R.string.legacy_world_seed), "")

        if (profile == LegacyMcpeProfile.MCPE_061) {
            gameModeGroup = MaterialButtonToggleGroup(this).apply {
                isSingleSelection = true
                isSelectionRequired = true
                addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = View.generateViewId()
                    text = getString(R.string.legacy_game_mode_creative)
                })
                addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = View.generateViewId()
                    text = getString(R.string.legacy_game_mode_survival)
                })
                check(getChildAt(0).id)
            }
            root.addView(gameModeGroup, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            })
        }

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
            text = getString(R.string.legacy_create)
            setOnClickListener { finishCreated() }
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dp(12)
        })

        return scroll
    }

    private fun addTextInput(root: LinearLayout, label: String, value: String): TextInputEditText {
        val density = resources.displayMetrics.density
        val layout = TextInputLayout(this).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val input = TextInputEditText(layout.context).apply {
            setText(value)
            setSingleLine(true)
        }
        layout.addView(input)
        root.addView(layout, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = (16 * density).toInt()
        })
        return input
    }

    private fun finishCreated() {
        val values = if (profile == LegacyMcpeProfile.MCPE_061) {
            val selectedIndex = gameModeGroup?.indexOfChild(gameModeGroup?.findViewById(gameModeGroup?.checkedButtonId ?: -1)) ?: 0
            val mode = if (selectedIndex == 1) "survival" else "creative"
            arrayOf(worldNameInput.text?.toString().orEmpty(), seedInput.text?.toString().orEmpty(), mode)
        } else {
            arrayOf(worldNameInput.text?.toString().orEmpty(), seedInput.text?.toString().orEmpty())
        }
        setResult(Activity.RESULT_OK, Intent().putExtra(LegacyMcpe.EXTRA_INPUT_VALUES, values))
        finish()
    }

    private fun finishCancelled() {
        setResult(Activity.RESULT_CANCELED)
        finish()
    }
}
