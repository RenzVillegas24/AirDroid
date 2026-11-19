package io.moonbase.crosspair

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Applies the Transfer-mode boot flag after Android has finished booting.
 *
 * KernelSU's service.d scripts are not executed on this K20 build, so the
 * reboot-enter path uses a normal BOOT_COMPLETED receiver. The receiver never
 * writes con_mode itself: it starts the foreground service, which enters
 * through the normal owner/watchdog-armed path.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                // KernelSU may not expose `su` yet when BOOT_COMPLETED lands.
                var present = false
                for (attempt in 1..FLAG_ATTEMPTS) {
                    present = transferFlagPresent(context)
                    if (present) break
                    Thread.sleep(FLAG_RETRY_MS)
                }
                Log.i("CrossPairLog", "boot: Transfer-mode flag present=$present")
                if (present) {
                    // One shot. The flag is consumed even if the switch fails,
                    // otherwise every later reboot would enter Transfer mode
                    // without anyone asking for it.
                    transferFlagClear(context)
                    Log.i("CrossPairLog", "boot: flag cleared, starting service")
                    CrossPairService.startBootEnter(context)
                }
            } catch (e: Exception) {
                Log.w("CrossPairLog", "boot: could not apply Transfer-mode flag: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** Reads the one-shot boot-enter flag in the app files through root. */
    private fun transferFlagPresent(context: Context): Boolean =
        rootTest("test -e '${flagFile(context).absolutePath}'")

    /** Removes the one-shot flag; harmless when it is already gone. */
    private fun transferFlagClear(context: Context) {
        rootTest("rm -f '${flagFile(context).absolutePath}'")
    }

    private fun flagFile(context: Context): File = File(context.filesDir, "owly/boot-enter")

    private fun rootTest(script: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", script)
                .redirectErrorStream(true)
                .start()
            if (process.waitFor(10, TimeUnit.SECONDS)) {
                process.exitValue() == 0
            } else {
                process.destroyForcibly()
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    private companion object {
        const val FLAG_ATTEMPTS = 5
        const val FLAG_RETRY_MS = 2_000L
    }
}
