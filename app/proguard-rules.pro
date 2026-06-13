# Flexmark
-keep class com.vladsch.flexmark.** { *; }
-dontwarn com.vladsch.flexmark.**

# Keep our model classes
-keep class com.md2docx.converter.DocumentModel { *; }
-keep class com.md2docx.converter.DocumentElement { *; }
-keep class com.md2docx.converter.TextSpan { *; }
