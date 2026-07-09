# Simple-XML uses reflection over the annotated response models.
-keep class com.novadash.net.model.** { *; }
-keepattributes *Annotation*, Signature
-dontwarn org.simpleframework.xml.**
-dontwarn javax.xml.stream.**

# Simple-XML also instantiates its own internals reflectively: LabelExtractor looks up
# Label constructors (ElementLabel, TextLabel, ...) via getConstructor(), which R8 strips
# as unused -> NoSuchMethodException on the first parsed response in release builds.
-keep class org.simpleframework.xml.** { *; }
