package me.effently.moddedmcpe.legacy_mcpe.activities

import android.os.Bundle
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

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            val instanceId = LegacyMcpe.instanceId(requireActivity()).orEmpty()
            profile = LegacyMcpe.resolveProfile(instanceId)
            preferenceManager.sharedPreferencesName = "legacy_mcpe_options_$instanceId"
            preferenceManager.sharedPreferencesMode = android.content.Context.MODE_PRIVATE

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
                setDefaultValue("Steve")
                summaryProvider = EditTextPreference.SimpleSummaryProvider.getInstance()
                setOnBindEditTextListener { editText ->
                    editText.setTextColor(ContextCompat.getColor(requireContext(), R.color.mc_text_dark))
                    editText.setHintTextColor(ContextCompat.getColor(requireContext(), R.color.mc_brown_panel_light))
                }
                setOnPreferenceChangeListener { preference, newValue ->
                    val normalized = LegacyMcpe.normalizedUsername(newValue as? String)
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
            category.addPreference(switch("gfx_lowquality", R.string.legacy_low_quality, false).apply {
                setOnPreferenceChangeListener { _, _ ->
                    view?.post { syncGraphicsState() }
                    true
                }
            })
        }

        private fun addControls(screen: androidx.preference.PreferenceScreen) {
            val category = category(R.string.legacy_category_controls)
            screen.addPreference(category)
            category.addPreference(SeekBarPreference(requireContext()).apply {
                key = "ctrl_sensitivity"
                title = getString(R.string.legacy_sensitivity)
                min = 0
                max = 100
                setDefaultValue(50)
                showSeekBarValue = true
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
            category.addPreference(switch("game_difficultypeaceful", R.string.legacy_peaceful_mode, false))
        }

        private fun category(titleRes: Int): PreferenceCategory {
            return PreferenceCategory(requireContext()).apply {
                title = getString(titleRes)
            }
        }

        private fun switch(key: String, titleRes: Int, defaultValue: Boolean): SwitchPreferenceCompat {
            return SwitchPreferenceCompat(requireContext()).apply {
                this.key = key
                title = getString(titleRes)
                setDefaultValue(defaultValue)
            }
        }

        private fun syncGraphicsState() {
            val lowQuality = findPreference<SwitchPreferenceCompat>("gfx_lowquality")?.isChecked == true
            findPreference<SwitchPreferenceCompat>("gfx_fancygraphics")?.let {
                it.isEnabled = !lowQuality
                if (lowQuality) {
                    it.isChecked = false
                }
            }
        }
    }
}
