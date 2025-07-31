# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ===================================================================
# Fix for missing desktop Java classes in Android environment
# ===================================================================

# Ignore warnings for desktop Java AWT classes (not available on Android)
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**

# Ignore warnings for Apache XML Graphics classes that reference desktop APIs
-dontwarn org.apache.xmlgraphics.**

# Ignore warnings for missing service classes
-dontwarn org.w3c.dom.DOMImplementationSourceList
-dontwarn org.xml.sax.driver

# Keep sora-editor classes
-keep class io.github.rosemoe.sora.** { *; }
-keep class org.eclipse.tm4e.** { *; }

# Keep LSP related classes
-keep class com.acc_ide.lsp.** { *; }

# Keep TextMate related classes
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Keep class names for reflection (if needed)
-keepnames class * implements java.io.Serializable

# ===================================================================
# Additional rules for third-party libraries
# ===================================================================

# OkHttp
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Kotlin interface default implementations (R8 generated)
-dontwarn kotlin.Cloneable$DefaultImpls

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response