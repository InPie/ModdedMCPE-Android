package me.effently.moddedmcpe

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.effently.moddedmcpe.R
import me.effently.moddedmcpe.fragments.gameManager.AddonsFragment
import me.effently.moddedmcpe.fragments.gameManager.InstancesFragment
import me.effently.moddedmcpe.fragments.gameManager.VersionsFragment
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceOperator

class GameManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_manager)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val operator = InstanceOperator(this, EnderCore.instance.fileEnvironment)
        operator.ensureInstalledGameInstanceExists()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_instances -> {
                    loadFragment(InstancesFragment())
                    true
                }
                R.id.nav_versions -> {
                    loadFragment(VersionsFragment())
                    true
                }
                R.id.nav_addons -> {
                    loadFragment(AddonsFragment())
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.nav_instances
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
