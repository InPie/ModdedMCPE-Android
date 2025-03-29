package me.effently.moddedmcpe

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity


class AboutUsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about_us)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    fun onSourcesClicked(view: View) {
        val uri: Uri = Uri.parse("https://github.com/oldminecraftcommunity/Modded-MCPE")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    fun onDiscordClicked(view: View) {
        val uri: Uri = Uri.parse("https://discord.gg/hex5QrRNqa")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    fun onUpdatesClicked(view: View) {
        val uri: Uri = Uri.parse("https://github.com/oldminecraftcommunity/Modded-MCPE/releases")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }
}