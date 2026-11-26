package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.PendingStatus
import com.example.data.local.entity.PendingTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingTransactionDao {

    @Query("SELECT * FROM pending_transactions WHERE status = :status ORDER BY timestamp DESC")
    fun getPendingTransactionsFlow(status: PendingStatus = PendingStatus.PENDING): Flow<List<PendingTransactionEntity>>

    @Query("SELECT * FROM pending_transactions WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getPendingTransactionsList(status: PendingStatus = PendingStatus.PENDING): List<PendingTransactionEntity>

    @Query("SELECT COUNT(*) FROM pending_transactions WHERE status = 'PENDING'")
    fun getPendingCountFlow(): Flow<Int>

    @Query("SELECT * FROM pending_transactions WHERE deduplicationKey = :dedupeKey LIMIT 1")
    suspend fun getByDeduplicationKey(dedupeKey: String): PendingTransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(pending: PendingTransactionEntity): Long

    @Update
    suspend fun update(pending: PendingTransactionEntity)

    @Query("UPDATE pending_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: PendingStatus)

    @Query("UPDATE pending_transactions SET status = 'APPROVED' WHERE id IN (:ids)")
    suspend fun approveAll(ids: List<Long>)

    @Query("UPDATE pending_transactions SET status = 'DISMISSED' WHERE id IN (:ids)")
    suspend fun dismissAll(ids: List<Long>)

    @Query("DELETE FROM pending_transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM pending_transactions WHERE status = 'DISMISSED' OR status = 'APPROVED'")
    suspend fun purgeProcessed()
}
