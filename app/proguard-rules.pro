# MCP_Run ProGuard Rules

# Keep JSON classes (used by MCP protocol)
-keep class org.json.** { *; }

# Keep our MCP tool classes
-keep class com.mcp_run.** { *; }
-keep class com.mcp_run.tools.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

# Keep service classes
-keep class * extends android.app.Service { *; }

# Keep Runnable implementations
-keep class * implements java.lang.Runnable { *; }