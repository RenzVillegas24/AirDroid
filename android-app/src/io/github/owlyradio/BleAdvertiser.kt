package io.github.owlyradio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import java.security.SecureRandom

/**
 * Sends the Apple Continuity AirDrop BLE advertisement.
 *
 * Owly's `owly-airdrop/src/proto/ble.rs` supplies the exact 18-byte payload:
 *   Continuity type 0x05, length 0x12,
 *   0x40 + 3-byte tag, 4 zero bytes, 0x03,
 *   four 2-byte contact hashes (zero = no contact match),
 *   trailing 0.
 *
 * Apple receivers in Everyone mode wake/publish their AirDrop service after
 * seeing this. The K20 uses BLE only for the wake advertisement; AWDL and
 * AirDrop traffic stay on the WCN3990 radio.
 *
 * Only one advertisement may be registered at a time. `onCreate` and a debug
 * `send` can otherwise race and register two adverts with different tags;
 * `restart` gives every send its own fresh tag, as Owly does (`BLE-5`).
 */
class BleAdvertiser(private val context: Context) {
    private var advertiser: BluetoothLeAdvertiser? = null
    @Volatile
    private var running = false
    private var starting = false
    private var generation = 0
    private val random = SecureRandom()

    @Synchronized
    fun isRunning(): Boolean = running

    @Synchronized
    fun start(arm: Int = 0, connectable: Boolean = true, onLog: (String) -> Unit): Boolean {
        if (running || starting) return true

        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter == null) {
            onLog("BLE: adapter not available")
            return false
        }
        if (!adapter.isEnabled) {
            onLog("BLE: adapter is off")
            return false
        }

        val le = adapter.bluetoothLeAdvertiser
        if (le == null) {
            onLog("BLE: LE advertiser unavailable")
            return false
        }

        val tag = ByteArray(3).also { random.nextBytes(it) }
        val hashes = ByteArray(8)
        val body = when (arm) {
            1 -> byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0x02) +
                hashes + byteArrayOf(0)
            2 -> byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0, 0x01) +
                hashes + byteArrayOf(0)
            else -> byteArrayOf(0x40.toByte(), tag[0], tag[1], tag[2],
                0, 0, 0, 0, 0x03) + hashes + byteArrayOf(0)
        }
        val payload = byteArrayOf(0x05, 0x12) + body
        val thisGeneration = ++generation

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(connectable)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(0x004C, payload)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                synchronized(this@BleAdvertiser) {
                    if (thisGeneration != generation) return
                    starting = false
                    running = true
                }
                onLog("BLE: AirDrop arm=$arm connectable=$connectable tag=${tag.joinToString("") { "%02x".format(it) }} started")
            }

            override fun onStartFailure(errorCode: Int) {
                synchronized(this@BleAdvertiser) {
                    if (thisGeneration != generation) return
                    starting = false
                    running = false
                }
                onLog("BLE: advertising failed ($errorCode)")
            }
        }

        advertiser = le
        starting = true
        return try {
            le.startAdvertising(settings, data, callback)
            true
        } catch (e: Exception) {
            synchronized(this) {
                if (thisGeneration == generation) {
                    starting = false
                    running = false
                }
            }
            onLog("BLE: ${e.message}")
            false
        }
    }

    /**
     * Stop whatever advert is up and register a new one with a fresh tag.
     * Owly starts a fresh advert for each browse/send; reusing one that has
     * been running since app launch is not the same wake.
     */
    fun restart(arm: Int = 0, connectable: Boolean = true, onLog: (String) -> Unit): Boolean {
        stop()
        // Android unregisters the old advertising instance asynchronously;
        // give it a moment so the fresh tag is the one actually on air.
        try {
            Thread.sleep(250)
        } catch (_: InterruptedException) {
        }
        return start(arm, connectable, onLog)
    }

    @Synchronized
    fun stop() {
        generation++
        starting = false
        running = false
        try {
            advertiser?.stopAdvertising(null)
        } catch (_: Exception) {
        }
        advertiser = null
    }
}
