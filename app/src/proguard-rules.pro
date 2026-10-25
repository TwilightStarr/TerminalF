# Shizuku UserService, adıyla (ComponentName) yansıma ile örneklendiği için korunmalı.
-keep class com.terminalf.app.shizuku.** { *; }
-keepclassmembers class com.terminalf.app.shizuku.ShellUserService {
    public <init>();
    public <init>(android.content.Context);
}
# Servis sürecinde (ayrı süreç) kullanılan saf Kotlin yardımcıları.
-keep class com.terminalf.app.core.ShellUtilsKt { *; }
-keep class com.terminalf.app.core.ShellResult { *; }

# Shizuku UserService süreci, Shizuku tarafından komut satırından (adıyla) başlatılır; kodda hiçbir yerden
# çağrılmadığı için kaldırılmaması gerekir. Aksi halde servis süreci açılmaz ve bağlanma zaman aşımına düşer.
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class com.terminalf.app.shizuku.IShellService { *; }
-keep class com.terminalf.app.shizuku.IShellService$Stub { *; }
-keep class com.terminalf.app.shizuku.IShellService$Stub$Proxy { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**
