package com.skyd.anivu.model.preference.appearance.article

import androidx.datastore.preferences.core.booleanPreferencesKey
import com.skyd.anivu.base.BasePreference
import com.skyd.ksp.preference.Preference

@Preference
object ShowArticlePullRefreshPreference : BasePreference<Boolean> {
    private const val SHOW_ARTICLE_PULL_REFRESH = "showArticlePullRefresh"

    override val default = true
    override val key = booleanPreferencesKey(SHOW_ARTICLE_PULL_REFRESH)
}