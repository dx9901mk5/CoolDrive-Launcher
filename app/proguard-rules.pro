# CoolDrive Launcher ProGuard / R8 Rules
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-keep class com.cooldrive.launcher.comms.** { *; }
-keep class com.cooldrive.launcher.data.** { *; }
-dontwarn org.maplibre.**
