package com.nl.growwa.data.layout

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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * One row per occupied slot on the home surface.
 * container: "home", "dock", or "folder_<id>" (a folder's contents). For dock, page is always 0.
 * kind: "app" (app fields set), "folder" (folderId set), or "widget" (appWidgetId + span + provider in
 * packageName/className). For widgets, position is the top-left cell; it spans spanX x spanY cells.
 */
@Entity(tableName = "placements", primaryKeys = ["container", "page", "position"])
data class PlacementEntity(
    val container: String,
    val page: Int,
    val position: Int,
    val packageName: String = "",
    val className: String = "",
    val userSerial: Long = 0,
    val kind: String = "app",
    val folderId: Long = 0,
    val appWidgetId: Int = 0,
    val spanX: Int = 1,
    val spanY: Int = 1,
)

@Dao
interface PlacementDao {
    @Query("SELECT * FROM placements")
    fun observeAll(): Flow<List<PlacementEntity>>

    @Query("SELECT * FROM placements")
    suspend fun getAll(): List<PlacementEntity>

    @Query("SELECT * FROM placements WHERE container = :container AND page = :page AND position = :position LIMIT 1")
    suspend fun getSlot(container: String, page: Int, position: Int): PlacementEntity?

    @Query("SELECT * FROM placements WHERE container = :container ORDER BY position")
    suspend fun itemsIn(container: String): List<PlacementEntity>

    @Query("SELECT * FROM placements WHERE kind = 'folder' AND folderId = :folderId LIMIT 1")
    suspend fun folderPlacement(folderId: Long): PlacementEntity?

    @Query("SELECT * FROM placements WHERE kind = 'widget' AND appWidgetId = :appWidgetId LIMIT 1")
    suspend fun widgetPlacement(appWidgetId: Int): PlacementEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(placement: PlacementEntity)

    @Query("DELETE FROM placements WHERE container = :container AND page = :page AND position = :position")
    suspend fun removeSlot(container: String, page: Int, position: Int)

    @Query("DELETE FROM placements WHERE container = :container")
    suspend fun clearContainer(container: String)

    @Query("DELETE FROM placements WHERE kind = 'app' AND packageName = :packageName AND className = :className AND userSerial = :userSerial")
    suspend fun removeApp(packageName: String, className: String, userSerial: Long)
}

/** A home-screen folder; its apps live in placements with container = "folder_<id>". */
@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders")
    fun observeAll(): Flow<List<FolderEntity>>

    @Insert
    suspend fun insert(folder: FolderEntity): Long

    @Query("UPDATE folders SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: Long)
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

@Database(
    entities = [PlacementEntity::class, FolderEntity::class, UsageEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class LayoutDatabase : RoomDatabase() {
    abstract fun placementDao(): PlacementDao
    abstract fun folderDao(): FolderDao
    abstract fun usageDao(): UsageDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE placements ADD COLUMN appWidgetId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE placements ADD COLUMN spanX INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE placements ADD COLUMN spanY INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun build(context: Context): LayoutDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                LayoutDatabase::class.java,
                "growwa_layout.db",
            )
                .addMigrations(MIGRATION_3_4)
                .fallbackToDestructiveMigration(true)
                .build()
    }
}
