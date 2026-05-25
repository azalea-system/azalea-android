/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import com.metrolist.music.db.entities.Song
import com.metrolist.music.discordrpc.ActivityType
import com.metrolist.music.discordrpc.DiscordRpcConnection
import com.metrolist.music.discordrpc.SuperProperties
import com.metrolist.music.discordrpc.entities.Button
import com.metrolist.music.discordrpc.entities.Timestamps
import timber.log.Timber

class DiscordRPC(
    token: String,
) {
    private val connection = DiscordRpcConnection(
        token = token,
        os = "Android",
        browser = "Discord Android",
        device = android.os.Build.DEVICE,
        userAgent = SuperProperties.userAgent,
        superPropertiesBase64 = SuperProperties.superPropertiesBase64,
    )

    init {
        Timber.d("[DiscordRPC] DiscordRPC instance created (device=${android.os.Build.DEVICE})")
    }

    fun start() {
        Timber.d("[DiscordRPC] start() called (wasRunning=${isRpcRunning()})")
        connection.connect()
        Timber.d("[DiscordRPC] start() returned (nowRunning=${isRpcRunning()})")
    }

    fun closeRPC() {
        Timber.d("[DiscordRPC] closeRPC() called (wasRunning=${isRpcRunning()})")
        connection.closeDirect()
        Timber.d("[DiscordRPC] closeRPC() completed")
    }

    fun isRpcRunning(): Boolean {
        val running = connection.isRunning()
        Timber.v("[DiscordRPC] isRpcRunning: $running")
        return running
    }

    suspend fun updateSong(
        song: Song,
        currentPlaybackTimeMillis: Long,
        playbackSpeed: Float = 1.0f,
        useDetails: Boolean = false,
        status: String = "online",
        button1Text: String = "",
        button1Visible: Boolean = true,
        button2Text: String = "",
        button2Visible: Boolean = true,
        activityType: String = "listening",
        activityName: String = "",
    ): Result<Unit> {
        val startTime = System.currentTimeMillis()
        Timber.d("[DiscordRPC] updateSong: ENTER")
        Timber.d("[DiscordRPC]   song.id=${song.song.id} title='${song.song.title}'")
        Timber.d("[DiscordRPC]   duration=${song.song.duration}s")
        Timber.d("[DiscordRPC]   artists=${song.artists.map { it.name }}")
        Timber.d("[DiscordRPC]   album='${song.album?.title}'")
        Timber.d("[DiscordRPC]   thumbnailUrl=${song.song.thumbnailUrl != null}")
        Timber.d("[DiscordRPC]   currentPlaybackTimeMillis=$currentPlaybackTimeMillis")
        Timber.d("[DiscordRPC]   playbackSpeed=$playbackSpeed")
        Timber.d("[DiscordRPC]   useDetails=$useDetails status='$status'")
        Timber.d("[DiscordRPC]   button1=[visible=$button1Visible text='$button1Text']")
        Timber.d("[DiscordRPC]   button2=[visible=$button2Visible text='$button2Text']")
        Timber.d("[DiscordRPC]   activityType='$activityType' activityName='$activityName'")

        val result = runCatching {
            val currentTime = System.currentTimeMillis()

            val adjustedPlaybackTime = (currentPlaybackTimeMillis / playbackSpeed).toLong()
            val calculatedStartTime = currentTime - adjustedPlaybackTime
            Timber.d("[DiscordRPC]   adjustedPlaybackTime=$adjustedPlaybackTime calculatedStartTime=$calculatedStartTime")

            val songTitleWithRate = if (playbackSpeed != 1.0f) {
                "${song.song.title} [${String.format("%.2fx", playbackSpeed)}]"
            } else {
                song.song.title
            }
            Timber.d("[DiscordRPC]   songTitleWithRate='$songTitleWithRate'")

            val remainingDuration = song.song.duration * 1000L - currentPlaybackTimeMillis
            val adjustedRemainingDuration = (remainingDuration / playbackSpeed).toLong()
            Timber.d("[DiscordRPC]   remainingDuration=$remainingDuration adjustedRemainingDuration=$adjustedRemainingDuration")

            val buttonsList = mutableListOf<Button>()
            if (button1Visible) {
                val rawText = button1Text.ifEmpty { "Listen on YouTube Music" }
                val resolvedText = resolveVariables(rawText, song)
                Timber.d("[DiscordRPC]   button1: raw='$rawText' resolved='$resolvedText'")
                buttonsList.add(Button(resolvedText, "https://music.youtube.com/watch?v=${song.song.id}"))
            }
            if (button2Visible) {
                val rawText = button2Text.ifEmpty { "Visit Metrolist" }
                val resolvedText = resolveVariables(rawText, song)
                Timber.d("[DiscordRPC]   button2: raw='$rawText' resolved='$resolvedText'")
                buttonsList.add(Button(resolvedText, "https://github.com/MetrolistGroup/Metrolist"))
            }
            Timber.d("[DiscordRPC]   buttonsList.size=${buttonsList.size}")

            val type = when (activityType) {
                "playing" -> ActivityType.PLAYING
                "watching" -> ActivityType.WATCHING
                "competing" -> ActivityType.COMPETING
                else -> ActivityType.LISTENING
            }
            Timber.d("[DiscordRPC]   activityType='$activityType' -> type=$type")

            val name = when {
                activityName.isNotEmpty() -> {
                    val resolved = resolveVariables(activityName, song)
                    Timber.d("[DiscordRPC]   name from activityName: '$activityName' -> '$resolved'")
                    resolved
                }
                useDetails -> {
                    Timber.d("[DiscordRPC]   name from useDetails (titleWithRate): '$songTitleWithRate'")
                    songTitleWithRate
                }
                else -> {
                    val artistNames = song.artists.joinToString { it.name }
                    Timber.d("[DiscordRPC]   name from artists: '$artistNames'")
                    artistNames
                }
            }

            val smallImageUrl = song.artists.firstOrNull()?.thumbnailUrl
            Timber.d("[DiscordRPC]   smallImageUrl=${smallImageUrl?.takeLast(40) ?: "null"}")
            Timber.d("[DiscordRPC]   largeImageUrl=${song.song.thumbnailUrl?.takeLast(40) ?: "null"}")

            val details = if (!useDetails) {
                songTitleWithRate
            } else {
                song.artists.joinToString { it.name }
            }
            val state = if (!useDetails) {
                song.artists.joinToString { it.name }
            } else {
                songTitleWithRate
            }
            Timber.d("[DiscordRPC]   details='$details' state='$state'")

            Timber.d("[DiscordRPC]   Calling connection.setActivity...")
            val setActivityStart = System.currentTimeMillis()
            connection.setActivity(
                name = name,
                type = type,
                details = details,
                state = state,
                timestamps = Timestamps(
                    start = calculatedStartTime,
                    end = currentTime + adjustedRemainingDuration,
                ),
                largeImage = song.song.thumbnailUrl,
                smallImage = smallImageUrl,
                largeText = song.album?.title,
                smallText = song.artists.firstOrNull()?.name,
                buttons = buttonsList.ifEmpty { null },
                status = status,
                since = currentTime,
                applicationId = APPLICATION_ID,
            )
            Timber.d("[DiscordRPC]   connection.setActivity took ${System.currentTimeMillis() - setActivityStart}ms")
            Timber.d("[DiscordRPC] updateSong: completed in ${System.currentTimeMillis() - startTime}ms")
        }

        if (result.isFailure) {
            Timber.e(result.exceptionOrNull(), "[DiscordRPC] updateSong FAILED after ${System.currentTimeMillis() - startTime}ms")
        } else {
            Timber.d("[DiscordRPC] updateSong: SUCCESS (${System.currentTimeMillis() - startTime}ms)")
        }

        return result
    }

    suspend fun close() {
        Timber.d("[DiscordRPC] close() called (isRpcRunning=${isRpcRunning()})")
        connection.close()
        Timber.d("[DiscordRPC] close() completed")
    }

    companion object {
        private const val APPLICATION_ID = "1411019391843172514"

        fun resolveVariables(text: String, song: Song): String {
            val original = text
            val result = text
                .replace("{song_name}", song.song.title)
                .replace("{artist_name}", song.artists.joinToString { it.name })
                .replace("{album_name}", song.album?.title ?: "")
            Timber.d("[DiscordRPC] resolveVariables: '$original' -> '$result'")
            return result
        }
    }
}
