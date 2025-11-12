package com.kitoko.packer.data

import com.google.firebase.firestore.FirebaseFirestore
import com.kitoko.packer.model.ScanEvent
import kotlinx.coroutines.tasks.await
import java.util.Date

class PackingLogRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun logScan(event: ScanEvent) {
        val doc = mapOf(
            "timestamp" to com.google.firebase.Timestamp(Date(event.timestamp)),
            "orderId" to event.orderId,
            "sku" to event.sku,
            "quantity" to event.quantity,
            "source" to event.source.name
        )
        firestore.collection("scanEvents").add(doc).await()
    }
}
