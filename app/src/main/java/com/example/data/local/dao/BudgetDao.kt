package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY monthYear DESC, categoryId ASC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY categoryId ASC")
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY categoryId ASC")
    suspend fun getBudgetsForMonthList(monthYear: String): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE monthYear = 'DEFAULT' ORDER BY categoryId ASC")
    fun getRecurringBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthYear = 'DEFAULT' ORDER BY categoryId ASC")
    suspend fun getRecurringBudgetsList(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND ((:subcategoryId IS NULL AND subcategoryId IS NULL) OR subcategoryId = :subcategoryId) AND monthYear = :monthYear LIMIT 1")
    suspend fun getBudget(categoryId: Long, subcategoryId: Long?, monthYear: String): BudgetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudgets(budgets: List<BudgetEntity>)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudget(id: Long)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun deleteBudgetsForCategory(categoryId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()
}
