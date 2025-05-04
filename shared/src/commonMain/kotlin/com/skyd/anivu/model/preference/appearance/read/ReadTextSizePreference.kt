package com.skyd.anivu.model.preference.appearance.read

import androidx.datastore.preferences.core.floatPreferencesKey
import com.skyd.anivu.model.preference.BasePreference
import com.skyd.ksp.preference.Preference

@Preference
object ReadTextSizePreference : BasePreference<Float>() {
    private const val READ_TEXT_SIZE = "readTextSize"

    override val default = 16f
    override val key = floatPreferencesKey(READ_TEXT_SIZE)
}