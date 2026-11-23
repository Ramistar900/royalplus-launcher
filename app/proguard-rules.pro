# sherpa-onnx reads Kotlin config fields by their exact JVM names through JNI.
# Keep native-recognizer classes and fields in minified release APKs.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep interface com.k2fsa.sherpa.onnx.** { *; }

# ONNX Runtime exposes native methods through JNI.
-keep class ai.onnxruntime.** { *; }
-keep interface ai.onnxruntime.** { *; }

# Android 7 ART rejects an R8-optimized Compose lambda with a float/int register type merge.
# Keep optimization disabled for this exact generated home-screen lambda only.
-keep class com.royalplus.launcher.RoyalHomeScreenKt$RoyalHomeScreen$17$1$3 { *; }
# Android 7 (API 24) can reject R8-optimized generated Compose lambdas from this screen.
# Keep its generated lambda classes stable until the minified API 24 smoke test passes.
-keep class com.royalplus.launcher.RoyalHomeScreenKt$* { *; }
