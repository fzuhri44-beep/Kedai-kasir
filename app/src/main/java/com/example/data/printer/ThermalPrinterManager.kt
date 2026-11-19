package com.example.data.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.PaymentMethod
import com.example.data.model.PosTransaction
import com.example.ui.components.formatRupiah
import com.example.ui.components.formatShortDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.UUID

enum class PaperWidth(val label: String, val charsPerLine: Int) {
    MM_58("58 mm", 32),
    MM_80("80 mm", 48)
}

data class PrinterDeviceInfo(
    val name: String,
    val address: String,
    val isPaired: Boolean
)

data class PrinterUiState(
    val isBluetoothSupported: Boolean = true,
    val isBluetoothEnabled: Boolean = false,
    val hasPermissions: Boolean = false,
    val isScanning: Boolean = false,
    val isPrinting: Boolean = false,
    val pairedDevices: List<PrinterDeviceInfo> = emptyList(),
    val discoveredDevices: List<PrinterDeviceInfo> = emptyList(),
    val selectedPrinter: PrinterDeviceInfo? = null,
    val paperWidth: PaperWidth = PaperWidth.MM_58,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

class ThermalPrinterManager(private val context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _state = MutableStateFlow(
        PrinterUiState(
            isBluetoothSupported = bluetoothAdapter != null,
            isBluetoothEnabled = bluetoothAdapter?.isEnabled == true,
            hasPermissions = checkBluetoothPermissions(),
            selectedPrinter = loadSavedPrinter(),
            paperWidth = loadSavedPaperWidth()
        )
    )
    val state: StateFlow<PrinterUiState> = _state.asStateFlow()

    private var receiverRegistered = false

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device != null && !device.address.isNullOrBlank()) {
                        val safeName = try {
                            if (hasConnectPermission()) device.name else null
                        } catch (_: SecurityException) {
                            null
                        }
                        val info = PrinterDeviceInfo(
                            name = safeName?.takeIf { it.isNotBlank() } ?: "Perangkat Bluetooth",
                            address = device.address,
                            isPaired = try {
                                if (hasConnectPermission()) device.bondState == BluetoothDevice.BOND_BONDED else false
                            } catch (_: SecurityException) {
                                false
                            }
                        )
                        _state.update { current ->
                            val alreadyInPaired = current.pairedDevices.any { it.address == info.address }
                            val updatedDiscovered = (current.discoveredDevices.filterNot { it.address == info.address } + info)
                                .filterNot { d -> alreadyInPaired && d.address == info.address }
                            current.copy(discoveredDevices = updatedDiscovered)
                        }
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _state.update { it.copy(isScanning = false) }
                }
            }
        }
    }

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

    fun checkBluetoothPermissions(): Boolean =
        requiredPermissions().all { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun refreshStatus() {
        val adapter = bluetoothAdapter
        val hasPerm = checkBluetoothPermissions()
        val enabled = adapter?.isEnabled == true
        val paired = if (adapter != null && enabled && hasPerm) {
            loadPairedDevices(adapter)
        } else {
            emptyList()
        }
        _state.update {
            it.copy(
                isBluetoothSupported = adapter != null,
                isBluetoothEnabled = enabled,
                hasPermissions = hasPerm,
                pairedDevices = paired
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices(adapter: BluetoothAdapter): List<PrinterDeviceInfo> {
        return try {
            adapter.bondedDevices.orEmpty().map { device ->
                PrinterDeviceInfo(
                    name = device.name?.takeIf { it.isNotBlank() } ?: "Printer Bluetooth",
                    address = device.address,
                    isPaired = true
                )
            }.sortedBy { it.name.lowercase() }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read bonded devices: ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        refreshStatus()
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _state.update { it.copy(errorMessage = "Perangkat ini tidak mendukung Bluetooth.") }
            return
        }
        if (!checkBluetoothPermissions()) {
            _state.update { it.copy(errorMessage = "Izin Bluetooth diperlukan untuk mencari printer.") }
            return
        }
        if (!adapter.isEnabled) {
            _state.update { it.copy(errorMessage = "Aktifkan Bluetooth terlebih dahulu.") }
            return
        }

        registerDiscoveryReceiverIfNeeded()
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            _state.update {
                it.copy(
                    isScanning = true,
                    discoveredDevices = emptyList(),
                    errorMessage = null,
                    statusMessage = "Mencari printer Bluetooth di sekitar..."
                )
            }
            val started = adapter.startDiscovery()
            if (!started) {
                _state.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = "Gagal memulai pemindaian Bluetooth. Pastikan GPS/Bluetooth aktif."
                    )
                }
            }
        } catch (e: SecurityException) {
            _state.update {
                it.copy(
                    isScanning = false,
                    errorMessage = "Izin pemindaian Bluetooth ditolak: ${e.localizedMessage}"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val adapter = bluetoothAdapter ?: return
        try {
            if (checkBluetoothPermissions() && adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "cancelDiscovery failed: ${e.message}")
        }
        _state.update { it.copy(isScanning = false) }
    }

    fun selectPrinter(device: PrinterDeviceInfo) {
        prefs.edit()
            .putString(KEY_PRINTER_NAME, device.name)
            .putString(KEY_PRINTER_ADDR, device.address)
            .apply()
        _state.update {
            it.copy(
                selectedPrinter = device,
                errorMessage = null,
                statusMessage = "Printer aktif: ${device.name}"
            )
        }
    }

    fun setPaperWidth(width: PaperWidth) {
        prefs.edit().putString(KEY_PAPER_WIDTH, width.name).apply()
        _state.update { it.copy(paperWidth = width) }
    }

    fun clearFeedback() {
        _state.update { it.copy(statusMessage = null, errorMessage = null) }
    }

    suspend fun printReceipt(transaction: PosTransaction, cashierName: String): Result<Unit> {
        val bytes = buildEscPosReceiptBytes(
            tx = transaction,
            cashierName = cashierName,
            charsPerLine = _state.value.paperWidth.charsPerLine
        )
        return sendBytesToSelectedPrinter(bytes, "Struk ${transaction.receiptNumber} berhasil dicetak!")
    }

    suspend fun printTestPage(): Result<Unit> {
        val bytes = buildTestPageBytes(_state.value.paperWidth.charsPerLine)
        return sendBytesToSelectedPrinter(bytes, "Tes cetak berhasil dikirim ke printer!")
    }

    @SuppressLint("MissingPermission")
    private suspend fun sendBytesToSelectedPrinter(bytes: ByteArray, successMsg: String): Result<Unit> {
        val adapter = bluetoothAdapter
            ?: return Result.failure(IllegalStateException("Bluetooth tidak tersedia pada perangkat ini."))
        if (!checkBluetoothPermissions()) {
            return Result.failure(IllegalStateException("Izin Bluetooth belum diberikan."))
        }
        if (!adapter.isEnabled) {
            return Result.failure(IllegalStateException("Bluetooth sedang mati. Aktifkan Bluetooth terlebih dahulu."))
        }
        val selected = _state.value.selectedPrinter
            ?: return Result.failure(IllegalStateException("Pilih printer termal Bluetooth terlebih dahulu."))

        _state.update { it.copy(isPrinting = true, errorMessage = null, statusMessage = "Menghubungkan ke ${selected.name}...") }

        return withContext(Dispatchers.IO) {
            var socket: android.bluetooth.BluetoothSocket? = null
            try {
                if (adapter.isDiscovering) {
                    adapter.cancelDiscovery()
                }
                val device = adapter.getRemoteDevice(selected.address)
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket.connect()
                val out = socket.outputStream
                out.write(bytes)
                out.flush()
                delay(350)
                _state.update {
                    it.copy(
                        isPrinting = false,
                        statusMessage = successMsg,
                        errorMessage = null
                    )
                }
                Result.success(Unit)
            } catch (e: IOException) {
                Log.e(TAG, "Bluetooth print IO error", e)
                val msg = "Gagal mencetak ke ${selected.name}. Pastikan printer menyala, dalam jangkauan, dan sudah terhubung."
                _state.update { it.copy(isPrinting = false, errorMessage = msg, statusMessage = null) }
                Result.failure(IOException(msg, e))
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth print security error", e)
                val msg = "Izin koneksi Bluetooth ditolak."
                _state.update { it.copy(isPrinting = false, errorMessage = msg, statusMessage = null) }
                Result.failure(e)
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                }
            }
        }
    }

    private fun registerDiscoveryReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(
            appContext,
            discoveryReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }

    fun release() {
        stopScan()
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(discoveryReceiver)
            } catch (_: IllegalArgumentException) {
            }
            receiverRegistered = false
        }
    }

    private fun loadSavedPrinter(): PrinterDeviceInfo? {
        val name = prefs.getString(KEY_PRINTER_NAME, null)
        val addr = prefs.getString(KEY_PRINTER_ADDR, null)
        return if (!name.isNullOrBlank() && !addr.isNullOrBlank()) {
            PrinterDeviceInfo(name = name, address = addr, isPaired = true)
        } else {
            null
        }
    }

    private fun loadSavedPaperWidth(): PaperWidth {
        val raw = prefs.getString(KEY_PAPER_WIDTH, PaperWidth.MM_58.name)
        return PaperWidth.entries.firstOrNull { it.name == raw } ?: PaperWidth.MM_58
    }

    private fun buildEscPosReceiptBytes(
        tx: PosTransaction,
        cashierName: String,
        charsPerLine: Int
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val charset = Charset.forName("CP437")
        val divider = "-".repeat(charsPerLine)

        fun writeText(text: String) {
            out.write(text.toByteArray(charset))
        }

        fun writeLine(text: String = "") {
            writeText(text + "\n")
        }

        fun twoCol(left: String, right: String): String {
            val cleanLeft = left.take((charsPerLine - right.length - 1).coerceAtLeast(1))
            val spaces = (charsPerLine - cleanLeft.length - right.length).coerceAtLeast(1)
            return cleanLeft + " ".repeat(spaces) + right
        }

        // Initialize printer
        out.write(byteArrayOf(0x1B, 0x40))
        // Center align + Double height/width + Bold
        out.write(byteArrayOf(0x1B, 0x61, 0x01))
        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        out.write(byteArrayOf(0x1D, 0x21, 0x11))
        writeLine("KEDAI AA SULTHAN")
        // Normal size, not bold
        out.write(byteArrayOf(0x1D, 0x21, 0x00))
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        writeLine("Struk Pembayaran Resmi")
        // Left align
        out.write(byteArrayOf(0x1B, 0x61, 0x00))
        writeLine(divider)
        writeLine("No   : ${tx.receiptNumber}")
        writeLine("Tgl  : ${formatShortDateTime(tx.createdAt)}")
        writeLine("Kasir: ${cashierName.take(charsPerLine - 7)}")
        if (!tx.customerNote.isNullOrBlank()) {
            writeLine("Note : ${tx.customerNote.take(charsPerLine - 7)}")
        }
        writeLine(divider)

        tx.items.forEach { item ->
            writeLine(item.name.take(charsPerLine))
            val qtyPrice = "  ${item.quantity}x ${formatRupiah(item.unitPrice)}"
            val sub = formatRupiah(item.subtotal)
            writeLine(twoCol(qtyPrice, sub))
        }

        writeLine(divider)
        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        writeLine(twoCol("TOTAL", formatRupiah(tx.totalAmount)))
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        writeLine(twoCol("Bayar (${tx.paymentMethod})", formatRupiah(tx.cashReceived)))
        if (tx.paymentMethod == PaymentMethod.TUNAI.label) {
            writeLine(twoCol("Kembalian", formatRupiah(tx.changeAmount)))
        }
        writeLine(divider)

        // Center footer
        out.write(byteArrayOf(0x1B, 0x61, 0x01))
        writeLine("Terima kasih telah jajan")
        writeLine("di Kedai Aa Sulthan!")
        writeLine()
        writeLine()
        writeLine()
        // Partial paper cut (ignored safely on printers without cutter)
        out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))

        return out.toByteArray()
    }

    private fun buildTestPageBytes(charsPerLine: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val charset = Charset.forName("CP437")
        val divider = "=".repeat(charsPerLine)
        out.write(byteArrayOf(0x1B, 0x40))
        out.write(byteArrayOf(0x1B, 0x61, 0x01))
        out.write(byteArrayOf(0x1B, 0x45, 0x01))
        out.write("KEDAI AA SULTHAN\n".toByteArray(charset))
        out.write(byteArrayOf(0x1B, 0x45, 0x00))
        out.write("$divider\n".toByteArray(charset))
        out.write("TES PRINTER TERMAL BERHASIL\n".toByteArray(charset))
        out.write("Lebar Kertas: ${charsPerLine} kolom\n".toByteArray(charset))
        out.write("$divider\n\n\n\n".toByteArray(charset))
        out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
        return out.toByteArray()
    }

    companion object {
        private const val TAG = "ThermalPrinter"
        private const val PREFS_NAME = "kedai_printer_prefs"
        private const val KEY_PRINTER_NAME = "printer_name"
        private const val KEY_PRINTER_ADDR = "printer_address"
        private const val KEY_PAPER_WIDTH = "paper_width"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
