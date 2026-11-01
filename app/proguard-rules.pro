# Proguard Rules for Smart Parking

# SUNMI AIDL Services
-keep class woyou.aidlservice.jiuiv5.** { *; }
-keep interface woyou.aidlservice.jiuiv5.** { *; }

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Retrofit / Moshi / JSON
-keepclassmembers,allowobfuscation class * {
    @com.squareup.moshi.Json(name = *) <fields>;
}

# ZXing QR Code
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
