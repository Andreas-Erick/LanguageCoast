# LiteRT-LM's native library looks Kotlin classes and their getters up by name through JNI
# (e.g. SamplerConfig.getTopP, InputData$Text.getText). R8 must not rename or strip them, or
# creating a conversation aborts the app with "JNI DETECTED ERROR IN APPLICATION: mid == null".
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }

# zstd-jni (reading Anki packages): its native library reads and writes the fields of the stream
# classes by name through JNI, and the AAR ships no rules of its own.
-keep class com.github.luben.zstd.** { *; }
