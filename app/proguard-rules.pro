# R8 rules for Dialtone release builds.

# --- Visual voicemail engine (app.dialtone.phone.vvm) ---
# Tasks are stored in intents/jobs by class name and recreated with
# Class.forName(name).getDeclaredConstructor().newInstance(). Keep their names and
# no-arg constructors stable, including across app updates with jobs still queued.
-keep class * implements app.dialtone.phone.vvm.scheduling.Task {
    <init>();
}
-keep @app.dialtone.phone.vvm.compat.UsedByReflection class * { *; }
-keepclassmembers class * {
    @app.dialtone.phone.vvm.compat.UsedByReflection *;
}

# --- Apache Mime4j (IMAP message parsing) and its commons-io dependency ---
# Field parsers are looked up at runtime; keep the library intact and silence
# warnings for optional JVM-only references.
-keep class org.apache.james.mime4j.** { *; }
-dontwarn org.apache.james.mime4j.**
-dontwarn org.apache.commons.io.**

# --- Room (inside WorkManager, which Glance uses for widget updates) ---
# Room creates each database's generated _Impl class by reflection with its no-arg constructor.
# R8 no longer keeps default constructors on its own; without them WorkManager failed to start
# and took the whole app down at launch (v0.20.0). Cheap insurance on top of the newer WorkManager.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
