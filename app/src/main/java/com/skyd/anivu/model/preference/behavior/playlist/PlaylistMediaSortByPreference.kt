package com.skyd.anivu.model.preference.behavior.playlist

import androidx.datastore.preferences.core.stringPreferencesKey

object PlaylistMediaSortByPreference : BasePlaylistSortByPreference() {
    private const val PLAYLIST_MEDIA_SORT_BY = "playlistMediaSortBy"

    val values = listOf(MANUAL, CREATE_TIME)

    override val default = MANUAL

    override val key = stringPreferencesKey(PLAYLIST_MEDIA_SORT_BY)
}
