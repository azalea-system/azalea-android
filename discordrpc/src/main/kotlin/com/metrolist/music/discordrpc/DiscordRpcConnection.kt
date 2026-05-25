package com.metrolist.music.discordrpc

import com.metrolist.music.discordrpc.entities.Activity
import com.metrolist.music.discordrpc.entities.Assets
import com.metrolist.music.discordrpc.entities.Button
import com.metrolist.music.discordrpc.entities.Metadata
import com.metrolist.music.discordrpc.entities.Timestamps
import com.metrolist.music.discordrpc.entities.Presence
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

enum class ActivityType(val value: Int) {
    PLAYING(0),
    STREAMING(1),
    LISTENING(2),
    WATCHING(3),
    COMPETING(5),
}

class DiscordRpcConnection(
    private val token: String,
    os: String = "Android",
    browser: String = "Discord Android",
    device: String = "Generic Android Device",
    private val userAgent: String = "Discord-Android/314013;RNA",
    private val superPropertiesBase64: String? = null,
) {
    private val tag = "DiscordConn"
    private val gateway = GatewayWebSocket(token, os, browser, device)
    private val httpClient = HttpClient()
    private val httpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastUpdateTime = 0L
    private val minUpdateInterval = 500L // Minimum 500ms between updates

    init {
        Timber.tag(tag).d("DiscordRpcConnection created: os=$os device=$device userAgent=$userAgent")
        Timber.tag(tag).d("Token length=${token.length}, superProperties=${superPropertiesBase64 != null}")
    }

    fun isRunning(): Boolean {
        val running = gateway.isSessionEstablished()
        Timber.tag(tag).v("isRunning: $running")
        return running
    }

    fun connect() {
        Timber.tag(tag).i("connect() called (isRunning=${isRunning()})")
        gateway.connect()
    }

    suspend fun setActivity(
        name: String?,
        type: ActivityType = ActivityType.LISTENING,
        state: String? = null,
        details: String? = null,
        timestamps: Timestamps? = null,
        largeImage: String? = null,
        largeText: String? = null,
        smallImage: String? = null,
        smallText: String? = null,
        buttons: List<Button>? = null,
        status: String = "online",
        since: Long? = null,
        applicationId: String? = null,
    ) {
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastUpdateTime
        val resolvedName = name ?: "Metrolist"
        Timber.tag(tag).i("setActivity called: name='$resolvedName' type=$type state='$state' details='$details'")
        Timber.tag(tag).d("setActivity: largeImage=${largeImage?.takeLast(50) ?: "null"}, smallImage=${smallImage?.takeLast(50) ?: "null"}")
        Timber.tag(tag).d("setActivity: largeText='$largeText' smallText='$smallText' status='$status'")
        Timber.tag(tag).d("setActivity: buttons=${buttons?.size ?: 0} applicationId=$applicationId")
        Timber.tag(tag).d("setActivity: timestamps start=${timestamps?.start} end=${timestamps?.end} since=$since")
        Timber.tag(tag).d("setActivity: elapsed since last update=${elapsed}ms (minInterval=${minUpdateInterval}ms)")

        if (elapsed < minUpdateInterval) {
            val delay = minUpdateInterval - elapsed
            Timber.tag(tag).d("setActivity: debouncing, waiting ${delay}ms (last update ${elapsed}ms ago)")
            delay(delay)
        }
        lastUpdateTime = System.currentTimeMillis()

        val startTime = lastUpdateTime

        if (!isRunning()) {
            Timber.tag(tag).d("setActivity: gateway not running, connecting...")
            gateway.connect()
            Timber.tag(tag).d("setActivity: connect() returned")
        }

        Timber.tag(tag).d("setActivity: resolving images...")
        val resolvedLargeImage = largeImage?.let {
            Timber.tag(tag).d("setActivity: resolving large image: ${it.takeLast(50)}")
            resolveImage(it).also { result ->
                Timber.tag(tag).d("setActivity: large image resolved: ${result?.takeLast(40) ?: "null"}")
            }
        }
        val resolvedSmallImage = smallImage?.let {
            Timber.tag(tag).d("setActivity: resolving small image: ${it.takeLast(50)}")
            resolveImage(it).also { result ->
                Timber.tag(tag).d("setActivity: small image resolved: ${result?.takeLast(40) ?: "null"}")
            }
        }

        val imageElapsed = System.currentTimeMillis() - startTime
        Timber.tag(tag).d("setActivity: image resolution took ${imageElapsed}ms")
        Timber.tag(tag).d("setActivity: final largeImage=${resolvedLargeImage?.takeLast(40) ?: "null"}, smallImage=${resolvedSmallImage?.takeLast(40) ?: "null"}")

        val buttonLabels = buttons?.map { it.label }?.takeIf { it.isNotEmpty() }
        val buttonUrls = buttons?.map { it.url }?.takeIf { it.isNotEmpty() }

        if (buttonLabels != null) {
            Timber.tag(tag).d("setActivity: buttons resolved: labels=$buttonLabels urls=$buttonUrls")
        }

        val activity = Activity(
            name = resolvedName,
            type = type.value,
            applicationId = applicationId,
            state = state,
            details = details,
            timestamps = timestamps,
            assets = if (resolvedLargeImage != null || resolvedSmallImage != null) {
                Assets(
                    largeImage = resolvedLargeImage,
                    largeText = largeText,
                    smallImage = resolvedSmallImage,
                    smallText = smallText,
                )
            } else {
                Timber.tag(tag).d("setActivity: no assets to send (both images null)")
                null
            },
            buttons = buttonLabels,
            metadata = buttonUrls?.let { Metadata(buttonUrls = it) },
        )

        Timber.tag(tag).d("setActivity: constructed Activity: name=${activity.name} type=${activity.type} state=${activity.state} details=${activity.details}")
        Timber.tag(tag).d("setActivity: calling gateway.updatePresence()...")
        val presenceStart = System.currentTimeMillis()
        gateway.updatePresence(
            Presence(
                activities = listOf(activity),
                since = since,
                status = status,
                afk = false,
            ),
        )
        Timber.tag(tag).d("setActivity: gateway.updatePresence() took ${System.currentTimeMillis() - presenceStart}ms")
        Timber.tag(tag).i("setActivity completed in ${System.currentTimeMillis() - startTime}ms")
    }

    suspend fun clearActivity(status: String = "online") {
        Timber.tag(tag).d("clearActivity: called (isRunning=${isRunning()}, status='$status')")
        if (isRunning()) {
            Timber.tag(tag).i("clearActivity: clearing activity via gateway")
            gateway.clearPresence()
            Timber.tag(tag).d("clearActivity: gateway.clearPresence() done")
        } else {
            Timber.tag(tag).d("clearActivity: gateway not running, nothing to clear")
        }
    }

    suspend fun close() {
        Timber.tag(tag).i("close() called (isRunning=${isRunning()})")
        clearActivity()
        Timber.tag(tag).d("close: clearing gateway...")
        gateway.close()
        Timber.tag(tag).d("close: cancelling httpScope...")
        httpScope.cancel()
        Timber.tag(tag).d("close: closing httpClient...")
        httpClient.close()
        Timber.tag(tag).i("close() completed")
    }

    fun closeDirect() {
        Timber.tag(tag).i("closeDirect() called (isRunning=${isRunning()})")
        gateway.close()
        httpScope.cancel()
        httpClient.close()
        Timber.tag(tag).d("closeDirect() completed")
    }

    private suspend fun resolveImage(image: String): String? {
        Timber.tag(tag).d("resolveImage: image=${image.takeLast(50)}, length=${image.length}")
        if (image.isBlank()) {
            Timber.tag(tag).d("resolveImage: blank image, returning null")
            return null
        }
        return if (image.startsWith("mp:") || image.startsWith("http")) {
            Timber.tag(tag).d("resolveImage: image starts with mp: or http, checking cache")
            ArtworkCache.getOrFetch(image) {
                if (image.startsWith("mp:")) {
                    Timber.tag(tag).d("resolveImage: image already mp: — ${image.takeLast(30)}")
                    image
                } else {
                    Timber.tag(tag).d("resolveImage: fetching external asset for: ${image.takeLast(50)}")
                    val deferred = httpScope.async {
                        Timber.tag(tag).d("resolveImage: launching async fetchExternalAsset...")
                        fetchExternalAsset(
                            client = httpClient,
                            applicationId = APPLICATION_ID,
                            token = token,
                            imageUrl = image,
                            userAgent = userAgent,
                            superPropertiesBase64 = superPropertiesBase64,
                        )
                    }
                    val asset = deferred.await()
                    if (asset != null) {
                        Timber.tag(tag).i("resolveImage: external asset uploaded: ${image.takeLast(30)} -> ${asset.takeLast(30)}")
                    } else {
                        Timber.tag(tag).w("resolveImage: external asset upload failed for ${image.takeLast(30)}, sending no image")
                    }
                    asset
                }
            }.also {
                Timber.tag(tag).d("resolveImage: returning ${it?.takeLast(40) ?: "null"}")
            }
        } else {
            Timber.tag(tag).d("resolveImage: image does not start with mp: or http, treating as mp: prefix")
            "mp:$image"
        }
    }

    companion object {
        private const val APPLICATION_ID = "1411019391843172514"
        private const val TAG = "DiscordConn"

        suspend fun getUserInfo(
            token: String,
            userAgent: String = SuperProperties.userAgent,
            superPropertiesBase64: String? = null,
        ): Result<UserInfo> = runCatching {
            Timber.tag(TAG).i("getUserInfo: fetching from Discord API (token len=${token.length})")
            val client = HttpClient()
            try {
                val response = client.get("https://discord.com/api/v9/users/@me") {
                    Timber.tag(TAG).d("getUserInfo: sending GET request with auth header")
                    header("Authorization", token)
                    header("User-Agent", userAgent)
                    if (superPropertiesBase64 != null) {
                        Timber.tag(TAG).d("getUserInfo: including X-Super-Properties header (len=${superPropertiesBase64.length})")
                        header("X-Super-Properties", superPropertiesBase64)
                    }
                }
                val statusCode = response.status.value
                Timber.tag(TAG).d("getUserInfo: response status=$statusCode")
                val text = response.bodyAsText()
                Timber.tag(TAG).d("getUserInfo: response body len=${text.length}")
                val json = org.json.JSONObject(text)
                val id = json.getString("id")
                val username = json.getString("username")
                val name = json.optString("global_name", username)
                val avatarHash = json.optString("avatar")
                val avatar = if (avatarHash.isNotEmpty() && avatarHash != "null") {
                    val avatarUrl = "https://cdn.discordapp.com/avatars/$id/$avatarHash.png"
                    Timber.tag(TAG).d("getUserInfo: avatar URL constructed")
                    avatarUrl
                } else {
                    Timber.tag(TAG).d("getUserInfo: no avatar hash")
                    null
                }
                Timber.tag(TAG).i("getUserInfo: success — id=$id username=$username name=$name hasAvatar=${avatar != null}")
                UserInfo(id, username, name, avatar)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "getUserInfo: HTTP request failed")
                throw e
            } finally {
                client.close()
                Timber.tag(TAG).d("getUserInfo: HTTP client closed")
            }
        }
    }
}
