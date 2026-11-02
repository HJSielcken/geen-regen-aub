# R8 rules for release builds. AndroidX (Compose, Glance, WorkManager, DataStore), MapLibre,
# kotlinx.serialization: our @Serializable models are found through their companion's
# serializer(), so keep that and the generated $$serializer classes.
-keepclassmembers @kotlinx.serialization.Serializable class org.theharmen.geenregenaub.** {
    static ** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class org.theharmen.geenregenaub.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class org.theharmen.geenregenaub.**$$serializer { *; }

# WorkManager creates workers by class name from its database, so a renamed worker would
# orphan the scheduled job after an update.
-keep class org.theharmen.geenregenaub.** extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
