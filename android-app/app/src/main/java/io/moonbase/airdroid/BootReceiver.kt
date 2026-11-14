package io.moonbase.airdroid

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.concurrent.TimeUnit

/**
 * Applies the Transfer-mode boot flag after Android has finished booting.
 *
 * KernelSU's service.d scripts are not executed on this K20 build, so the
 * reboot-enter path uses a normal BOOT_COMPLETED receiver with root instead.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        Thread {
            try {
                val command = """
                    FLAG=/data/adb/owly-transfer-mode
                    CON_MODE=/sys/module/wlan/parameters/con_mode
                    LOG=/data/local/tmp/owly-mode-boot.log
                    [ -e "${'$'}FLAG" ] || exit 0
                    echo "boot receiver started ${'$'}(date 2>/dev/null)" >> "${'$'}LOG"
                    svc wifi disable
                    sleep 2
                    ip link set wlan0 down 2>/dev/null
                    echo 4 > "${'$'}CON_MODE" 2>> "${'$'}LOG"
                    for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do
                        [ "${'$'}(cat /sys/class/net/wlan0/type 2>/dev/null)" = "803" ] && break
                        sleep 1
                    done
                    ip link set wlan0 up 2>/dev/null
                    echo "boot mode=${'$'}(cat "${'$'}CON_MODE" 2>/dev/null) type=${'$'}(cat /sys/class/net/wlan0/type 2>/dev/null)" >> "${'$'}LOG"
                """.trimIndent()
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                process.inputStream.bufferedReader().use { it.readText() }
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                }
            } catch (_: Exception) {
                // The next app launch still verifies con_mode and can fall back
                // to the live switch.
            } finally {
                pending.finish()
            }
        }.start()
    }
}
