package me.effently.moddedmcpe.legacy_mcpe.activities

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import me.effently.moddedmcpe.R
import me.effently.moddedmcpe.legacy_mcpe.LegacyMcpe
import me.effently.moddedmcpe.legacy_mcpe.LegacyMcpeProfile
import java.io.File
import java.util.Properties

class LegacyGameOptionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.legacy_options_title)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, LegacyGameOptionsFragment())
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun finish() {
        setResult(RESULT_OK)
        super.finish()
    }

    class LegacyGameOptionsFragment : PreferenceFragmentCompat() {
        private var profile = LegacyMcpeProfile.MCPE_061
        private lateinit var optionsFile: File
        private val options = Properties()

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val instanceId = LegacyMcpe.instanceId(requireActivity()).orEmpty()
            profile = LegacyMcpe.resolveProfile(instanceId)
            optionsFile = LegacyMcpe.optionsFile(instanceId)
            readOptions()

            val screen = preferenceManager.createPreferenceScreen(requireContext())
            preferenceScreen = screen

            addMultiplayer(screen)
            addGraphics(screen)
            addControls(screen)
            addFeedback(screen)
            if (profile == LegacyMcpeProfile.MCPE_061) {
                addGame(screen)
            }

            syncGraphicsState()
        }

        private fun addMultiplayer(screen: androidx.preference.PreferenceScreen) {
            val category = category(R.string.legacy_category_multiplayer)
            screen.addPreference(category)
            category.addPreference(EditTextPreference(requireContext()).apply {
                key = "mp_username"
                title = getString(R.string.legacy_username)
                dialogTitle = getString(R.string.legacy_username)
                isPersistent = false
                text = options.getProperty("mp_username", "Steve")
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                setOnBindEditTextListener { editText ->
                    editText.setTextColor(ContextCompat.getColor(requireContext(), R.color.mc_text_dark))
                    editText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.mc_brown_panel_light))
                }
                setOnPreferenceChangeListener { preference, newValue ->
                    val normalized = LegacyMcpe.normalizedUsername(newValue as? String)
                    saveOption("mp_username", normalized)
                    if (normalized != newValue) {
                        (preference as EditTextPreference).text = normalized
                        false
                    } else {
                        true
                    }
                }
            })
            category.addPreference(switch("mp_server_visible_default", R.string.legacy_server_visible, true))
        }

        private fun addGraphics(screen: androidx.preference.PreferenceScreen) {
            val category = category(R.string.legacy_category_graphics)
            screen.addPreference(category)
            category.addPreference(switch("gfx_fancygraphics", R.string.legacy_fancy_graphics, false))
            category.addPreference(switch("gfx_lowquality", R.string.legacy_low_quality, false) {
                view?.post { syncGraphicsState() }
            })
        }

        private fun addControls(screen: androidx.preference.PreferenceScreen) {
            val category = category(R.string.legacy_category_controls)
            screen.addPreference(category)
            category.addPreference(SeekBarPreference(requireContext()).apply {
                key = "ctrl_sensitivity"
                title = getString(R.string.legacy_sensitivity)
                isPersistent = false
                min = 0
                max = 100
                value = intOption("ctrl_sensitivity", 50)
                showSeekBarValue = true
                setOnPreferenceChangeListener { _, newValue ->
                    saveOption("ctrl_sensitivity", newValue.toString())
                    true
                }
            })
            category.addPreference(switch("ctrl_invertmouse", R.string.legacy_invert_y_axis, false))
            category.addPreference(switch("ctrl_islefthanded", R.string.legacy_lefty, false))
            category.addPreference(switch("ctrl_usetouchscreen", R.string.legacy_use_touchscreen, true).apply {
                isEnabled = false
            })
            if (profile == LegacyMcpeProfile.MCPE_061) {
                category.addPreference(switch("ctrl_usetouchjoypad", R.string.legacy_split_touch_controls, false))
            }
        }

        private fun addFeedback(screen: androidx.preference.PreferenceScreen) {
            val category = category(R.string.legacy_category_feedback)
            screen.addPreference(category)
            category.addPreference(switch("feedback_vibration", R.string.legacy_vibration, true))
        }

        private fun addGame(screen: androidx.preference.PreferenceScreen) {
            val category = category(R.string.legacy_category_game)
            screen.addPreference(category)
            category.addPreference(SwitchPreferenceCompat(requireContext()).apply {
                key = "game_difficulty"
                title = getString(R.string.legacy_peaceful_mode)
                isPersistent = false
                isChecked = options.getProperty("game_difficulty", "2") == "0"
                setOnPreferenceChangeListener { _, newValue ->
                    saveOption("game_difficulty", if (newValue == true) "0" else "2")
                    true
                }
            })
        }

        private fun category(titleRes: Int): PreferenceCategory {
            return PreferenceCategory(requireContext()).apply {
                title = getString(titleRes)
            }
        }

        private fun switch(
            key: String,
            titleRes: Int,
            defaultValue: Boolean,
            afterChange: (() -> Unit)? = null
        ): SwitchPreferenceCompat {
            return SwitchPreferenceCompat(requireContext()).apply {
                this.key = key
                title = getString(titleRes)
                isPersistent = false
                isChecked = booleanOption(key, defaultValue)
                setOnPreferenceChangeListener { _, newValue ->
                    saveOption(key, newValue.toString())
                    afterChange?.invoke()
                    true
                }
            }
        }

        private fun syncGraphicsState() {
            val lowQuality = findPreference<SwitchPreferenceCompat>("gfx_lowquality")?.isChecked == true
            findPreference<SwitchPreferenceCompat>("gfx_fancygraphics")?.let {
                it.isEnabled = !lowQuality
                if (lowQuality) {
                    it.isChecked = false
                    saveOption("gfx_fancygraphics", "false")
                }
            }
        }

        private fun readOptions() {
            if (!optionsFile.isFile) {
                return
            }
            try {
                optionsFile.inputStream().use { options.load(it) }
            } catch (e: Exception) {
                Log.w("LegacyMcpe", "Failed to read legacy options from ${optionsFile.absolutePath}", e)
            }
        }

        private fun saveOption(key: String, value: String) {
            options.setProperty(key, value)
            try {
                optionsFile.parentFile?.mkdirs()
                optionsFile.bufferedWriter().use { writer ->
                    for ((optionKey, optionValue) in options) {
                        writer.append(optionKey.toString())
                            .append(':')
                            .append(optionValue.toString())
                        writer.newLine()
                    }
                }
            } catch (e: Exception) {
                Log.w("LegacyMcpe", "Failed to write legacy options to ${optionsFile.absolutePath}", e)
            }
        }

        private fun booleanOption(key: String, defaultValue: Boolean): Boolean {
            return options.getProperty(key)?.toBooleanStrictOrNull() ?: defaultValue
        }

        private fun intOption(key: String, defaultValue: Int): Int {
            return options.getProperty(key)?.toIntOrNull() ?: defaultValue
        }
    }
}
