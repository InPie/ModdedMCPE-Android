package me.effently.moddedmcpe.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import me.effently.moddedmcpe.R

// Places the content below a toolbar and keeps both clear of system bars and display cutouts.
abstract class ToolbarActivity : AppCompatActivity() {
    private lateinit var contentContainer: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        super.setContentView(R.layout.activity_toolbar)
        // Fitting insets here would leave gaps beside the toolbar
        findViewById<View>(androidx.appcompat.R.id.action_bar_root).fitsSystemWindows = false

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        contentContainer = findViewById(R.id.toolbar_content)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar_root)) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
            )
            toolbar.updatePadding(left = insets.left, top = insets.top, right = insets.right)
            contentContainer.updatePadding(left = insets.left, right = insets.right, bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun setContentView(layoutResID: Int) {
        contentContainer.removeAllViews()
        layoutInflater.inflate(layoutResID, contentContainer)
    }

    override fun setContentView(view: View?) {
        contentContainer.removeAllViews()
        contentContainer.addView(view)
    }
}
