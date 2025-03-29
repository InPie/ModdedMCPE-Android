package me.effently.moddedmcpe

import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.content.ActivityNotFoundException
import android.provider.Settings
import android.os.Environment
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import me.effently.moddedmcpe.BuildConfig
import androidx.core.net.toUri


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<TextView>(R.id.version_name_textview).text = BuildConfig.VERSION_NAME

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = mutableListOf<String>()

            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (permissions.isNotEmpty()) {
                requestPermissions(permissions.toTypedArray(), 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    // val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    // fallbackIntent.data = Uri.fromParts("package", packageName, null)
                    // startActivity(fallbackIntent)
                }
            }
        }

    }

    fun onStartGameClicked(view: View) {
        startActivity(Intent(this, InitializingActivity::class.java))
        finish()
    }

    fun onMenuClicked(view: View) {
        startActivity(Intent(this, OptionsActivity::class.java))
    }
}