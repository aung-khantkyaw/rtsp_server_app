# android/app/proguard-rules.pro

# TensorFlow Lite Rules - ပိုမိုပြည့်စုံအောင်ထည့်ပါ
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }

# AutoValue အတွက် Keep Rules
-keep class com.google.auto.value.** { *; }
-keep @interface com.google.auto.value.** { *; }
-dontwarn com.google.auto.value.**

# GPU Delegate အတွက် Keep Rules
-keep class org.tensorflow.lite.gpu.GpuDelegate { *; }
-keep class org.tensorflow.lite.gpu.GpuDelegateFactory { *; }
-keep class org.tensorflow.lite.gpu.GpuDelegateFactory$Options { *; }
-keep class org.tensorflow.lite.gpu.GpuDelegate$Options { *; }
-dontwarn org.tensorflow.lite.**
-dontwarn org.tensorflow.lite.gpu.**

# OpenCV Rules
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue
-dontwarn org.tensorflow.lite.gpu.GpuDelegateFactory$Options

# Keep all classes in your app
-keep class com.example.rtsp_server_app.** { *; }

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep all classes in assets
-keep class * extends java.lang.annotation.Annotation {
    *;
}

# Keep all classes from TensorFlow Lite Support
-keep class org.tensorflow.lite.support.** { *; }
-keepclassmembers class org.tensorflow.lite.support.** { *; }

# Keep AutoValue generated classes
-keep class **$AutoValue_** { *; }
-keep class **$AutoValue_**$Builder { *; }
-keepclassmembers class **$AutoValue_** {
    public static ** create(...);
    public static ** builder(...);
}

# GSON & Data Models rules (Keep your AiState class)
-keep class com.google.gson.** { *; }
-keep class com.example.rtsp_server_app.DetectionManager$AiState { *; }
-keep class com.example.rtsp_server_app.DetectionManager$DetectedObject { *; }