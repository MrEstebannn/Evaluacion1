package com.example.evaluacion1.repository.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.evaluacion1.model.Category
import com.example.evaluacion1.model.Comment
import com.example.evaluacion1.model.HistoryEntry
import com.example.evaluacion1.model.HistoryType
import com.example.evaluacion1.model.Priority
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.TicketStatus

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val priority: Priority,
    val status: TicketStatus,
    val branch: String,
    val requesterEmail: String,
    val requesterName: String,
    val assigneeEmail: String?,
    val assigneeName: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "comments", indices = [Index(value = ["ticketId"])])
data class CommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: String,
    val authorName: String,
    val authorEmail: String,
    val text: String,
    val createdAt: Long
)

@Entity(tableName = "history", indices = [Index(value = ["ticketId"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ticketId: String,
    val actorName: String,
    val type: HistoryType,
    val detail: String,
    val note: String?,
    val createdAt: Long
)

fun Ticket.toEntity() = TicketEntity(
    id = id,
    title = title,
    description = description,
    category = category,
    priority = priority,
    status = status,
    branch = branch,
    requesterEmail = requesterEmail,
    requesterName = requesterName,
    assigneeEmail = assigneeEmail,
    assigneeName = assigneeName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun TicketEntity.toModel() = Ticket(
    id = id,
    title = title,
    description = description,
    category = category,
    priority = priority,
    status = status,
    branch = branch,
    requesterEmail = requesterEmail,
    requesterName = requesterName,
    assigneeEmail = assigneeEmail,
    assigneeName = assigneeName,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CommentEntity.toModel() = Comment(
    id = id,
    ticketId = ticketId,
    authorName = authorName,
    authorEmail = authorEmail,
    text = text,
    createdAt = createdAt
)

fun HistoryEntry.toEntity() = HistoryEntity(
    id = id,
    ticketId = ticketId,
    actorName = actorName,
    type = type,
    detail = detail,
    note = note,
    createdAt = createdAt
)

fun HistoryEntity.toModel() = HistoryEntry(
    id = id,
    ticketId = ticketId,
    actorName = actorName,
    type = type,
    detail = detail,
    note = note,
    createdAt = createdAt
)