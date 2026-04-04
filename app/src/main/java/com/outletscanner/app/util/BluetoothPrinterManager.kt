package com.outletscanner.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import kotlin.math.ceil

/**
 * Manages Bluetooth connection and ESC/POS printing to a Rongta RPP320 thermal label printer.
 * Uses standard Android Bluetooth SPP (Serial Port Profile).
 */
class BluetoothPrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothPrinter"

        /** Standard SPP UUID for Bluetooth serial communication */
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // ESC/POS commands
        private val CMD_INIT = byteArrayOf(0x1B, 0x40)             // ESC @ - Initialize printer
        private val CMD_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x00) // ESC d 0 - No extra feed
        private val CMD_CUT = byteArrayOf(0x1D, 0x56, 0x00)        // GS V 0 - Full cut (if supported)
    }

    enum class ConnectionState {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    interface ConnectionCallback {
        fun onStateChanged(state: ConnectionState)
        fun onError(message: String)
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var connectedDevice: BluetoothDevice? = null
    var connectionState: ConnectionState = ConnectionState.DISCONNECTED
        private set

    var callback: ConnectionCallback? = null

    /**
     * Check if Bluetooth is available and enabled.
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    /**
     * Check if the app has the required Bluetooth permissions.
     */
    fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            true // Legacy permissions are granted at install time
        }
    }

    /**
     * Get list of paired Bluetooth devices.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        if (!hasBluetoothPermissions()) return emptyList()
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Connect to a Bluetooth printer device.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions()) {
            callback?.onError("Bluetooth permission not granted")
            return@withContext false
        }

        try {
            disconnect()

            updateState(ConnectionState.CONNECTING)
            Log.d(TAG, "Connecting to ${device.name} (${device.address})...")

            // Cancel discovery to speed up connection
            bluetoothAdapter?.cancelDiscovery()

            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()

            outputStream = socket?.outputStream
            connectedDevice = device

            // Initialize the printer
            outputStream?.write(CMD_INIT)
            outputStream?.flush()

            updateState(ConnectionState.CONNECTED)
            Log.d(TAG, "Connected to ${device.name}")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed: ${e.message}")
            cleanupConnection()
            updateState(ConnectionState.DISCONNECTED)
            callback?.onError("Connection failed: ${e.message}")
            false
        }
    }

    /**
     * Connect to a device by MAC address.
     */
    @SuppressLint("MissingPermission")
    suspend fun connectByAddress(address: String): Boolean {
        if (!hasBluetoothPermissions()) {
            callback?.onError("Bluetooth permission not granted")
            return false
        }

        val device = bluetoothAdapter?.getRemoteDevice(address)
        return if (device != null) {
            connect(device)
        } else {
            callback?.onError("Device not found: $address")
            false
        }
    }

    /**
     * Disconnect from the printer.
     */
    fun disconnect() {
        cleanupConnection()
        updateState(ConnectionState.DISCONNECTED)
    }

    /**
     * Check if currently connected to a printer.
     */
    fun isConnected(): Boolean {
        return connectionState == ConnectionState.CONNECTED && socket?.isConnected == true
    }

    /**
     * Get the name of the currently connected device.
     */
    @SuppressLint("MissingPermission")
    fun getConnectedDeviceName(): String? {
        if (!hasBluetoothPermissions()) return null
        return connectedDevice?.name
    }

    /**
     * Print a bitmap image using ESC/POS GS v 0 raster bitmap command.
     *
     * The bitmap is converted to 1-bit monochrome and sent using:
     * GS v 0 m xL xH yL yH d1...dk
     * Where:
     *   m = 0 (normal mode)
     *   xL, xH = width in bytes (low, high)
     *   yL, yH = height in dots (low, high)
     *   d1...dk = bitmap data (1 bit per pixel, MSB first, 1=black, 0=white)
     */
    suspend fun printBitmap(bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        // Auto-reconnect if disconnected but we have a saved device
        if (!isConnected()) {
            val savedAddress = PrefsManager(context).savedPrinterAddress
            if (savedAddress.isNotEmpty()) {
                val reconnected = connectByAddress(savedAddress)
                if (!reconnected) {
                    callback?.onError("Printer not connected")
                    return@withContext false
                }
            } else {
                callback?.onError("Printer not connected")
                return@withContext false
            }
        }

        try {
            val os = outputStream ?: run {
                callback?.onError("Output stream not available")
                return@withContext false
            }

            // Initialize printer
            os.write(CMD_INIT)

            // Convert bitmap to monochrome byte data
            val width = bitmap.width
            val height = bitmap.height
            val widthBytes = ceil(width / 8.0).toInt()

            // GS v 0 command: 0x1D 0x76 0x30 0x00
            os.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00))

            // xL xH - width in bytes
            os.write(byteArrayOf(
                (widthBytes and 0xFF).toByte(),
                ((widthBytes shr 8) and 0xFF).toByte()
            ))

            // yL yH - height in dots
            os.write(byteArrayOf(
                (height and 0xFF).toByte(),
                ((height shr 8) and 0xFF).toByte()
            ))

            // Bitmap data - row by row
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val rowBuffer = ByteArray(widthBytes)
            for (y in 0 until height) {
                // Clear the row buffer
                rowBuffer.fill(0)

                for (x in 0 until width) {
                    val pixel = pixels[y * width + x]

                    // Convert to grayscale and threshold
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                    // 1 = black, 0 = white (threshold at 128)
                    if (gray < 128) {
                        val byteIndex = x / 8
                        val bitIndex = 7 - (x % 8) // MSB first
                        rowBuffer[byteIndex] = (rowBuffer[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                    }
                }

                os.write(rowBuffer)
            }

            // Feed some lines after the image
            os.write(CMD_FEED_LINES)
            os.flush()

            Log.d(TAG, "Bitmap printed successfully: ${width}x${height} pixels, $widthBytes bytes/row")
            true
        } catch (e: IOException) {
            Log.e(TAG, "Print failed: ${e.message}")
            cleanupConnection()
            updateState(ConnectionState.DISCONNECTED)
            callback?.onError("Print failed: ${e.message}")
            false
        }
    }

    private fun cleanupConnection() {
        try {
            outputStream?.close()
        } catch (_: IOException) {}
        try {
            socket?.close()
        } catch (_: IOException) {}
        outputStream = null
        socket = null
        connectedDevice = null
    }

    private fun updateState(state: ConnectionState) {
        connectionState = state
        callback?.onStateChanged(state)
    }
}
