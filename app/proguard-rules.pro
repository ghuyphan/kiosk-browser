# WebView callbacks are referenced by the Android framework.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

