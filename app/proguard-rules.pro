# MVP 阶段 release 不开启混淆（isMinifyEnabled=false），此文件留作以后启用时的规则入口
# kotlinx-serialization 的序列化类需要保留
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.lewis.phoneagent.**$$serializer { *; }
-keepclassmembers class com.lewis.phoneagent.** { *** Companion; }
-keepclasseswithmembers class com.lewis.phoneagent.** { kotlinx.serialization.KSerializer serializer(...); }
