# R8 rules for Dialtone release builds.

# --- Visual voicemail engine (app.dialtone.vvm) ---
# Tasks are stored in intents/jobs by class name and recreated with
# Class.forName(name).getDeclaredConstructor().newInstance(). Keep their names and
# no-arg constructors stable, including across app updates with jobs still queued.
-keep class * implements app.dialtone.vvm.scheduling.Task {
    <init>();
}
-keep @app.dialtone.vvm.compat.UsedByReflection class * { *; }
-keepclassmembers class * {
    @app.dialtone.vvm.compat.UsedByReflection *;
}

# --- Apache Mime4j (IMAP message parsing) and its commons-io dependency ---
# Field parsers are looked up at runtime; keep the library intact and silence
# warnings for optional JVM-only references.
-keep class org.apache.james.mime4j.** { *; }
-dontwarn org.apache.james.mime4j.**
-dontwarn org.apache.commons.io.**
