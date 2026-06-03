-keep class com.cobalt.android.CobaltJsBridge { *; }
-keepclassmembers class com.cobalt.android.CobaltJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
