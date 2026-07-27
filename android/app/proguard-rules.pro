# Google API Client — keep all model/service classes used via reflection
-keep class com.google.api.** { *; }
-keep class com.google.api.client.** { *; }
-keep class com.google.api.services.sheets.** { *; }
-keep class com.google.auth.** { *; }
-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses

# Jackson — JSON serialization/deserialization
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**

# Google HTTP / OAuth
-dontwarn com.google.api.client.**
-dontwarn com.google.auth.**
-dontwarn org.apache.http.**
-dontwarn android.net.http.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
