package com.cia.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val name: String,
    val openingBalance: Long = 0L,
    val currency: String = "تومان",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: String,
    val number: Long,
    val type: String,
    val amount: Long,
    val accountId: String,
    val party: String?,
    val category: String?,
    val description: String?,
    val occurredAt: Long,
    val deletedAt: Long? = null,
    val sourceSmsId: String? = null
)

@Dao
interface CiaDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAccount(account: AccountEntity)

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, deletedAt: Long)

    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun permanentlyDelete(id: String)

    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('درآمد','پول گرفتم','هدیه گرفتم','قرض گرفتم') THEN amount WHEN type IN ('هزینه','پول دادم','هدیه دادم','قرض دادم') THEN -amount ELSE 0 END), 0) FROM transactions WHERE deletedAt IS NULL")
    suspend fun netTransactions(): Long

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun transactionCount(): Int
}

@Database(entities = [AccountEntity::class, TransactionEntity::class], version = 1, exportSchema = false)
abstract class CiaDatabase : RoomDatabase() {
    abstract fun dao(): CiaDao

    companion object {
        @Volatile private var INSTANCE: CiaDatabase? = null
        fun get(context: Context): CiaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CiaDatabase::class.java, "cia.db")
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}
