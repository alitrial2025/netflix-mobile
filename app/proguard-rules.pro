# Preserve only reflection/serialization boundaries; app logic remains obfuscated.
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
# Catalog/offline models use Moshi's Kotlin reflection adapter.
-keep class com.example.data.model.MediaItem { *; }
-keep class com.example.data.model.Episode { *; }
-keep class com.example.data.model.MediaType { *; }
-keep class com.example.data.download.SavedDownloadRequest { *; }
-keep class com.example.data.download.DownloadTaskInfo { *; }

-keep class com.example.data.download.DownloadTaskStatus { *; }
