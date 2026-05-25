package com.metrolist.music.discordrpc

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Serializable
private data class ExternalAssetRequest(
    val urls: List<String>,
)

@Serializable
private data class ExternalAssetResponse(
    @SerialName("url")
    val url: String? = null,
    @SerialName("external_asset_path")
    val externalAssetPath: String? = null,
)

suspend fun fetchExternalAsset(
    client: HttpClient,
    applicationId: String,
    token: String,
    imageUrl: String,
    userAgent: String,
    superPropertiesBase64: String? = null,
): String? {
    val TAG = "DiscordExtAssets"
    val imageId = imageUrl.takeLast(20)
    Timber.tag(TAG).d("fetchExternalAsset: imageUrl ends with ...$imageId")

    if (imageUrl.startsWith("mp:")) {
        Timber.tag(TAG).d("fetchExternalAsset: imageUrl already mp:, returning as-is")
        return imageUrl
    }

    val api = "https://discord.com/api/v9/applications/$applicationId/external-assets"
    val startTime = System.currentTimeMillis()
    Timber.tag(TAG).d("Posting to $api for ...$imageId")

    return withContext(NonCancellable) {
        try {
            val requestBody = Json.encodeToString(ExternalAssetRequest(urls = listOf(imageUrl)))
            Timber.tag(TAG).d("Request body: $requestBody")

            val response = client.post(api) {
                Timber.tag(TAG).d("Authorization: token (len=${token.length})")
                header("Authorization", token)
                header("User-Agent", userAgent)
                if (superPropertiesBase64 != null) {
                    Timber.tag(TAG).d("Including X-Super-Properties header (len=${superPropertiesBase64.length})")
                    header("X-Super-Properties", superPropertiesBase64)
                } else {
                    Timber.tag(TAG).w("No X-Super-Properties available")
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            val uploadTime = System.currentTimeMillis() - startTime
            Timber.tag(TAG).d("Upload completed in ${uploadTime}ms, status=${response.status.value}")
            val text = response.body<String>()
            Timber.tag(TAG).d("Response body: ${text.take(200)}")

            val json = Json { ignoreUnknownKeys = true }
            val list = json.decodeFromString<List<ExternalAssetResponse>>(text)
            Timber.tag(TAG).d("Parsed ${list.size} response items")

            if (list.isEmpty()) {
                Timber.tag(TAG).w("Asset upload returned empty list for ...$imageId, response: $text")
                return@withContext null
            }

            val result = list.firstOrNull()?.externalAssetPath?.let { "mp:$it" }
            if (result != null) {
                Timber.tag(TAG).i("Asset uploaded successfully: ...$imageId -> $result (took ${uploadTime}ms)")
            } else {
                Timber.tag(TAG).w("Asset upload returned no path for ...$imageId, first item: url=${list.firstOrNull()?.url}, response: $text")
            }
            result
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Asset upload failed for: ...$imageId after ${System.currentTimeMillis() - startTime}ms")
            null
        }
    }
}
