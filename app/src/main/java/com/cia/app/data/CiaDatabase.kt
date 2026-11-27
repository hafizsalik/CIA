package com.cia.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Index
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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


@Entity(tableName = "parties", indices = [Index(value = ["phone"], unique = true)])
data class PartyEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String,
    val nationalCode: String = "",
    val postalCode: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface CiaDao {
    @Query("SELECT * FROM accounts ORDER BY createdAt")
    fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM parties ORDER BY name COLLATE NOCASE")
    fun observeParties(): Flow<List<PartyEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertParty(party: PartyEntity)

    @Query("SELECT * FROM parties WHERE phone = :phone LIMIT 1")
    suspend fun findPartyByPhone(phone: String): PartyEntity?

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

    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('درآمد','پول گرفتم','هدیه گرفتم','قرض گرفتم') THEN amount WHEN type IN ('هزینه','هزینه کردم','خرج کردم','پول دادم','هدیه دادم','قرض دادم') THEN -amount ELSE 0 END), 0) FROM transactions WHERE deletedAt IS NULL")
    suspend fun netTransactions(): Long

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun transactionCount(): Int
}

@Database(entities = [AccountEntity::class, TransactionEntity::class, PartyEntity::class], version = 2, exportSchema = false)
abstract class CiaDatabase : RoomDatabase() {
    abstract fun dao(): CiaDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS parties (id TEXT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, nationalCode TEXT NOT NULL, postalCode TEXT NOT NULL, description TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_parties_phone ON parties(phone)")
            }
        }
        @Volatile private var INSTANCE: CiaDatabase? = null
        fun get(context: Context): CiaDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, CiaDatabase::class.java, "cia.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { INSTANCE = it }
        }
    }
}
