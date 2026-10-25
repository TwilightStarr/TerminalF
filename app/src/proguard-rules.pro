# Shizuku UserService, adıyla (ComponentName) yansıma ile örneklendiği için korunmalı.
-keep class com.terminalf.app.shizuku.** { *; }
-keepclassmembers class com.terminalf.app.shizuku.ShellUserService {
    public <init>();
    public <init>(android.content.Context);
}
# Servis sürecinde (ayrı süreç) kullanılan saf Kotlin yardımcıları.
-keep class com.terminalf.app.core.ShellUtilsKt { *; }
-keep class com.terminalf.app.core.ShellResult { *; }
