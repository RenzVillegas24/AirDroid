package io.moonbase.droidy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * ADB-only debugging bridge.
 *
 * The K20's KernelSU grant is scoped to the app process, so `adb shell su`
 * does not work. This receiver runs commands as the app (which can invoke
 * `su`) and publishes their output to /data/local/tmp/droidy-debug.txt.
 *
 * The receiver is exported so `adb shell am broadcast` can reach it. It is
 * debug-only and requires a per-install token stored in the app's private
 * files directory; no token is hardcoded in source.
 */
class DebugReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION = "io.moonbase.droidy.DEBUG"
        const val OUTPUT = "/data/local/tmp/droidy-debug.txt"
        @Volatile private var debugBle: BleAdvertiser? = null

        private fun debugToken(context: Context): String {
            val tokenFile = File(context.filesDir, "debug-token")
            if (!tokenFile.exists()) {
                tokenFile.parentFile?.mkdirs()
                tokenFile.writeText(UUID.randomUUID().toString())
            }
            return tokenFile.readText().trim()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        if (intent.getStringExtra("token") != debugToken(context)) return
        if (intent.getBooleanExtra("ble", false)) {
            val advertiser = debugBle ?: BleAdvertiser(context).also { debugBle = it }
            val arm = intent.getIntExtra("arm", 0)
            advertiser.restart(arm, true) { Log.i("DroidyDebug", "BLE $it") }
        }
        val command = intent.getStringExtra("cmd")
            ?: intent.getStringExtra("b64")?.let {
                String(Base64.decode(it, Base64.NO_WRAP))
            }
            ?: return
        val timeout = intent.getIntExtra("timeout", 20).coerceIn(1, 180)
        val pending = goAsync()

        Thread {
            val result = StringBuilder()
            try {
                val process = ProcessBuilder("su", "-c", command)
                    .redirectErrorStream(true)
                    .start()
                process.outputStream.close()
                val reader = Thread {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            synchronized(result) {
                                result.append(line).append('\n')
                            }
                        }
                    }
                }
                reader.start()
                val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
                if (!finished) process.destroyForcibly()
                reader.join(2000)
                result.append("[exit ").append(if (finished) process.exitValue() else "TIMEOUT").append("]\n")
            } catch (e: Exception) {
                result.append("ERROR: ").append(e.message).append('\n')
            }
            writeOutput(context, result.toString())
            Log.i("DroidyDebug", result.toString())
            pending.finish()
        }.start()
    }

    private fun writeOutput(context: Context, text: String) {
        try {
            val external = File(context.getExternalFilesDir(null), "debug.txt")
            external.parentFile?.mkdirs()
            external.writeText(text)
        } catch (_: Exception) {
        }
        try {
            val process = ProcessBuilder(
                "su",
                "-c",
                "cat > $OUTPUT; chmod 644 $OUTPUT",
            ).start()
            process.outputStream.use { it.write(text.toByteArray()) }
            process.waitFor(5, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
    }
}
