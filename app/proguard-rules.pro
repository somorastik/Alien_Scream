# Keep line numbers and source file names for better stack traces in Play Console
-keepattributes SourceFile,LineNumberTable

# Keep signatures for reflection and debugging
-keepattributes Signature

# Keep annotations and inner classes metadata
-keepattributes *Annotation*,InnerClasses,EnclosingMethod

# Specifically keep JNI methods and their descriptors
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
