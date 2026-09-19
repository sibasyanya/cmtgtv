# Keep TDLib JNI and Kotlin reflection
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**

# Keep models and data classes
-keep class com.tgmedia.tv.data.** { *; }
-keepclassmembers class com.tgmedia.tv.data.** { *; }

# Android TV & Compose
-keep class androidx.tv.** { *; }
