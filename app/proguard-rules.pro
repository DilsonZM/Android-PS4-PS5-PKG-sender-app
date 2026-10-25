# Keep Kotlin serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.remotesend.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.remotesend.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
