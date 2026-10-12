-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keep class androidx.webkit.** { *; }
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn javax.annotation.concurrent.GuardedBy
