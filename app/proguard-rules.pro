# pdfbox-android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn java.awt.**
-dontwarn javax.swing.**
-dontwarn org.apache.fontbox.**
-keep class org.apache.fontbox.** { *; }

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room (KSP)
-keep class * extends androidx.room.RoomDatabase
