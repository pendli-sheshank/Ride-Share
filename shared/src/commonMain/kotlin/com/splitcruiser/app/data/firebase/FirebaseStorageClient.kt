package com.splitcruiser.app.data.firebase

import com.splitcruiser.app.data.FirebaseConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Firebase Storage over its v0 REST API — used only for profile pictures.
 *
 * Takes raw bytes rather than a platform file handle, so image picking and resizing stay on each
 * platform where the imaging APIs live.
 */
internal class FirebaseStorageClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
    private val tokens: TokenProvider,
) {

    /** Uploads [bytes] and returns the public download URL, matching what the Android SDK produced. */
    suspend fun uploadBytes(path: String, bytes: ByteArray, contentType: String): String {
        require(bytes.size <= MAX_UPLOAD_BYTES) {
            "Image is ${bytes.size / 1024}KB; the storage rules cap uploads at 5MB."
        }
        val token = requireToken()
        val encodedPath = path.encodeURLParameter()
        val response = http.post("${config.storageBase}?uploadType=media&name=$encodedPath") {
            // Storage v0 uses the `Firebase` auth scheme, not `Bearer`.
            firebaseAuth(token)
            // The rules compare contentType for equality, so no charset parameter may be attached.
            contentType(ContentType.parse(contentType))
            setBody(bytes)
        }.requireSuccess("Uploading $path", config.projectId)

        val body: JsonObject = response.body()
        val downloadToken = body["downloadTokens"]?.jsonPrimitive?.content?.substringBefore(',').orEmpty()
        return downloadUrl(path, downloadToken)
    }

    suspend fun delete(path: String) {
        val token = requireToken()
        val response = http.delete("${config.storageBase}/${path.encodeURLParameter()}") {
            firebaseAuth(token)
        }
        if (response.status == HttpStatusCode.NotFound) return
        response.requireSuccess("Deleting $path", config.projectId)
    }

    fun downloadUrl(path: String, downloadToken: String): String {
        val base = "${config.storageBase}/${path.encodeURLParameter()}?alt=media"
        return if (downloadToken.isBlank()) base else "$base&token=$downloadToken"
    }

    private suspend fun requireToken(): String = tokens.idToken()
        ?: throw SplitCruiserException("You need to be logged in.", code = "UNAUTHENTICATED")

    private companion object {
        const val MAX_UPLOAD_BYTES = 5 * 1024 * 1024
    }
}
