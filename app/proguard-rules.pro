# Keep JS bridge for WebView — annotation-only form is robust under future refactors
-keepclasseswithmembers class com.cobalt.android.CobaltJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
