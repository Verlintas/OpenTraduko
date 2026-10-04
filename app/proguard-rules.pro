# Vosk / Kaldi
-keep class org.vosk.** { *; }
-dontwarn org.vosk.**

# JNA (used by Vosk for native bindings)
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.** { public *; }
-dontwarn com.sun.jna.**
-dontwarn java.awt.**

# ML Kit
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
