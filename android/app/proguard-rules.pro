# Preserve names of the WebView JavaScript-interface methods. The WebView
# resolves @JavascriptInterface methods by reflection on their declared name,
# so R8 must not rename or strip them (minifyEnabled is on for all build types).
-keepclassmembers class art.tessell.editor.android.WebAppInterface {
    @android.webkit.JavascriptInterface <methods>;
}

# Readable stack traces from R8-minified debug builds.
-keepattributes SourceFile,LineNumberTable

-dontwarn java.net.http.HttpTimeoutException
