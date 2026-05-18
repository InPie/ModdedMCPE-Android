package me.effently.moddedmcpe.legacy_mcpe

import android.content.Context
import android.util.Log
import org.endercore.android.EnderCore
import org.endercore.android.operator.instance.InstanceRepository
import org.endercore.android.utils.LaunchContext
import java.io.File

enum class LegacyMcpeProfile {
    MCPE_013,
    MCPE_061
}

object LegacyMcpe {
    const val EXTRA_INPUT_VALUES = "me.effently.moddedmcpe.extra.LEGACY_INPUT_VALUES"

    private const val TAG = "Modded-MCPE-UI-LegacyMcpe"

    fun instanceId(context: Context): String? {
        return LaunchContext.getInstanceId((context as? android.app.Activity)?.intent)
    }

    fun optionsFile(instanceId: String): File {
        val repository = InstanceRepository(EnderCore.instance.fileEnvironment)
        return File(File(repository.getInstanceDir(instanceId), "data"), "options.txt")
    }

    fun resolveProfile(instanceId: String?): LegacyMcpeProfile {
        if (instanceId.isNullOrBlank()) {
            Log.w(TAG, "Instance id is missing, >> back to 0.6.1 profile.")
            return LegacyMcpeProfile.MCPE_061
        }

        val versionName = try {
            val repository = InstanceRepository(EnderCore.instance.fileEnvironment)
            repository.getInstance(instanceId)?.packageSnapshot?.versionName
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read instance profile for $instanceId", e)
            null
        }

        val minor = versionName?.substringAfter("0.", "")?.substringBefore(".")?.toIntOrNull()
        val profile = if (versionName != null && versionName.startsWith("0.") && minor != null && minor < 6) {
            LegacyMcpeProfile.MCPE_013
        } else {
            LegacyMcpeProfile.MCPE_061
        }

        Log.i(TAG, "Resolved legacy profile $profile for versionName=$versionName")
        return profile
    }

    fun sanitizeAscii(value: String): String {
        val builder = StringBuilder(value.length)
        for (ch in value) {
            if (ch.code < 128) {
                builder.append(ch)
            }
        }
        return builder.toString()
    }

    fun normalizedUsername(value: String?): String {
        val sanitized = sanitizeAscii(value.orEmpty()).trim()
        return sanitized.ifEmpty { "Steve" }
    }
}
