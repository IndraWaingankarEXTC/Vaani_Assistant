# Accessibility + notification listener services are looked up by name from the manifest.
-keep class com.vaani.assistant.ui.AccessibilityAgent { *; }
-keep class com.vaani.assistant.exec.VaaniNotificationListener { *; }
-keep class com.vaani.assistant.voice.VoiceService { *; }
-keep class com.vaani.assistant.voice.BootReceiver { *; }
# Native LLM bridge: symbols are resolved by JNI at runtime.
-keepclasseswithmembernames class com.vaani.assistant.llm.TinyLlmBridge { native <methods>; }
