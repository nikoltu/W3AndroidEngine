# The engine and its parsers use reflection (Gson, JASS natives, generated parsers), so their
# classes are kept whole; R8 only removes unused library code.
-dontobfuscate
-keep class com.etheller.** { *; }
-keep class com.hiveworkshop.** { *; }
-keep class com.google.gson.** { *; }
-keep class org.antlr.v4.runtime.** { *; }

# libGDX backend classes are created from native code.
-keep class com.badlogic.gdx.backends.android.** { *; }
-keep class com.badlogic.gdx.physics.box2d.** { *; }
-keep class com.badlogic.gdx.graphics.g2d.freetype.** { *; }
-keepclassmembers class com.badlogic.gdx.** { native <methods>; }
# libGDX pools create their objects through the no-argument constructor by reflection.
-keepclassmembers class com.badlogic.gdx.** { <init>(); }

-dontwarn javax.annotation.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.checkerframework.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.sound.**
-dontwarn org.lwjgl.**
-dontwarn sun.misc.**
