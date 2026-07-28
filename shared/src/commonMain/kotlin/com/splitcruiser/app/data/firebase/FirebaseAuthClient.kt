package com.splitcruiser.app.data.firebase

import com.splitcruiser.app.data.FirebaseConfig
import com.splitcruiser.app.data.nowMs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Firebase Authentication over the Identity Toolkit REST API.
 *
 * Every call is unauthenticated apart from the ones that take an explicit `idToken`, so this class
 * deliberately does not depend on [TokenProvider] — the dependency runs the other way, which keeps
 * the refresh path free of cycles.
 */
internal class FirebaseAuthClient(
    private val http: HttpClient,
    private val config: FirebaseConfig,
) {

    suspend fun signUp(email: String, password: String): StoredSession =
        identityCall("accounts:signUp", IdentityRequest(email = email, password = password))

    suspend fun signIn(email: String, password: String): StoredSession =
        identityCall("accounts:signInWithPassword", IdentityRequest(email = email, password = password))

    /**
     * The odd one out, in three ways: a different host, a form-encoded body rather than JSON, and a
     * snake_case response where every other Identity Toolkit response is camelCase.
     */
    suspend fun refresh(refreshToken: String): StoredSession {
        val response = http.submitForm(
            url = "${config.secureTokenBase}/token?key=${config.apiKey}",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        ).requireSuccess("Token refresh")

        val body: RefreshResponse = response.body()
        return StoredSession(
            uid = body.userId,
            email = "",
            idToken = body.idToken,
            refreshToken = body.refreshToken,
            expiresAtMs = expiryFrom(body.expiresIn),
        )
    }

    suspend fun sendEmailVerification(idToken: String) {
        http.post("${config.identityBase}/accounts:sendOobCode?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(OobRequest(requestType = "VERIFY_EMAIL", idToken = idToken, email = null))
        }.requireSuccess("Sending the verification email")
    }

    suspend fun sendPasswordReset(email: String) {
        http.post("${config.identityBase}/accounts:sendOobCode?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(OobRequest(requestType = "PASSWORD_RESET", idToken = null, email = email))
        }.requireSuccess("Sending the password reset email")
    }

    suspend fun lookup(idToken: String): AccountInfo? {
        val response = http.post("${config.identityBase}/accounts:lookup?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(LookupRequest(idToken))
        }.requireSuccess("Looking up the account")
        return response.body<LookupResponse>().users.firstOrNull()
    }

    suspend fun deleteAccount(idToken: String) {
        http.post("${config.identityBase}/accounts:delete?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(LookupRequest(idToken))
        }.requireSuccess("Deleting the account")
    }

    private suspend fun identityCall(path: String, request: IdentityRequest): StoredSession {
        val response = http.post("${config.identityBase}/$path?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.value.let { it in 200..299 }) {
            val error = parseFirebaseError(runCatching { response.call.body<String>() }.getOrDefault(""))
            // Identity Toolkit carries the machine-readable code in `message`, not `status`.
            throw SplitCruiserException(authErrorMessage(error.message), code = error.message)
        }
        val body: IdentityResponse = response.body()
        return StoredSession(
            uid = body.localId,
            email = body.email,
            idToken = body.idToken,
            refreshToken = body.refreshToken,
            expiresAtMs = expiryFrom(body.expiresIn),
        )
    }

    /** `expiresIn` is seconds, and arrives as a string on both endpoints. */
    private fun expiryFrom(expiresIn: String): Long =
        nowMs() + (expiresIn.toLongOrNull() ?: 3600L) * 1000L
}

@Serializable
private data class IdentityRequest(
    val email: String,
    val password: String,
    val returnSecureToken: Boolean = true,
)

@Serializable
private data class IdentityResponse(
    val localId: String = "",
    val email: String = "",
    val idToken: String = "",
    val refreshToken: String = "",
    val expiresIn: String = "3600",
    val registered: Boolean = false,
)

@Serializable
private data class RefreshResponse(
    @SerialName("user_id") val userId: String = "",
    @SerialName("id_token") val idToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: String = "3600",
)

@Serializable
private data class OobRequest(
    val requestType: String,
    val idToken: String?,
    val email: String?,
)

@Serializable
private data class LookupRequest(val idToken: String)

@Serializable
internal data class LookupResponse(val users: List<AccountInfo> = emptyList())

@Serializable
internal data class AccountInfo(
    val localId: String = "",
    val email: String = "",
    val emailVerified: Boolean = false,
)
