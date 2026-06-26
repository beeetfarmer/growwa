package com.nl.growwa.data.layout

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One row per occupied slot on the home surface.
 * container: "home" or "dock". For dock, page is always 0.
 */
@Entity(tableName = "placements", primaryKeys = ["container", "page", "position"])
data class PlacementEntity(
    val container: String,
    val page: Int,
    val position: Int,
    val packageName: String,
    val className: String,
    val userSerial: Long,
)

@Dao
interface PlacementDao {
    @Query("SELECT * FROM placements")
    fun observeAll(): Flow<List<PlacementEntity>>

    @Query("SELECT * FROM placements")
    suspend fun getAll(): List<PlacementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(placement: PlacementEntity)

    @Query("DELETE FROM placements WHERE container = :container AND page = :page AND position = :position")
    suspend fun removeSlot(container: String, page: Int, position: Int)

    @Query("DELETE FROM placements WHERE packageName = :packageName AND className = :className AND userSerial = :userSerial")
    suspend fun removeApp(packageName: String, className: String, userSerial: Long)

    @Query("SELECT COUNT(*) FROM placements WHERE container = :container AND page = :page AND position = :position")
    suspend fun isOccupied(container: String, page: Int, position: Int): Int
}

/** Launch frequency per app, used to rank Finder's default suggestions. */
@Entity(tableName = "usage", primaryKeys = ["packageName", "className", "userSerial"])
data class UsageEntity(
    val packageName: String,
    val className: String,
    val userSerial: Long,
    val count: Int,
    val lastUsed: Long,
)

@Dao
interface UsageDao {
    @Query(
        "INSERT INTO usage(packageName, className, userSerial, count, lastUsed) " +
            "VALUES(:packageName, :className, :userSerial, 1, :now) " +
            "ON CONFLICT(packageName, className, userSerial) " +
            "DO UPDATE SET count = count + 1, lastUsed = :now"
    )
    suspend fun increment(packageName: String, className: String, userSerial: Long, now: Long)

    @Query("SELECT * FROM usage ORDER BY count DESC, lastUsed DESC")
    fun observeAll(): Flow<List<UsageEntity>>
}

@Database(entities = [PlacementEntity::class, UsageEntity::class], version = 2, exportSchema = false)
abstract class LayoutDatabase : RoomDatabase() {
    abstract fun placementDao(): PlacementDao
    abstract fun usageDao(): UsageDao

    companion object {
        fun build(context: Context): LayoutDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LayoutDatabase::class.java,
                "growwa_layout.db",
            ).fallbackToDestructiveMigration(true).build()
    }
}
