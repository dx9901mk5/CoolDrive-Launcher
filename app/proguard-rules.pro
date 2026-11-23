# DX-Car Launcher ProGuard / R8 Rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.dxcar.launcher.comms.** { *; }
-keep class com.dxcar.launcher.data.** { *; }
-dontwarn org.maplibre.**
