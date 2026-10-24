# R8 / ProGuard rules for release builds.
#
# The app itself is R8-friendly, but a few libraries resolve classes, fields and
# annotations reflectively. Keep those entry points and let R8 shrink everything
# else (including unused Kotlin/Compose code).

# Generic signatures and annotations are read reflectively by Gson, Jackson and
# Room. Dropping them causes silent "no such field" failures at runtime.
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# ---------------------------------------------------------------------------
# Gson: command templates are stored in SharedPreferences as JSON
# (TypeToken<List<CommandTemplate>> in UserPreferences).
# ---------------------------------------------------------------------------
-keep class com.kappi.crunch.data.CommandTemplate { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn sun.misc.Unsafe

# ---------------------------------------------------------------------------
# Room: entities are instantiated and populated by generated DAO code.
# room-runtime ships consumer rules, but keep the entities explicit so a future
# dependency change cannot silently break database reads.
# ---------------------------------------------------------------------------
-keep class com.kappi.crunch.data.DownloadItem { *; }
-keep class com.kappi.crunch.data.PlaylistEntry { *; }

# ---------------------------------------------------------------------------
# WorkManager: workers are constructed reflectively from their class name.
# ---------------------------------------------------------------------------
-keep class com.kappi.crunch.download.DownloadWorker { *; }
-keep class com.kappi.crunch.updater.UpdateWorker { *; }
-keep class com.kappi.crunch.download.DownloadCancelReceiver { *; }

# ---------------------------------------------------------------------------
# youtubedl-android: JSON responses are mapped onto mapper classes through
# Jackson, which reads getters/setters and @JsonProperty names reflectively.
# The library ships no consumer rules, so keep its public surface.
# ---------------------------------------------------------------------------
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.apache.commons.io.**

# ---------------------------------------------------------------------------
# Apache Commons Compress: ZipUtils unpacks the engine zips with it.
# ExtraFieldUtils registers ZipExtraField implementations reflectively and
# calls Class.newInstance() on them; if R8 abstracts or strips any of those
# classes, init fails with "class ... is not a concrete class".
# ---------------------------------------------------------------------------
-keep class org.apache.commons.compress.archivers.zip.** { *; }
