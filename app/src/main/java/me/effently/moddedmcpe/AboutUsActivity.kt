package me.effently.moddedmcpe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import me.effently.moddedmcpe.ui.ToolbarActivity


class AboutUsActivity : ToolbarActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_us)
    }

    fun onSourcesClicked(view: View) {
        val uri: Uri = Uri.parse("https://github.com/InPie/ModdedMCPE-Android")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    fun onDiscordClicked(view: View) {
        val uri: Uri = Uri.parse("https://discord.gg/BZvDJVpvzB")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    fun onUpdatesClicked(view: View) {
        val uri: Uri = Uri.parse("https://github.com/InPie/ModdedMCPE-Android/releases")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }
}
