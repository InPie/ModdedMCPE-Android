package me.effently.moddedmcpe

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import com.google.android.material.bottomnavigation.BottomNavigationView
import me.effently.moddedmcpe.R
import me.effently.moddedmcpe.fragments.gameManager.AddonsFragment
import me.effently.moddedmcpe.fragments.gameManager.INSTALLED_MCPE_INSTANCE_ID
import me.effently.moddedmcpe.fragments.gameManager.InstancesFragment
import me.effently.moddedmcpe.fragments.gameManager.VersionsFragment
import me.effently.moddedmcpe.fragments.gameManager.isInstancePrepared
import me.effently.moddedmcpe.fragments.gameManager.packageSnapshotOf
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceRepository
import org.endercore.android.operator.instance.model.GameInstance
import org.endercore.android.operator.instance.model.InstanceSource
import org.endercore.android.operator.instance.model.InstanceSourceType
import org.endercore.android.operator.instance.model.InstanceState

class GameManagerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game_manager)

        ensureInstalledGameInstance()

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

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun ensureInstalledGameInstance() {
        val core = EnderCore.instance
        val repository = InstanceRepository(core.fileEnvironment)
        val installedPackage = core.gamePackageManager.gamePackage
        try {
            if (installedPackage != null) {
                val instance = repository.getInstance(INSTALLED_MCPE_INSTANCE_ID) ?: GameInstance().apply {
                    id = INSTALLED_MCPE_INSTANCE_ID
                    createdAt = System.currentTimeMillis()
                    settings = JsonObject()
                }
                instance.name = "Installed ${installedPackage.versionName ?: "MCPE"}"
                instance.source = InstanceSource(InstanceSourceType.INSTALLED_PACKAGE).apply {
                    packageName = installedPackage.packageName
                }
                instance.packageSnapshot = packageSnapshotOf(installedPackage)
                instance.state = if (isInstancePrepared(core.fileEnvironment, instance.id)) {
                    InstanceState.READY
                } else {
                    InstanceState.REBUILD_REQUIRED
                }
                repository.saveInstance(instance)
            } else {
                val instance = repository.getInstance(INSTALLED_MCPE_INSTANCE_ID)
                if (instance != null) {
                    instance.state = InstanceState.MISSING_SOURCE
                    repository.saveInstance(instance)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
