package com.skyd.podaura.model.preference.player

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.skyd.ksp.preference.Preference
import com.skyd.podaura.model.preference.BasePreference

@Preference
object PlayerShowProgressIndicatorPreference : BasePreference<Boolean>() {
    private const val PLAYER_SHOW_PROGRESS_INDICATOR = "playerShowProgressIndicator"

    override val default = false
    override val key = booleanPreferencesKey(PLAYER_SHOW_PROGRESS_INDICATOR)
}