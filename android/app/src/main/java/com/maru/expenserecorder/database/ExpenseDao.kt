package com.maru.expenserecorder.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Insert
    suspend fun insert(expense: Expense): Long

    @Query("SELECT * FROM expenses ORDER BY timestampMs DESC")
    fun getAllFlow(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE syncedToOneDrive = 0 ORDER BY timestampMs ASC")
    suspend fun getUnsynced(): List<Expense>

    @Query("UPDATE expenses SET syncedToOneDrive = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    @Query("SELECT SUM(amount) FROM expenses")
    suspend fun getTotal(): Double?

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}
