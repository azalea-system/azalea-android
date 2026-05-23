package com.metrolist.music.ui.screens.wrapped

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.db.DatabaseDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class WrappedViewModel @Inject constructor(
    private val databaseDao: DatabaseDao,
) : ViewModel() {

    private val _state = MutableStateFlow(WrappedState())
    val state = _state.asStateFlow()

    fun prepare(fromTimeStamp: Long, toTimeStamp: Long) {
        if (_state.value.isDataReady || _state.value.isLoading) return
        _state.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            try {
                val topSongsDeferred = async(Dispatchers.IO) { databaseDao.mostPlayedSongsStats(fromTimeStamp, limit = 30, toTimeStamp = toTimeStamp).first() }
                val topArtistsDeferred = async(Dispatchers.IO) { databaseDao.mostPlayedArtists(fromTimeStamp, limit = 5, toTimeStamp = toTimeStamp).first() }
                val topAlbumsDeferred = async(Dispatchers.IO) { databaseDao.mostPlayedAlbums(fromTimeStamp, limit = 5, toTimeStamp = toTimeStamp).first() }
                val uniqueSongCountDeferred = async(Dispatchers.IO) { databaseDao.getUniqueSongCountInRange(fromTimeStamp, toTimeStamp).first() }
                val uniqueArtistCountDeferred = async(Dispatchers.IO) { databaseDao.getUniqueArtistCountInRange(fromTimeStamp, toTimeStamp).first() }
                val uniqueAlbumCountDeferred = async(Dispatchers.IO) { databaseDao.getUniqueAlbumCountInRange(fromTimeStamp, toTimeStamp).first() }
                val totalPlayTimeMsDeferred = async(Dispatchers.IO) { databaseDao.getTotalPlayTimeInRange(fromTimeStamp, toTimeStamp).first() ?: 0L }

                val results = awaitAll(
                    topSongsDeferred,
                    topArtistsDeferred,
                    topAlbumsDeferred,
                    uniqueSongCountDeferred,
                    uniqueArtistCountDeferred,
                    uniqueAlbumCountDeferred,
                    totalPlayTimeMsDeferred,
                )

                withContext(Dispatchers.IO) {
                    @Suppress("UNCHECKED_CAST")
                    val topSongsResult = results[0] as List<com.metrolist.music.db.entities.SongWithStats>
                    @Suppress("UNCHECKED_CAST")
                    val topArtistsResult = results[1] as List<com.metrolist.music.db.entities.Artist>
                    @Suppress("UNCHECKED_CAST")
                    val topAlbumsResult = results[2] as List<com.metrolist.music.db.entities.Album>

                    _state.update {
                        it.copy(
                            topSongs = topSongsResult,
                            topArtists = topArtistsResult,
                            topAlbums = topAlbumsResult,
                            uniqueSongCount = results[3] as Int,
                            uniqueArtistCount = results[4] as Int,
                            uniqueAlbumCount = results[5] as Int,
                            totalMinutes = (results[6] as Long) / 1000 / 60,
                            isDataReady = true,
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }

    companion object {
        val HALF_YEAR_START: Long
            get() = Calendar.getInstance().apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.JANUARY)
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis

        val HALF_YEAR_END: Long
            get() = Calendar.getInstance().apply {
                set(Calendar.YEAR, 2026)
                set(Calendar.MONTH, Calendar.MAY)
                set(Calendar.DAY_OF_MONTH, 31)
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis

        const val CARD_CUTOFF_YEAR = 2026
        const val CARD_CUTOFF_MONTH = 6
        const val CARD_CUTOFF_DAY = 1
    }
}
