# MSAL
-keep class com.microsoft.identity.** { *; }
-keep interface com.microsoft.identity.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
