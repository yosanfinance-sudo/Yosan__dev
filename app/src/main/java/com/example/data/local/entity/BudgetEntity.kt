package com.example.data.local.entity

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["categoryId", "subcategoryId", "monthYear"], unique = true)
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val subcategoryId: Long? = null,
    val monthYear: String, // Format: "YYYY-MM" (e.g. "2026-11") or "DEFAULT" for recurring
    val budgetAmount: Double,
    val alertThresholdPercent: Int = 80,
    val isRolloverEnabled: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
