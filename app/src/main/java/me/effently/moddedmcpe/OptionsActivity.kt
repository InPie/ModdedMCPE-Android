package me.effently.moddedmcpe

import android.content.*
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.endercore.android.EnderCore
import org.endercore.android.exception.NModException
import org.endercore.android.mod.nmod.NMod
import org.endercore.android.mod.nmod.NModPackage
import org.endercore.android.utils.FileUtils
import java.io.File
import java.io.FileNotFoundException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.Executors


class OptionsActivity : AppCompatActivity() {
    private val backgroundExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pickNModPackage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onNModPackagePicked(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_options)
        supportFragmentManager
                .beginTransaction()
                .replace(R.id.viewOptions, OptionsFragment())
                .commit()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun onManageNModsClicked() {
        startActivity(Intent(this, ManageNModsActivity::class.java))
    }

    fun onInstallNModsClicked() {
        pickNModPackage.launch("*/*")
    }

    fun onInfoClicked() {
        startActivity(Intent(this, AboutUsActivity::class.java))
    }

    class OptionsFragment : PreferenceFragmentCompat() {
        private lateinit var listener: SharedPreferences.OnSharedPreferenceChangeListener

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey)

            // @warn: don't support rn, add this settings in the near future!
            lockForcedSwitch("redirect_directory")
            lockForcedSwitch("unlock_mjscript")
            // lockForcedSwitch("use_nmods")
            findPreference<Preference>("manage")!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                (activity as OptionsActivity?)!!.onManageNModsClicked()
                false
            }
            findPreference<Preference>("install")!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                (activity as OptionsActivity?)!!.onInstallNModsClicked()
                false
            }
            findPreference<Preference>("info")!!.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                (activity as OptionsActivity?)!!.onInfoClicked()
                false
            }

            listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences: SharedPreferences, key: String? ->
                        run {
                            when (key) {
                                "auto_license" -> {
                                    EnderCore.getInstance().optionsManager.autoLicense = sharedPreferences.getBoolean(key, EnderCore.getInstance().optionsManager.autoLicense)
                                }
                                "redirect_directory" -> {
                                    EnderCore.getInstance().optionsManager.redirectGameDir = sharedPreferences.getBoolean(key, EnderCore.getInstance().optionsManager.redirectGameDir)
                                }
                                "unlock_mjscript" -> {
                                    EnderCore.getInstance().optionsManager.unlockMjscript = sharedPreferences.getBoolean(key, EnderCore.getInstance().optionsManager.unlockMjscript)
                                }
                                "use_nmods" -> {
                                    EnderCore.getInstance().optionsManager.useNMods = sharedPreferences.getBoolean(key, EnderCore.getInstance().optionsManager.useNMods)
                                }
                            }
                        }
                        EnderCore.getInstance().optionsManager.saveDataToFile()
                    }
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun onResume() {
            super.onResume()
            preferenceManager.sharedPreferences?.registerOnSharedPreferenceChangeListener(listener)
        }

        override fun onPause() {
            super.onPause()
            preferenceManager.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(listener)
        }

        private fun lockForcedSwitch(key: String) {
            val preference = findPreference<SwitchPreferenceCompat>(key) ?: return
            preference.isEnabled = false
        }
    }

    private var nmodPackage: NModPackage? = null
    private fun onNModPackagePicked(uri: Uri) {
        backgroundExecutor.execute {
            val inputStream = contentResolver.openInputStream(uri)
            val copiedFile = File(EnderCore.getInstance().fileEnvironment.codeCacheDirPathForNMods, "package.nmod")
            FileUtils.copy(inputStream, copiedFile)
            try {
                nmodPackage = NModPackage(copiedFile)
            } catch (nmodException: NModException) {
                mainHandler.post { showNModReadFailed(nmodException) }
                return@execute
            }
            mainHandler.post { showNModReadSucceed(nmodPackage ?: return@post) }
        }
    }

    private fun onDialogInstallClicked() {
        backgroundExecutor.execute {
            val nmod: NMod?
            try {
                nmod = EnderCore.instance.nModManager.installNMod(nmodPackage)
            } catch (nmodException: NModException) {
                mainHandler.post { showNModInstallFailed(nmodException) }
                return@execute
            }
            mainHandler.post { showNModInstallSucceed(nmod) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        backgroundExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun showNModReadFailed(exception: Exception) {
        val writer = stackTraceOf(exception)
        AlertDialog.Builder(this)
            .setTitle(R.string.app_nmod_package_open_failed_title)
            .setMessage(getString(R.string.app_nmod_package_open_failed_summary) + writer)
            .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }
            .setNegativeButton(android.R.string.copy) { _: DialogInterface, _: Int ->
                copyText("NMod Package Read Failed Message", writer)
            }
            .setCancelable(false)
            .show()
    }

    private fun showNModReadSucceed(nmodPackage: NModPackage) {
        val icon = loadPackageIcon(nmodPackage)
        val dialogBuilder = AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(nmodPackage.name)
            .setMessage(getString(R.string.app_nmod_install_message, nmodPackage.name))
        if (icon != null) {
            dialogBuilder.setIcon(icon)
        }
        dialogBuilder.setPositiveButton(R.string.app_nmod_install) { dialogInterface: DialogInterface, _: Int ->
            onDialogInstallClicked()
            dialogInterface.dismiss()
        }
        dialogBuilder.setNegativeButton(R.string.app_nmod_cancel) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }
        dialogBuilder.show()
    }

    private fun showNModInstallSucceed(nmod: NMod) {
        val icon = loadNModIcon(nmod)
        val dialogBuilder = AlertDialog.Builder(this)
            .setCancelable(false)
            .setTitle(R.string.app_nmod_install_succeed_title)
            .setMessage(R.string.app_nmod_install_succeed_message)
        if (icon != null) {
            dialogBuilder.setIcon(icon)
        }
        dialogBuilder.setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, _: Int ->
            dialogInterface.dismiss()
        }
        dialogBuilder.show()
    }

    private fun showNModInstallFailed(exception: Exception) {
        val writer = stackTraceOf(exception)
        AlertDialog.Builder(this)
            .setTitle(R.string.app_nmod_install_failed_title)
            .setMessage(getString(R.string.app_nmod_install_failed_message) + writer)
            .setPositiveButton(android.R.string.ok) { dialogInterface: DialogInterface, _: Int ->
                dialogInterface.dismiss()
            }
            .setNegativeButton(android.R.string.copy) { _: DialogInterface, _: Int ->
                copyText("NMod Install Failed Message", writer)
            }
            .setCancelable(false)
            .show()
    }

    private fun loadPackageIcon(nmodPackage: NModPackage): Drawable? {
        val iconPath = nmodPackage.packageManifest.icon ?: return null
        return try {
            Drawable.createFromStream(nmodPackage.openInPackage(iconPath), iconPath)
        } catch (ignored: FileNotFoundException) {
            null
        }
    }

    private fun loadNModIcon(nmod: NMod): Drawable? {
        val iconPath = nmod.packageManifest.icon ?: return null
        return try {
            Drawable.createFromStream(nmod.openInFiles(iconPath), iconPath)
        } catch (ignored: FileNotFoundException) {
            null
        }
    }

    private fun stackTraceOf(exception: Exception): String {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        exception.printStackTrace(printWriter)
        exception.printStackTrace()
        return writer.toString()
    }

    private fun copyText(label: String, text: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val mClipData = ClipData.newPlainText(label, text)
        cm.setPrimaryClip(mClipData)
        Toast.makeText(this, R.string.app_copied, Toast.LENGTH_LONG).show()
    }
}
