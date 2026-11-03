# LiteRT-LM's native library looks Kotlin classes and their getters up by name through JNI
# (e.g. SamplerConfig.getTopP, InputData$Text.getText). R8 must not rename or strip them, or
# creating a conversation aborts the app with "JNI DETECTED ERROR IN APPLICATION: mid == null".
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** { *; }
