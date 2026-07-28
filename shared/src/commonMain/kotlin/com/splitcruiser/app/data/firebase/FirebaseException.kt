package com.splitcruiser.app.data.firebase

/**
 * Every failure the backend can surface, as one exception type.
 *
 * The facade throws rather than returning `Result`: `kotlin.Result` is an inline value class and
 * Kotlin/Native does not export those usefully to Objective-C, so a `Result`-returning API is
 * unusable from Swift. Android gets its `Result` shape back through thin `runCatching` wrappers in
 * androidMain, which Swift never sees.
 */
class SplitCruiserException(
    message: String,
    /** The raw Firebase error code, e.g. `EMAIL_EXISTS`. Empty for transport failures. */
    val code: String = "",
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * Identity Toolkit reports failures as `{"error":{"code":400,"message":"EMAIL_EXISTS"}}`. Those
 * codes are not fit to show anyone, and the old repository surfaced the raw `e.message` straight
 * into the login screen.
 */
internal fun authErrorMessage(code: String): String = when {
    code.startsWith("EMAIL_EXISTS") ->
        "That email is already registered. Try logging in instead."
    code.startsWith("EMAIL_NOT_FOUND") ->
        "No account found for that email."
    code.startsWith("INVALID_PASSWORD") || code.startsWith("INVALID_LOGIN_CREDENTIALS") ->
        "Incorrect email or password."
    code.startsWith("WEAK_PASSWORD") ->
        "Password must be at least 6 characters."
    code.startsWith("INVALID_EMAIL") ->
        "That doesn't look like a valid email address."
    code.startsWith("TOO_MANY_ATTEMPTS_TRY_LATER") ->
        "Too many attempts. Please wait a few minutes and try again."
    code.startsWith("USER_DISABLED") ->
        "This account has been disabled."
    code.startsWith("TOKEN_EXPIRED") || code.startsWith("INVALID_REFRESH_TOKEN") ||
        code.startsWith("USER_NOT_FOUND") ->
        "Your session expired. Please log in again."
    code.startsWith("OPERATION_NOT_ALLOWED") ->
        "Email and password sign-in is not enabled for this Firebase project."
    code.isBlank() -> "Something went wrong. Please try again."
    else -> code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * Firestore reports a missing composite index as FAILED_PRECONDITION with a console URL buried in
 * the message. That is otherwise indistinguishable from a rules rejection, so name it.
 */
internal fun firestoreErrorMessage(status: String, raw: String): String = when (status) {
    "PERMISSION_DENIED" -> "You don't have permission to do that."
    "NOT_FOUND" -> "That item no longer exists."
    "FAILED_PRECONDITION" ->
        "The backend is missing a Firestore index. See firestore.indexes.json. ($raw)"
    "UNAUTHENTICATED" -> "Your session expired. Please log in again."
    "RESOURCE_EXHAUSTED" -> "The backend is rate limited. Please try again shortly."
    else -> raw.ifBlank { "The backend request failed." }
}
