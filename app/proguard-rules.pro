# Proguard Rules for Smart Parking - Anti-Reverse Engineering & Obfuscation

# Optimization & Obfuscation Tweaks
-repackageclasses ''
-allowaccessmodification
-mergeinterfacesaggressively

# Strip all debug logs in release build so no secrets or internal logic leak into Logcat
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# SUNMI AIDL Services (Required for hardware printer communication)
-keep class woyou.aidlservice.jiuiv5.** { *; }
-keep interface woyou.aidlservice.jiuiv5.** { *; }

# Room Database Entities and DAOs
-keep class * extends androidx.room.RoomDatabase
-keep class com.smartparking.app.data.local.entity.** { *; }
-keep interface com.smartparking.app.data.local.dao.** { *; }
-dontwarn androidx.room.paging.**

# SecurityGuard
-keep class com.smartparking.app.security.SecurityGuard {
    public static com.smartparking.app.security.SecurityGuard$SecurityStatus verifyAppIntegrity(android.content.Context, boolean);
    public static java.lang.String computeLicenseHmac(java.lang.String, java.lang.String, java.lang.String);
}

# ZXing QR Code
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
