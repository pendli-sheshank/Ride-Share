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
 *
 * [projectId] is only used to name the project in the project-level failures — the ones a user
 * cannot do anything about and whose message therefore has to be aimed at whoever set the build up.
 * Without it, `CONFIGURATION_NOT_FOUND` reaches the login screen as the useless "Configuration not
 * found" and nobody can tell which of the two projects in play is misconfigured.
 */
internal fun authErrorMessage(code: String, projectId: String = ""): String {
    val project = if (projectId.isBlank()) "this Firebase project" else "Firebase project \"$projectId\""
    return when {
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
        code.startsWith("OPERATION_NOT_ALLOWED") || code.startsWith("PASSWORD_LOGIN_DISABLED") ->
            "Email/password sign-in is turned off for $project. Enable it in the Firebase console " +
                "under Authentication → Sign-in method."
        // The account tier of the whole project is missing, not the account being signed in. It
        // means the API key is valid but its project has never had Firebase Authentication turned
        // on — or the key belongs to a different project than FIREBASE_PROJECT_ID.
        code.startsWith("CONFIGURATION_NOT_FOUND") ->
            "Firebase Authentication has not been set up for $project. In the Firebase console, " +
                "open Authentication → Get started and enable the Email/Password provider, then " +
                "check that FIREBASE_API_KEY belongs to that same project."
        code.contains("API_KEY_INVALID") || code.contains("API key not valid") ->
            "This build's Firebase API key was rejected. Check the FIREBASE_API_KEY secret — it " +
                "must be the Web API key of $project."
        code.contains("SERVICE_DISABLED") || code.contains("has not been used in project") ->
            "The Identity Toolkit API is disabled for $project. Enable it in the Google Cloud " +
                "console, then retry in a few minutes."
        code.isBlank() -> "Something went wrong. Please try again."
        // A SCREAMING_SNAKE code is one of ours to prettify. Anything else is already an English
        // sentence from Google, and lowercasing it turns "API key not valid" into "api key not valid".
        code.all { it.isUpperCase() || it.isDigit() || it == '_' } ->
            code.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        else -> code
    }
}

/**
 * Firestore reports a missing composite index as FAILED_PRECONDITION with a console URL buried in
 * the message. That is otherwise indistinguishable from a rules rejection, so name it.
 *
 * [projectId] names the project in the two failures that are about the project rather than about
 * the document, for the same reason [authErrorMessage] takes it.
 */
internal fun firestoreErrorMessage(status: String, raw: String, projectId: String = ""): String {
    val project = if (projectId.isBlank()) "this Firebase project" else "project \"$projectId\""
    return when {
        // Not a missing document: a project with no database answers *every* request with this,
        // which reached the login screen as "That item no longer exists."
        isMissingDatabase(raw) ->
            "$project has no Firestore database yet. In the Firebase console, open Firestore " +
                "Database → Create database (Native mode, not Datastore), then deploy " +
                "firestore.rules and firestore.indexes.json."
        raw.contains("SERVICE_DISABLED") || raw.contains("has not been used in project") ->
            "The Firestore API is disabled for $project. Enable it in the Google Cloud console, " +
                "then retry in a few minutes."
        status == "PERMISSION_DENIED" ->
            "You don't have permission to do that. If this is a fresh project, check that " +
                "firestore.rules has been deployed — the default rules deny every request."
        status == "NOT_FOUND" -> "That item no longer exists."
        status == "FAILED_PRECONDITION" ->
            "The backend is missing a Firestore index. See firestore.indexes.json. ($raw)"
        status == "UNAUTHENTICATED" -> "Your session expired. Please log in again."
        status == "RESOURCE_EXHAUSTED" -> "The backend is rate limited. Please try again shortly."
        else -> raw.ifBlank { "The backend request failed." }
    }
}

/**
 * True for the 404 that means "there is no database here", as opposed to "there is no such
 * document". Firestore uses the same status code for both, and only the prose distinguishes them:
 * `The database (default) does not exist for project X`.
 */
internal fun isMissingDatabase(raw: String): Boolean =
    raw.contains("database", ignoreCase = true) && raw.contains("does not exist", ignoreCase = true)
