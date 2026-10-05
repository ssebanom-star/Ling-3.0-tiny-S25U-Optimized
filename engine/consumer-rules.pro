# JNI 콜백 인터페이스/네이티브 메서드 유지
-keep class io.github.ssebanom.ling.engine.LingNative { *; }
-keep interface io.github.ssebanom.ling.engine.NativeCallbacks$* { *; }
-keep class * implements io.github.ssebanom.ling.engine.NativeCallbacks$* { *; }
