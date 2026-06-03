# ProGuard rules for HELM
-keepattributes *Annotation*
-keep class dev.helm.app.data.model.** { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
