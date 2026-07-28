package com.splitcruiser.app.data.firebase

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal val firebaseJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    isLenient = true
}

/**
 * One Ktor client for all three Firebase REST surfaces.
 *
 * [engine] is null in production so Ktor picks the platform engine — OkHttp on Android, Darwin on
 * iOS — and is a `MockEngine` in tests, which is what lets the whole backend be exercised on Linux.
 */
internal fun createFirebaseHttpClient(engine: HttpClientEngine?): HttpClient {
    val configure: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        // Firebase signals failure with a 4xx and a JSON error body worth reading, so handle
        // statuses explicitly rather than letting Ktor throw a bare exception.
        expectSuccess = false
        install(ContentNegotiation) { json(firebaseJson) }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 15_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, "SplitCruiser/1.0")
        }
    }
    return if (engine != null) HttpClient(engine, configure) else HttpClient(configure)
}

/**
 * Firestore and Identity Toolkit take `Bearer`; Firebase Storage's v0 API takes `Firebase`. Getting
 * this wrong produces a 401 that looks exactly like an expired token.
 */
internal fun HttpRequestBuilder.bearer(idToken: String) {
    header(HttpHeaders.Authorization, "Bearer $idToken")
}

internal fun HttpRequestBuilder.firebaseAuth(idToken: String) {
    header(HttpHeaders.Authorization, "Firebase $idToken")
}

internal suspend fun HttpResponse.requireSuccess(context: String, projectId: String = ""): HttpResponse {
    if (status.isSuccess()) return this
    val body = runCatching { bodyAsText() }.getOrDefault("")
    val error = parseFirebaseError(body)
    throw SplitCruiserException(
        message = if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
            firestoreErrorMessage(error.status.ifBlank { "PERMISSION_DENIED" }, error.message, projectId)
        } else {
            firestoreErrorMessage(
                error.status,
                error.message.ifBlank { "$context failed ($status)" },
                projectId,
            )
        },
        code = error.status.ifBlank { error.message },
    )
}

internal data class FirebaseErrorBody(val status: String, val message: String)

/**
 * Both surfaces nest the failure under `error`, but Identity Toolkit puts the machine-readable code
 * in `error.message` (e.g. `EMAIL_EXISTS`) while Firestore puts it in `error.status`.
 */
internal fun parseFirebaseError(body: String): FirebaseErrorBody {
    val error = runCatching {
        firebaseJson.parseToJsonElement(body).jsonObject["error"]?.jsonObject
    }.getOrNull() ?: return FirebaseErrorBody("", body.take(200))
    return FirebaseErrorBody(
        status = error["status"]?.jsonPrimitive?.content.orEmpty(),
        message = error["message"]?.jsonPrimitive?.content.orEmpty(),
    )
}

internal fun JsonObject.fieldsOrEmpty(): JsonObject =
    this["fields"]?.jsonObject ?: JsonObject(emptyMap())
