# Keep TDLib JNI and Kotlin reflection
-keep class org.drinkless.tdlib.** { *; }
-keepclassmembers class org.drinkless.tdlib.** { *; }
-keepnames class org.drinkless.tdlib.** { *; }
-keepclassmembernames class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep models and data classes
-keep class com.tgmedia.tv.data.** { *; }
-keepclassmembers class com.tgmedia.tv.data.** { *; }

# Android TV & Compose
-keep class androidx.tv.** { *; }
