# R8 configuration for the release build.
#
# The release build is minified and resource-shrunk (see app/build.gradle.kts). Before that, the
# shipped AAB was fully symbolised — every class name, the Firebase identifiers, and the app's own
# logic were trivially readable with `strings`/`apktool`. These rules keep the pieces that break
# under obfuscation/shrinking (kotlinx.serialization's generated serializers, the Ktor REST client,
# coroutines) and let R8 rename and strip the rest.
#
# Keep source/line info but strip the original file name, so a Play-symbolicated crash stays
# readable while the class names it points at are obfuscated.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# kotlinx.serialization
# The models are (de)serialised through generated `$$serializer` classes and their companions.
# Losing them turns every Firestore read/write into a runtime crash. These are the rules from the
# kotlinx.serialization project, scoped to this app's data package plus the library's own json types.
# ---------------------------------------------------------------------------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.splitcruiser.app.**$$serializer { *; }
-keepclassmembers class com.splitcruiser.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.splitcruiser.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------------------------------------------------------------------------
# Ktor + coroutines
# The REST backend runs on Ktor with the OkHttp engine. Ktor resolves engines and does enough
# reflective/service-loader work that shrinking it is fragile; keep it and coroutines intact rather
# than chase individual keeps. R8 still obfuscates and strips the app's own code around them.
# ---------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**

# Jetpack Security (EncryptedSharedPreferences) pulls in Tink, which references optional providers.
-dontwarn com.google.crypto.tink.**
