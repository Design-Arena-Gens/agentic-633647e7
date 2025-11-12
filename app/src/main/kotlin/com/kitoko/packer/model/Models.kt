package com.kitoko.packer.model

data class InvoicePayload(
    val o: String,
    val i: List<InvoiceItem>
)

data class InvoiceItem(
    val sku: String,
    val units: Int
)

data class ScanEvent(
    val timestamp: Long,
    val orderId: String,
    val sku: String,
    val quantity: Int,
    val source: ScanSource
)

enum class ScanSource { INVOICE, PRODUCT }

sealed class PackingPhase {
    data object AwaitingInvoice : PackingPhase()
    data class ReadyToPack(
        val orderId: String,
        val checklist: List<ChecklistEntry>,
        val scannedCount: Int,
        val totalRequired: Int
    ) : PackingPhase()
    data class Completed(
        val orderId: String,
        val checklist: List<ChecklistEntry>
    ) : PackingPhase()
}

data class ChecklistEntry(
    val sku: String,
    val required: Int,
    val scanned: Int = 0
) {
    val remaining: Int get() = (required - scanned).coerceAtLeast(0)
    val isComplete: Boolean get() = scanned >= required
}
