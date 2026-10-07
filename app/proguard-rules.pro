-keep class io.github.ssebanom.ling.engine.** { *; }

# JLatexMath(Markwon LaTeX): 매크로를 리플렉션으로 찾고 폰트/설정 XML 을 클래스 기준 경로로 읽는다 → 이름·패키지 유지
-keep class org.scilab.forge.jlatexmath.** { *; }
-keep class ru.noties.jlatexmath.** { *; }
-dontwarn org.scilab.forge.jlatexmath.**
-dontwarn ru.noties.jlatexmath.**
