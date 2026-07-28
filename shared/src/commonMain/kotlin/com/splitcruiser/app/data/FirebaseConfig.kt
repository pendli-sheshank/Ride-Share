package com.splitcruiser.app.data

/**
 * Where the Firebase project lives and how to reach it.
 *
 * Both platforms build this from [FirebaseBuildConfig], which the `generateFirebaseConfig` Gradle
 * task writes from the `FIREBASE_*` environment variables — so Android and iOS are pointed at the
 * same project by construction rather than by two separate config files.
 *
 * Only [apiKey] and [projectId] are needed to talk to Auth and Firestore; [storageBucket] is used
 * for profile pictures. `FIREBASE_APP_ID` is deliberately not consumed: it identifies an app to the
 * native SDKs and has no role in any REST endpoint.
 */
data class FirebaseConfig(
    val apiKey: String,
    val projectId: String,
    val storageBucket: String,
) {
    /**
     * False when a value is missing or is still the placeholder committed in `.env.example`.
     *
     * The old Android repository used the same test to decide whether to fall back to a local JSON
     * store. That fallback is gone — Firebase is the only backend now — so an unconfigured build
     * must say so plainly instead of appearing to work.
     */
    val isConfigured: Boolean
        get() = listOf(apiKey, projectId).all { it.isNotBlank() && !it.contains("PLACEHOLDER") }

    val identityBase: String
        get() = "https://identitytoolkit.googleapis.com/v1"

    val secureTokenBase: String
        get() = "https://securetoken.googleapis.com/v1"

    val firestoreBase: String
        get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    val storageBase: String
        get() = "https://firebasestorage.googleapis.com/v0/b/$storageBucket/o"

    /** Fully-qualified Firestore document name, as `commit` and reference values require. */
    fun documentName(path: String): String =
        "projects/$projectId/databases/(default)/documents/$path"

    companion object {
        /**
         * A factory rather than a default argument: Kotlin default arguments do not survive into
         * the generated Swift initialiser, so `ViewModel.swift` needs something callable.
         */
        fun fromBuild(): FirebaseConfig = FirebaseConfig(
            apiKey = FirebaseBuildConfig.API_KEY,
            projectId = FirebaseBuildConfig.PROJECT_ID,
            storageBucket = FirebaseBuildConfig.STORAGE_BUCKET,
        )
    }
}
