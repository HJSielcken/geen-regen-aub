package org.theharmen.geenregenaub

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * A crash log on the phone itself, next to Sentry, so a crash can be seen even when no report
 * reaches Sentry (a crash before Sentry starts, a native crash in MapLibre, or no network).
 * Open it with the "Crashrapport" shortcut on the app icon.
 */
object CrashLog {
    private const val FILE = "last-crash.txt"
    private const val PREFS = "crash-log"
    private const val SEEN_EXIT = "seen-exit-timestamp"

    fun file(context: Context): File = File(context.filesDir, FILE)

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }

    /** Writes every uncaught exception to the log, then lets the previous handler (Sentry, Android) run. */
    fun install(context: Context) {
        val app = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }
                write(app, "Crash in thread \"${thread.name}\"\n\n$trace")
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Notes how the app ended last time, when that was a crash Android saw but our handler could
     * not (native code, or the system killed it). Android 11 and newer keep this history.
     */
    fun recordPreviousExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return
            val exit = manager.getHistoricalProcessExitReasons(context.packageName, 0, 1).firstOrNull() ?: return
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (exit.timestamp <= prefs.getLong(SEEN_EXIT, 0)) return
            prefs.edit().putLong(SEEN_EXIT, exit.timestamp).apply()
            val reason = when (exit.reason) {
                ApplicationExitInfo.REASON_CRASH -> "Java crash"
                ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash (signal ${exit.status})"
                ApplicationExitInfo.REASON_ANR -> "app reageerde niet (ANR)"
                ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "opstarten mislukt"
                ApplicationExitInfo.REASON_LOW_MEMORY -> "te weinig geheugen"
                else -> return
            }
            val description = exit.description?.let { "\n$it" } ?: ""
            val time = Instant.ofEpochMilli(exit.timestamp)
            // A Java crash is already in the log with its stack trace; add the system's view below it.
            val earlier = if (exit.reason == ApplicationExitInfo.REASON_CRASH) read(context)?.let { "$it\n\n" } ?: "" else ""
            write(context, "${earlier}Vorige keer afgesloten door: $reason om $time$description", stamp = earlier.isEmpty())
        }
    }

    private fun write(context: Context, text: String, stamp: Boolean = true) {
        val header = if (stamp) {
            "Geen regen aub ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "${Instant.now()}\n\n"
        } else {
            ""
        }
        file(context).writeText(header + text)
    }
}
