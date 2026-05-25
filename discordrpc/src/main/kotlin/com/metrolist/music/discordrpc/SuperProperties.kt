package com.metrolist.music.discordrpc

import android.os.Build
import android.util.Base64
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import java.util.UUID

object SuperProperties {
    private const val CLIENT_VERSION = "314.13 - Stable"
    private const val CLIENT_BUILD_NUMBER = 314013
    private const val RELEASE_CHANNEL = "googleRelease"
    private const val TAG = "DiscordSuperProps"

    val superProperties: JSONObject by lazy {
        Timber.tag(TAG).d("Initializing super properties")
        JSONObject().apply {
            put("os", "Android")
            put("browser", "Discord Android")
            put("device", Build.DEVICE)
            put("system_locale", Locale.getDefault().toString())
            put("client_version", CLIENT_VERSION)
            put("release_channel", RELEASE_CHANNEL)
            put("device_vendor_id", UUID.randomUUID().toString())
            put("client_uuid", UUID.randomUUID().toString())
            put("client_launch_id", UUID.randomUUID().toString())
            put("os_version", Build.VERSION.RELEASE)
            put("os_sdk_version", Build.VERSION.SDK_INT.toString())
            put("client_build_number", CLIENT_BUILD_NUMBER)
            put("client_event_source", JSONObject.NULL)
            put("design_id", 0)
        }.also {
            Timber.tag(TAG).d("Super properties initialized: os=Android device=${Build.DEVICE} sdk=${Build.VERSION.SDK_INT}")
        }
    }

    val superPropertiesBase64: String by lazy {
        val jsonString = superProperties.toString()
        val encoded = Base64.encodeToString(jsonString.toByteArray(), Base64.NO_WRAP)
        Timber.tag(TAG).d("Base64 encoded (len=${encoded.length})")
        encoded
    }

    val userAgent: String by lazy {
        val ua = "Discord-Android/$CLIENT_BUILD_NUMBER;RNA"
        Timber.tag(TAG).d("User-Agent: $ua")
        ua
    }
}
