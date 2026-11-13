# sherpa-onnx reads Kotlin config fields by their exact JVM names through JNI
# (for example OfflineRecognizerConfig.decodingMethod). Keep class names and fields.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep interface com.k2fsa.sherpa.onnx.** { *; }
