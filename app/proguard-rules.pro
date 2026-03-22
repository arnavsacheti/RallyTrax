# RallyTrax ProGuard Rules

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rallytrax.app.**$$serializer { *; }
-keepclassmembers class com.rallytrax.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.rallytrax.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Navigation route classes (serializable data classes)
-keep @kotlinx.serialization.Serializable class com.rallytrax.app.navigation.** { *; }

# DataStore
-keep class androidx.datastore.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# Google Maps
-keep class com.google.android.gms.maps.** { *; }
-keep class com.google.maps.android.** { *; }

# osmdroid (OpenStreetMap fallback)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep enum classes used in preferences
-keepclassmembers enum com.rallytrax.app.data.preferences.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep enum classes used in entities
-keepclassmembers enum com.rallytrax.app.data.local.entity.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# UpdateChecker JSON parsing
-keep class com.rallytrax.app.update.** { *; }

# Firebase Auth
-keep class com.google.firebase.auth.** { *; }

# Credential Manager
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Google Drive API / HTTP Client
-keep class com.google.api.services.drive.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.http.client.** { *; }
-dontwarn com.google.api.client.**
-dontwarn com.google.http.client.**
-dontwarn org.apache.http.**

# Coil
-keep class coil.** { *; }
-dontwarn coil.**

# Keep sync data classes for serialization
-keep @kotlinx.serialization.Serializable class com.rallytrax.app.data.sync.** { *; }

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
