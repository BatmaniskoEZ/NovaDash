# Simple-XML uses reflection over the annotated response models.
-keep class com.novadash.net.model.** { *; }
-keepattributes *Annotation*, Signature
-dontwarn org.simpleframework.xml.**
-dontwarn javax.xml.stream.**

# Simple-XML also instantiates its own internals reflectively: LabelExtractor looks up
# Label constructors (ElementLabel, TextLabel, ...) via getConstructor(), which R8 strips
# as unused -> NoSuchMethodException on the first parsed response in release builds.
-keep class org.simpleframework.xml.** { *; }

# libVLC's native side resolves Java classes/methods/fields by name over JNI
# (GetMethodID/GetFieldID) and exit(1)s the whole process when a lookup fails —
# no Java stack trace, ApplicationExitInfo shows EXIT_SELF. The AAR ships no
# consumer rules, so keep everything it may look up.
-keep class org.videolan.** { *; }
