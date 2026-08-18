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
    /**
     * The OAuth 2.0 **Web** client ID, or empty when Google sign-in has not been set up.
     *
     * Only the platform half of the flow needs it — it is what the Google ID token is minted for.
     * The token exchange in [com.splitcruiser.app.data.firebase.FirebaseAuthClient] does not, since
     * Identity Toolkit infers the audience from the API key.
     */
    val googleWebClientId: String = "",
    /**
     * The Firestore database id within [projectId].
     *
     * Defaults to `"splitcruiser"`, not the `"(default)"` every project gets automatically —
     * because that is the id this project's database actually has. The console's "Create
     * database" dialog has a Database ID field that accepts anything typed over its suggestion,
     * with no warning that doing so means every `databases/(default)/...` REST call this app
     * makes will 404 against a database that, from the API's point of view, does not exist. A
     * project that creates a fresh `(default)` database instead can override this back with
     * `FIRESTORE_DATABASE_ID=(default)`.
     */
    val firestoreDatabaseId: String = "splitcruiser",
    /**
     * Google Places (New) API key for address autocomplete, or empty to use the free Photon/OSM
     * search instead. Metered when set: every autocomplete and place-details call is billed, which
     * is why the empty-key fallback exists and is the default. Consumed only by the location search
     * layer ([com.splitcruiser.app.data.OsmLocationService]), never by any Firebase endpoint.
     */
    val mapsApiKey: String = "",
) {
    /** Whether Google Places autocomplete is configured. When false, address search uses Photon. */
    val isMapsAutocompleteEnabled: Boolean
        get() = mapsApiKey.isNotBlank() && !mapsApiKey.contains("PLACEHOLDER")

    /**
     * False when a value is missing or is still the placeholder committed in `.env.example`.
     *
     * The old Android repository used the same test to decide whether to fall back to a local JSON
     * store. That fallback is gone — Firebase is the only backend now — so an unconfigured build
     * must say so plainly instead of appearing to work.
     */
    val isConfigured: Boolean
        get() = listOf(apiKey, projectId).all { it.isNotBlank() && !it.contains("PLACEHOLDER") }

    /**
     * Whether to offer the Google button at all. Showing it without a client ID produces a
     * `DEVELOPER_ERROR` from Play Services at the tap, which says nothing to a user.
     */
    val isGoogleSignInConfigured: Boolean
        get() = isConfigured && googleWebClientId.isNotBlank() &&
            !googleWebClientId.contains("PLACEHOLDER")

    val identityBase: String
        get() = "https://identitytoolkit.googleapis.com/v1"

    val secureTokenBase: String
        get() = "https://securetoken.googleapis.com/v1"

    val firestoreBase: String
        get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/$firestoreDatabaseId/documents"

    val storageBase: String
        get() = "https://firebasestorage.googleapis.com/v0/b/$storageBucket/o"

    /** Fully-qualified Firestore document name, as `commit` and reference values require. */
    fun documentName(path: String): String =
        "projects/$projectId/databases/$firestoreDatabaseId/documents/$path"

    companion object {
        /**
         * A factory rather than a default argument: Kotlin default arguments do not survive into
         * the generated Swift initialiser, so `ViewModel.swift` needs something callable.
         */
        fun fromBuild(): FirebaseConfig = FirebaseConfig(
            apiKey = FirebaseBuildConfig.API_KEY,
            projectId = FirebaseBuildConfig.PROJECT_ID,
            storageBucket = FirebaseBuildConfig.STORAGE_BUCKET,
            googleWebClientId = FirebaseBuildConfig.GOOGLE_WEB_CLIENT_ID,
            firestoreDatabaseId = FirebaseBuildConfig.FIRESTORE_DATABASE_ID,
            mapsApiKey = FirebaseBuildConfig.MAPS_API_KEY,
        )
    }
}
