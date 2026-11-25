package com.eisenberg.trader.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** source: "SIM" (simulado) ou "REAL" (importado pelo usuário). */
data class Asset(val symbol: String, val source: String, val closes: List<Double>)

@Entity(tableName = "assets") data class AssetEntity(@PrimaryKey val symbol: String, val source: String, val closes: String)
@Entity(tableName = "positions") data class PositionEntity(@PrimaryKey val symbol: String, val entry: Double, val qty: Double, val sl: Double, val tp: Double)
@Entity(tableName = "trades") data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val symbol: String, val source: String,
    val entry: Double, val exit: Double, val qty: Double, val pnl: Double, val reason: String, val time: Long
)

fun AssetEntity.toAsset() = Asset(symbol, source, closes.split(",").mapNotNull { it.toDoubleOrNull() })
fun Asset.toEntity() = AssetEntity(symbol, source, closes.joinToString(","))

@Dao interface AppDao {
    @Query("SELECT * FROM assets") fun assets(): Flow<List<AssetEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(a: AssetEntity)
    @Query("DELETE FROM assets WHERE symbol = :s") suspend fun delAsset(s: String)
    @Query("SELECT COUNT(*) FROM assets") suspend fun count(): Int
    @Query("SELECT * FROM positions") fun positions(): Flow<List<PositionEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertPos(p: PositionEntity)
    @Query("DELETE FROM positions WHERE symbol = :s") suspend fun delPos(s: String)
    @Query("DELETE FROM positions") suspend fun clearPositions()
    @Query("SELECT * FROM trades ORDER BY time DESC") fun trades(): Flow<List<TradeEntity>>
    @Insert suspend fun insertTrade(t: TradeEntity)
    @Query("DELETE FROM trades") suspend fun clearTrades()
}

@Database(entities = [AssetEntity::class, PositionEntity::class, TradeEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "eisenberg.db")
                .fallbackToDestructiveMigration().build().also { inst = it }
        }
    }
}
