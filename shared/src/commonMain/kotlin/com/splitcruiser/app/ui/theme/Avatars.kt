package com.splitcruiser.app.ui.theme

/**
 * The avatars a user can pick for their profile.
 *
 * One list, read by both platforms — and, on Android, by both the picker and the renderer. The
 * keys used to be written out three times (twice in `SplitCruiserApp.kt`, once in
 * `DesignPrimitives.swift`), which is exactly the duplication `.claude/DESIGN_SYSTEM.md` §1 warns
 * about: change the set in one place and the other two quietly disagree.
 *
 * The key is stored *as* the user's `avatarUrl` string — there is no separate field — so
 * [StudentAvatar] on each platform resolves in this order: an `http` URL (an uploaded photo),
 * then one of these keys, then a [LEGACY_PRESETS] key, then the initial fallback.
 *
 * Keys are numbered rather than named after who they depict. A user never sees a key
 * (§2 rule 2: never show an internal identifier), and numbering keeps the data from asserting
 * anything about the person who picked it.
 */
object SplitCruiserAvatars {

    /**
     * Every selectable avatar, in display order. The comment on each is for whoever regenerates
     * the artwork — it describes what `scripts/generate-avatars.py` draws, nothing more.
     */
    val ALL: List<String> = listOf(
        "avatar_01", // young woman, long dark hair
        "avatar_02", // young man, short hair
        "avatar_03", // woman, curly hair, mid-tone skin
        "avatar_04", // man, beard, mid-tone skin
        "avatar_05", // woman, headscarf
        "avatar_06", // man, turban
        "avatar_07", // older woman, grey hair, glasses
        "avatar_08", // older man, grey beard
        "avatar_09", // teenage girl, ponytail
        "avatar_10", // teenage boy, cap
        "avatar_11", // woman, short afro, deep skin tone
        "avatar_12", // man, locs, deep skin tone
    )

    /**
     * The six object emoji this replaced.
     *
     * Kept, and still resolved, because the key lives in `avatarUrl` on the user document —
     * anyone who picked one of these before the change still has it stored, and dropping the
     * branch would turn their avatar into a bare letter.
     */
    val LEGACY_PRESETS: Map<String, String> = mapOf(
        "preset_grad" to "🎓",
        "preset_driver" to "🚗",
        "preset_tech" to "💻",
        "preset_explorer" to "🎒",
        "preset_star" to "⭐",
        "preset_globe" to "🌐",
    )

    /** True when [avatarUrl] names one of [ALL]. */
    fun isAvatarKey(avatarUrl: String): Boolean = avatarUrl in ALL

    /** The emoji for a legacy key, or null if this is not one. */
    fun legacyEmoji(avatarUrl: String): String? = LEGACY_PRESETS[avatarUrl]

    /**
     * What a screen reader announces. Neutral by design — the artwork varies, but the app has no
     * business telling a user which of these they "are".
     */
    fun accessibilityLabel(avatarUrl: String): String {
        val index = ALL.indexOf(avatarUrl)
        return if (index >= 0) "Avatar ${index + 1} of ${ALL.size}" else "Profile picture"
    }
}
