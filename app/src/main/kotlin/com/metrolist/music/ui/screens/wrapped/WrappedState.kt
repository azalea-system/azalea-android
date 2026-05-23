package com.metrolist.music.ui.screens.wrapped

import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.SongWithStats

data class WrappedState(
    val totalMinutes: Long = 0,
    val topSongs: List<SongWithStats> = emptyList(),
    val topArtists: List<Artist> = emptyList(),
    val topAlbums: List<Album> = emptyList(),
    val uniqueSongCount: Int = 0,
    val uniqueArtistCount: Int = 0,
    val uniqueAlbumCount: Int = 0,
    val isDataReady: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)
