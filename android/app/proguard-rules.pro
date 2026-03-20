# Proguard rules for Object Language App
# Keep Retrofit models
-keepclassmembers class com.duc.objectlanguage.data.api.** { *; }
-keepclassmembers class com.duc.objectlanguage.data.model.** { *; }

# Keep Room entities
-keep class com.duc.objectlanguage.data.db.** { *; }
