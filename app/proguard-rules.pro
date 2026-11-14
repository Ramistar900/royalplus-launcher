# sherpa-onnx reads Kotlin config fields by their exact JVM names through JNI.
# Keep native-recognizer classes and fields in minified release APKs.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep interface com.k2fsa.sherpa.onnx.** { *; }
