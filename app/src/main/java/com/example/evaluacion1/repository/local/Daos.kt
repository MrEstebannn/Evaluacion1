package com.example.evaluacion1.repository.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE id = :id")
    fun observeById(id: String): Flow<TicketEntity?>

    @Query("SELECT * FROM tickets WHERE id = :id")
    suspend fun getById(id: String): TicketEntity?

    @Query("SELECT id FROM tickets ORDER BY id DESC LIMIT 1")
    suspend fun lastId(): String?

    @Query("SELECT COUNT(*) FROM tickets")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(ticket: TicketEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tickets: List<TicketEntity>)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE ticketId = :ticketId ORDER BY createdAt ASC, id ASC")
    fun observeForTicket(ticketId: String): Flow<List<CommentEntity>>

    @Insert
    suspend fun insert(comment: CommentEntity)
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history WHERE ticketId = :ticketId ORDER BY createdAt DESC, id DESC")
    fun observeForTicket(ticketId: String): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity)

    @Insert
    suspend fun insertAll(entries: List<HistoryEntity>)
}