package com.example.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class PendingStatus {
    PENDING,
    APPROVED,
    DISMISSED
}

@Immutable
@Entity(tableName = "pending_transactions")
data class PendingTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val transactionType: TransactionType,
    val description: String,
    val merchant: String?,
    val categoryId: Long? = null,
    val categoryName: String = "Uncategorized",
    val subcategoryId: Long? = null,
    val subcategoryName: String? = null,
    val accountId: Long = 1L,
    val accountOrCardMask: String? = null,
    val referenceNumber: String? = null,
    val runningBalance: Double? = null,
    val transactionDate: String,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val rawMessage: String,
    val deduplicationKey: String,
    val status: PendingStatus = PendingStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis()
)
