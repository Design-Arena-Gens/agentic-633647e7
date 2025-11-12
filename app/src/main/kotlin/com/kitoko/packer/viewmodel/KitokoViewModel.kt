package com.kitoko.packer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kitoko.packer.data.AuthRepository
import com.kitoko.packer.data.PackedOrderStore
import com.kitoko.packer.data.PackingLogRepository
import com.kitoko.packer.model.ChecklistEntry
import com.kitoko.packer.model.InvoiceItem
import com.kitoko.packer.model.InvoicePayload
import com.kitoko.packer.model.PackingPhase
import com.kitoko.packer.model.ScanEvent
import com.kitoko.packer.model.ScanSource
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val csvFormatter: DateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

class KitokoViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val packedOrderStore: PackedOrderStore,
    private val packingLogRepository: PackingLogRepository
) : AndroidViewModel(application) {

    data class UiState(
        val isSignedIn: Boolean = false,
        val phase: PackingPhase = PackingPhase.AwaitingInvoice,
        val snackbarMessage: String? = null,
        val showPackedOverlay: Boolean = false,
        val exportInProgress: Boolean = false,
        val packedOrders: Set<String> = emptySet()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var currentInvoice: InvoicePayload? = null
    private var checklist: MutableList<ChecklistEntry> = mutableListOf()
    private val scanEvents = mutableListOf<ScanEvent>()

    init {
        viewModelScope.launch {
            authRepository.authState.collectLatest { isSignedIn ->
                _uiState.value = _uiState.value.copy(isSignedIn = isSignedIn)
            }
        }

        viewModelScope.launch {
            packedOrderStore.packedOrders.collectLatest { packed ->
                _uiState.value = _uiState.value.copy(packedOrders = packed)
            }
        }
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(snackbarMessage = null)
    }

    fun consumeOverlay() {
        _uiState.value = _uiState.value.copy(showPackedOverlay = false)
    }

    fun signOut() {
        authRepository.signOut()
        resetState()
    }

    suspend fun signIn(email: String, password: String) {
        authRepository.signIn(email.trim(), password)
    }

    fun onBarcodeScanned(rawValue: String) {
        if (rawValue.isBlank()) return
        when (val phase = _uiState.value.phase) {
            PackingPhase.AwaitingInvoice -> handleInvoice(rawValue)
            is PackingPhase.ReadyToPack -> handleProduct(rawValue, phase)
            is PackingPhase.Completed -> handleCompletedPhase(rawValue, phase)
        }
    }

    private fun handleCompletedPhase(rawValue: String, phase: PackingPhase.Completed) {
        if (rawValue.startsWith("PKG1:")) {
            handleInvoice(rawValue)
        } else {
            sendMessage("Order ${phase.orderId} already packed. Scan next invoice.")
        }
    }

    private fun handleInvoice(rawValue: String) {
        val payload = parseInvoice(rawValue) ?: run {
            sendMessage("Invalid invoice QR")
            return
        }
        val orderId = payload.o
        if (_uiState.value.packedOrders.contains(orderId)) {
            sendMessage("Order $orderId already packed")
            return
        }

        currentInvoice = payload
        checklist = payload.i.map { item ->
            ChecklistEntry(sku = item.sku, required = item.units)
        }.toMutableList()
        scanEvents.clear()

        _uiState.value = _uiState.value.copy(
            phase = PackingPhase.ReadyToPack(
                orderId = orderId,
                checklist = checklist.toList(),
                scannedCount = 0,
                totalRequired = payload.i.sumOf(InvoiceItem::units)
            ),
            snackbarMessage = "Invoice $orderId ready",
            showPackedOverlay = false
        )
        logScan(ScanEvent(
            timestamp = System.currentTimeMillis(),
            orderId = orderId,
            sku = orderId,
            quantity = 1,
            source = ScanSource.INVOICE
        ))
    }

    private fun handleProduct(rawValue: String, phase: PackingPhase.ReadyToPack) {
        val sku = parseProduct(rawValue) ?: run {
            sendMessage("Unsupported SKU barcode")
            return
        }

        val entryIndex = checklist.indexOfFirst { it.sku.equals(sku, ignoreCase = true) }
        if (entryIndex == -1) {
            sendMessage("SKU $sku not in checklist")
            return
        }

        val entry = checklist[entryIndex]
        if (entry.isComplete) {
            sendMessage("SKU $sku already complete")
            return
        }

        val updated = entry.copy(scanned = min(entry.required, entry.scanned + 1))
        checklist[entryIndex] = updated

        logScan(ScanEvent(
            timestamp = System.currentTimeMillis(),
            orderId = phase.orderId,
            sku = sku,
            quantity = 1,
            source = ScanSource.PRODUCT
        ))

        val totalScanned = checklist.sumOf(ChecklistEntry::scanned)
        val allComplete = checklist.all(ChecklistEntry::isComplete)

        _uiState.value = _uiState.value.copy(
            phase = if (allComplete) {
                PackingPhase.Completed(phase.orderId, checklist.toList())
            } else {
                PackingPhase.ReadyToPack(
                    orderId = phase.orderId,
                    checklist = checklist.toList(),
                    scannedCount = totalScanned,
                    totalRequired = phase.totalRequired
                )
            }
        )

        if (allComplete) {
            onOrderPacked()
        }
    }

    private fun onOrderPacked() {
        val orderId = currentInvoice?.o ?: return
        viewModelScope.launch {
            packedOrderStore.markPacked(orderId)
        }
        _uiState.value = _uiState.value.copy(
            snackbarMessage = "Order $orderId packed",
            showPackedOverlay = true,
            phase = PackingPhase.Completed(orderId, checklist.toList())
        )
    }

    fun resetState() {
        currentInvoice = null
        checklist.clear()
        scanEvents.clear()
        _uiState.value = _uiState.value.copy(
            phase = PackingPhase.AwaitingInvoice,
            showPackedOverlay = false
        )
    }

    private fun parseInvoice(raw: String): InvoicePayload? = runCatching {
        if (!raw.startsWith("PKG1:")) return null
        val encoded = raw.substringAfter(":")
        val decodedBytes = Base64.getUrlDecoder().decode(padBase64(encoded))
        val payloadString = decodedBytes.decodeToString()
        val root = JSONObject(payloadString)
        val orderId = root.optString("o")
        if (orderId.isBlank()) return null
        val itemsArray = root.optJSONArray("i") ?: JSONArray()
        val items = buildList {
            for (index in 0 until itemsArray.length()) {
                val itemPair = itemsArray.optJSONArray(index) ?: continue
                val sku = itemPair.optString(0).takeIf { it.isNotBlank() } ?: continue
                val units = itemPair.optInt(1, 0)
                add(InvoiceItem(sku = sku, units = units))
            }
        }
        InvoicePayload(orderId, items)
    }.getOrNull()

    private fun parseProduct(raw: String): String? = when {
        raw.startsWith("PKT1:") -> runCatching {
            val encoded = raw.substringAfter(":")
            val decodedBytes = Base64.getUrlDecoder().decode(padBase64(encoded))
            val jsonObject = JSONObject(decodedBytes.decodeToString())
            jsonObject.optString("s").takeIf { it.isNotBlank() }?.uppercase()
        }.getOrNull()
        else -> raw.trim().uppercase().takeIf { it.isNotBlank() }
    }

    private fun padBase64(value: String): String =
        value + "=".repeat((4 - value.length % 4) % 4)

    private fun sendMessage(message: String) {
        _uiState.value = _uiState.value.copy(snackbarMessage = message)
    }

    private fun logScan(event: ScanEvent) {
        scanEvents += event
        viewModelScope.launch {
            runCatching { packingLogRepository.logScan(event) }
        }
    }

    suspend fun exportCsv(exportDir: File): File = withContext(Dispatchers.IO) {
        setExportInProgress(true)
        try {
            if (!exportDir.exists()) {
                exportDir.mkdirs()
            }
            val file = File(
                exportDir,
                "kitoko_packer_${Instant.now().epochSecond}.csv"
            )
            file.bufferedWriter().use { writer ->
                writer.appendLine("timestamp,orderId,sku,quantity,source")
                scanEvents.forEach { event ->
                    val ts = csvFormatter.format(
                        Instant.ofEpochMilli(event.timestamp).atOffset(ZoneOffset.UTC)
                    )
                    writer.appendLine("$ts,${event.orderId},${event.sku},${event.quantity},${event.source}")
                }
            }
            file
        } finally {
            setExportInProgress(false)
        }
    }

    private suspend fun setExportInProgress(inProgress: Boolean) {
        withContext(Dispatchers.Main) {
            _uiState.value = _uiState.value.copy(exportInProgress = inProgress)
        }
    }
}

class KitokoViewModelFactory(
    private val application: Application,
    private val authRepository: AuthRepository,
    private val packedOrderStore: PackedOrderStore,
    private val packingLogRepository: PackingLogRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(KitokoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return KitokoViewModel(application, authRepository, packedOrderStore, packingLogRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
