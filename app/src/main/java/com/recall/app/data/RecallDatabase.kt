package com.recall.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun observe(id: Long): Flow<Item?>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    @Query("SELECT * FROM items ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<Item>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Item): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Item>)

    @Update
    suspend fun update(item: Item)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM items")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceAll(items: List<Item>) {
        clear()
        insertAll(items)
    }
}

@Database(entities = [Item::class], version = 1, exportSchema = true)
abstract class RecallDatabase : RoomDatabase() {
    abstract fun items(): ItemDao

    companion object {
        fun create(context: Context): RecallDatabase =
            Room.databaseBuilder(context, RecallDatabase::class.java, "recall.db").build()
    }
}
