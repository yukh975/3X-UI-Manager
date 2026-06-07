# --- kotlinx.serialization ------------------------------------------------
# Official rules from github.com/Kotlin/kotlinx.serialization#android.
# Required because R8 strips $serializer classes and Companion lookups
# otherwise.

-keepattributes *Annotation*, InnerClasses, RuntimeVisibleAnnotations, AnnotationDefault
-dontnote kotlinx.serialization.AnnotationsKt

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep generated serializer fields/methods for our DTOs so R8 doesn't drop them.
-keep,includedescriptorclasses class net.yukh.xui.**$$serializer { *; }
-keepclasseswithmembers class net.yukh.xui.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# --- Retrofit + OkHttp ----------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclasseswithmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# --- Hilt is handled by its own consumer rules; nothing custom needed -----
