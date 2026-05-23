package com.metrolist.music.ui.screens.wrapped

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import com.metrolist.music.di.DownloadCache
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class WrappedAudioController @Inject constructor(
    @ApplicationContext private val context: Context,
    @DownloadCache private val downloadCache: SimpleCache,
) {
    private var player: ExoPlayer? = null
    private var pageTrackMap: Map<Int, Int> = emptyMap()
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    private val audioManager: AudioManager?
        get() = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    val isPlaying: Boolean
        get() = player?.isPlaying ?: false

    val isMuted: Boolean
        get() = (player?.volume ?: 1f) == 0f

    val currentTrackIndex: Int
        get() = player?.currentMediaItemIndex ?: 0

    fun createPlayer(
        mediaItems: List<MediaItem>,
        pageToTrack: Map<Int, Int>,
    ): ExoPlayer {
        release()

        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val newPlayer = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setHandleAudioBecomingNoisy(false)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            ).setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()

        newPlayer.setMediaItems(mediaItems)
        newPlayer.prepare()
        newPlayer.playWhenReady = true

        requestAudioFocus()

        pageTrackMap = pageToTrack
        player = newPlayer
        return newPlayer
    }

    fun seekToPage(pageIndex: Int) {
        val trackIndex = pageTrackMap[pageIndex] ?: 0
        seekToTrack(trackIndex)
    }

    fun seekToTrack(trackIndex: Int) {
        if (player?.currentMediaItemIndex != trackIndex) {
            player?.seekToDefaultPosition(trackIndex)
        }
    }

    fun setMuted(muted: Boolean) {
        player?.volume = if (muted) 0f else 1f
    }

    fun release() {
        abandonAudioFocus()
        player?.stop()
        player?.release()
        player = null
        pageTrackMap = emptyMap()
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.playWhenReady = true
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val am = audioManager ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            ).setOnAudioFocusChangeListener { }
            .build()
        audioFocusRequest = request
        hasAudioFocus = am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        val am = audioManager ?: return
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
        hasAudioFocus = false
    }
}
