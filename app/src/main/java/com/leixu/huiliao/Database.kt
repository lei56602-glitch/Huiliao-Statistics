package com.leixu.huiliao

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

@Entity(tableName = "production_records")
data class ProductionRecord(
    @PrimaryKey val id: String,
    val date: String,
    val productionLine: String,
    val team: String,
    val personnel: String,
    val productModel: String,
    val productWeight: Double,
    val totalUnreasonable: Double,
    val abnormalSubItems: String,
    val createTime: String
)

@Dao
interface RecordDao {
    @Query("SELECT * FROM production_records ORDER BY createTime DESC")
    fun observeAll(): Flow<List<ProductionRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ProductionRecord)

    @Query("DELETE FROM production_records WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM production_records")
    suspend fun allOnce(): List<ProductionRecord>
}

@Database(entities = [ProductionRecord::class], version = 1, exportSchema = false)
abstract class HuiliaoDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao

    companion object {
        @Volatile private var INSTANCE: HuiliaoDatabase? = null

        fun get(context: Context): HuiliaoDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HuiliaoDatabase::class.java,
                    "huiliao-statistics.db"
                ).build().also { INSTANCE = it }
            }
    }
}
