package com.metrolist.music.ui.screens.wrapped

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.db.DatabaseDao
import com.metrolist.music.db.entities.Album
import com.metrolist.music.db.entities.Artist
import com.metrolist.music.db.entities.SongWithStats
import com.metrolist.music.utils.YTPlayerUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
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
    private val audioController: WrappedAudioController,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(WrappedState())
    val state = _state.asStateFlow()

    private fun buildPageTrackMap(uniqueToMedia: Map<Int, Int>): Map<Int, Int> {
        val fallback = uniqueToMedia.values.firstOrNull() ?: 0
        fun idx(uniqueIndex: Int): Int = uniqueToMedia[uniqueIndex] ?: fallback
        return buildMap {
            put(0, idx(0))
            put(1, idx(1)); put(2, idx(1)); put(3, idx(1))
            put(4, idx(2)); put(5, idx(2))
            put(6, idx(3)); put(7, idx(3))
            put(8, idx(4))
            put(9, idx(5)); put(10, idx(5)); put(11, idx(5))
            put(12, idx(6)); put(13, idx(6)); put(14, idx(6))
            put(15, idx(7)); put(16, idx(7)); put(17, idx(7))
            put(18, idx(8))
        }
    }

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

                @Suppress("UNCHECKED_CAST")
                val topSongsResult = results[0] as List<SongWithStats>
                val topArtistsResult = results[1] as List<Artist>
                val topAlbumsResult = results[2] as List<Album>

                _state.update {
                    it.copy(
                        topSongs = topSongsResult,
                        topArtists = topArtistsResult,
                        topAlbums = topAlbumsResult,
                        uniqueSongCount = results[3] as Int,
                        uniqueArtistCount = results[4] as Int,
                        uniqueAlbumCount = results[5] as Int,
                        totalMinutes = (results[6] as Long) / 1000 / 60,
                        isLoading = false,
                    )
                }

                prepareAudio(topSongsResult, topArtistsResult, topAlbumsResult)
                _state.update { it.copy(isDataReady = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isDataReady = false,
                        error = e.message ?: "Unknown error",
                    )
                }
            }
        }
    }

    private suspend fun prepareAudio(
        topSongs: List<SongWithStats>,
        topArtists: List<Artist>,
        topAlbums: List<Album>,
    ) {
        if (topSongs.size < 7 || topArtists.isEmpty()) {
            _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }
            return
        }

        val usedIds = mutableSetOf<String>()

        val groupD = topSongs[0]
        usedIds.add(groupD.id)

        val topArtistId = topArtists.first().artist.id
        val groupF = topSongs.firstOrNull {
            it.id !in usedIds && it.artists.any { a -> a.id == topArtistId }
        } ?: topSongs.firstOrNull { it.id !in usedIds } ?: run {
            _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }; return
        }
        usedIds.add(groupF.id)

        val poolC = topSongs.slice(2..6).filter { it.id !in usedIds }
        if (poolC.isEmpty()) { _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }; return }
        val groupC = poolC.random()
        usedIds.add(groupC.id)

        val poolB = topSongs.slice(7..topSongs.lastIndex).filter { it.id !in usedIds }
        if (poolB.isEmpty()) { _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }; return }
        val groupB = poolB.random()
        usedIds.add(groupB.id)

        val poolE = topSongs.filter { it.id !in usedIds && it.artists.any { a -> a.id == topArtistId } }
        val groupE = poolE.randomOrNull() ?: topSongs.firstOrNull { it.id !in usedIds } ?: run {
            _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }; return
        }
        usedIds.add(groupE.id)

        val poolG = topSongs.filter { it.id !in usedIds }
        if (poolG.isEmpty()) { _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }; return }
        val groupG = poolG.random()
        usedIds.add(groupG.id)

        val poolH = topSongs.filter { it.id !in usedIds }
        if (poolH.isEmpty()) { _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }; return }
        val groupH = poolH.random()
        usedIds.add(groupH.id)

        val groupA = topSongs.filter { it.id !in usedIds }.randomOrNull() ?: groupH
        usedIds.add(groupA.id)

        val devVideoId = DEVELOPER_FAVORITE_VIDEO_IDS.random()
        val groupI = SongWithStats(
            id = devVideoId, title = "", artists = emptyList(),
            thumbnailUrl = "", artistName = "", songCountListened = 0, timeListened = 0L,
        )
        usedIds.add(devVideoId)

        val uniqueSongs = listOf(groupA, groupB, groupC, groupD, groupE, groupF, groupG, groupH, groupI)

        _state.update { it.copy(isAudioLoading = true, audioTotalTracks = uniqueSongs.size) }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val mediaItems = mutableListOf<MediaItem>()
        val uniqueToMedia = mutableMapOf<Int, Int>()
        var failedTracks = 0

        for ((i, song) in uniqueSongs.withIndex()) {
            var success = false
            repeat(MAX_AUDIO_RETRIES) { attempt ->
                try {
                    val url = withContext(Dispatchers.IO) {
                        YTPlayerUtils.playerResponseForPlayback(
                            videoId = song.id,
                            audioQuality = AudioQuality.AUTO,
                            connectivityManager = connectivityManager,
                        ).getOrThrow().streamUrl
                    }
                    mediaItems.add(
                        MediaItem.Builder()
                            .setMediaId(song.id)
                            .setUri(url.toUri())
                            .setCustomCacheKey(song.id)
                            .build()
                    )
                    uniqueToMedia[i] = mediaItems.lastIndex
                    success = true
                } catch (_: Exception) {
                    if (attempt < MAX_AUDIO_RETRIES - 1) {
                        delay(1000L * (attempt + 1))
                    }
                }
            }
            if (!success) {
                failedTracks++
            }
            _state.update { it.copy(audioLoadingProgress = i + 1) }
        }

        _state.update { it.copy(isAudioLoading = false) }

        if (mediaItems.isEmpty()) {
            _state.update {
                it.copy(
                    audioErrorMessage = "Failed to load any audio tracks",
                    isAudioReady = true,
                )
            }
            return
        }

        if (failedTracks > 0) {
            _state.update {
                it.copy(audioErrorMessage = "Failed to load $failedTracks track(s)")
            }
        }

        val trackMap = buildPageTrackMap(uniqueToMedia)
        audioController.createPlayer(mediaItems, trackMap)
        audioController.setMuted(_state.value.isMuted)
        _state.update { it.copy(isAudioReady = true) }
    }

    fun onPageChanged(pageIndex: Int) {
        audioController.seekToPage(pageIndex)
    }

    fun toggleMute() {
        val newMuted = !_state.value.isMuted
        audioController.setMuted(newMuted)
        _state.update { it.copy(isMuted = newMuted) }
    }

    fun retryAudio() {
        val s = _state.value
        if (s.topSongs.isNotEmpty()) {
            _state.update {
                it.copy(
                    audioLoadingProgress = 0,
                    audioTotalTracks = 0,
                    isAudioReady = false,
                    isAudioLoading = false,
                    audioErrorMessage = null,
                )
            }
            viewModelScope.launch { prepareAudio(s.topSongs, s.topArtists, s.topAlbums) }
        }
    }

    fun skipAudio() {
        _state.update { it.copy(isAudioReady = true, isAudioLoading = false) }
    }

    fun pauseAudio() {
        audioController.pause()
    }

    fun resumeAudio() {
        audioController.resume()
    }

    fun releaseAudio() {
        audioController.release()
    }

    override fun onCleared() {
        super.onCleared()
        audioController.release()
    }

    companion object {
        private val DEVELOPER_FAVORITE_VIDEO_IDS = listOf(
            "Mh2JWGWvy_Y",  // "Tayer" - Amir Eid (Mo Agamy)
            "m2zUrruKjDQ",  // "Mr. Brightside" - The Killers (Adriel)
            "zselaN6zPXw",  // "Re: searchlight" - Aiobahn (Nyx)
        )
        private const val MAX_AUDIO_RETRIES = 3

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
        const val CARD_CUTOFF_MONTH = 7
        const val CARD_CUTOFF_DAY = 1
    }
}
