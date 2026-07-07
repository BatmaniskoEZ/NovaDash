# Simple-XML uses reflection over the annotated response models.
-keep class com.novadash.net.model.** { *; }
-keepattributes *Annotation*
-dontwarn org.simpleframework.xml.**
-dontwarn javax.xml.stream.**
