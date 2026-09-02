# kotlinx.serialization: keep generated serializers for our model classes
-keepclassmembers class com.irigoyen.btcalert.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.irigoyen.btcalert.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.irigoyen.btcalert.model.**$$serializer { *; }
-dontwarn org.slf4j.**
