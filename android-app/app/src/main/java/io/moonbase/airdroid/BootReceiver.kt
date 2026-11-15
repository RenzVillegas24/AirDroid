package io.moonbase.airdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
                if (transferFlagPresent()) {
                    Log.i("AirDroidLog", "boot: Transfer-mode flag present, starting service")
                    AirDroidService.startBootEnter(context)
                }
            } catch (e: Exception) {
                Log.w("AirDroidLog", "boot: could not apply Transfer-mode flag: ${e.message}")
            } finally {
                pending.finish()
            }
        }.start()
    }

    /** Reads /data/adb/owly-transfer-mode through root. */
    private fun transferFlagPresent(): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", "test -e /data/adb/owly-transfer-mode")
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
}
