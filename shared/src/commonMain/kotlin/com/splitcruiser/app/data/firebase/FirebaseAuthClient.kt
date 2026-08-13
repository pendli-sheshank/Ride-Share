package com.splitcruiser.app.data.firebase

import com.splitcruiser.app.data.FirebaseConfig
import com.splitcruiser.app.data.nowMs
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
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
     * Exchanges a Google ID token for a Firebase session.
     *
     * The platform half — Credential Manager on Android — is what actually talks to Google; by the
     * time we are here the user has already picked an account and we hold a signed JWT. This
     * endpoint verifies it against the project's own Google provider and mints Firebase tokens,
     * which is why no Google SDK is needed on this side of the line.
     *
     * `postBody` is form-encoded *inside* a JSON field, and `requestUri` is required even though
     * nothing redirects anywhere — it is a leftover from the browser flow the endpoint also serves.
     */
    suspend fun signInWithGoogle(googleIdToken: String): GoogleSession {
        val response = http.post("${config.identityBase}/accounts:signInWithIdp?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(
                IdpRequest(
                    // URL-encode the token before splicing it into the form-encoded postBody. A raw
                    // `&` or `=` in the value would otherwise inject extra postBody parameters (e.g.
                    // an attacker-chosen providerId). Real Google JWTs are base64url and wouldn't
                    // today, but concatenating untrusted input into a form string is the bug class.
                    postBody = "id_token=${googleIdToken.encodeURLParameter()}&providerId=google.com",
                    requestUri = "http://localhost",
                ),
            )
        }.requireIdentitySuccess()

        val body: IdpResponse = response.body()
        return GoogleSession(
            session = StoredSession(
                uid = body.localId,
                email = body.email,
                idToken = body.idToken,
                refreshToken = body.refreshToken,
                expiresAtMs = expiryFrom(body.expiresIn),
            ),
            displayName = body.displayName,
            photoUrl = body.photoUrl,
        )
    }

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
        ).requireIdentitySuccess()

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
        }.requireIdentitySuccess()
    }

    suspend fun sendPasswordReset(email: String) {
        http.post("${config.identityBase}/accounts:sendOobCode?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(OobRequest(requestType = "PASSWORD_RESET", idToken = null, email = email))
        }.requireIdentitySuccess()
    }

    suspend fun lookup(idToken: String): AccountInfo? {
        val response = http.post("${config.identityBase}/accounts:lookup?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(LookupRequest(idToken))
        }.requireIdentitySuccess()
        return response.body<LookupResponse>().users.firstOrNull()
    }

    suspend fun deleteAccount(idToken: String) {
        http.post("${config.identityBase}/accounts:delete?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(LookupRequest(idToken))
        }.requireIdentitySuccess()
    }

    private suspend fun identityCall(path: String, request: IdentityRequest): StoredSession {
        val response = http.post("${config.identityBase}/$path?key=${config.apiKey}") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        response.requireIdentitySuccess()
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

    /**
     * The Identity Toolkit counterpart of [requireSuccess], which maps codes the Firestore way and
     * would show `CONFIGURATION_NOT_FOUND` raw. Identity Toolkit carries its machine-readable code
     * in `error.message`, not `error.status`.
     */
    private suspend fun HttpResponse.requireIdentitySuccess(): HttpResponse {
        if (status.isSuccess()) return this
        val error = parseFirebaseError(runCatching { bodyAsText() }.getOrDefault(""))
        val code = error.message.ifBlank { error.status }
        throw SplitCruiserException(authErrorMessage(code, config.projectId), code = code)
    }
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
private data class IdpRequest(
    val postBody: String,
    val requestUri: String,
    val returnSecureToken: Boolean = true,
    /** Off deliberately: the Google access token has no use here and does not want storing. */
    val returnIdpCredential: Boolean = false,
)

@Serializable
private data class IdpResponse(
    val localId: String = "",
    val email: String = "",
    val idToken: String = "",
    val refreshToken: String = "",
    val expiresIn: String = "3600",
    val displayName: String = "",
    val photoUrl: String = "",
)

/** What Google knows about the account, alongside the Firebase session it was traded for. */
internal data class GoogleSession(
    val session: StoredSession,
    val displayName: String,
    val photoUrl: String,
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
