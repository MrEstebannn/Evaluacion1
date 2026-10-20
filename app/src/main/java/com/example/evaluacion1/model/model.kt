package com.example.evaluacion1.model

enum class Role { REQUESTER, OPERATOR, SUPERVISOR, MANAGEMENT, ADMIN }

enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

enum class Category { STORE, PHARMACY, SUPPLIER, INFRASTRUCTURE, INTERNAL_SUPPORT, LEGAL }

enum class HistoryType { CREATED, STATUS_CHANGED, ASSIGNED }

enum class TicketStatus {
    NEW, ASSIGNED, IN_PROGRESS, ON_HOLD, RESOLVED, CLOSED;

    val isOpen: Boolean
        get() = this != RESOLVED && this != CLOSED

    fun nextStatuses(): List<TicketStatus> = when (this) {
        NEW -> listOf(ASSIGNED)
        ASSIGNED -> listOf(IN_PROGRESS, ON_HOLD)
        IN_PROGRESS -> listOf(ON_HOLD, RESOLVED)
        ON_HOLD -> listOf(IN_PROGRESS)
        RESOLVED -> listOf(CLOSED, IN_PROGRESS)
        CLOSED -> emptyList()
    }
}

data class User(
    val id: Long,
    val name: String,
    val email: String,
    val role: Role,
    val active: Boolean = true
)

data class Ticket(
    val id: String,
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

data class NewTicketDraft(
    val title: String,
    val description: String,
    val category: Category,
    val priority: Priority,
    val branch: String
)

data class Comment(
    val id: Long,
    val ticketId: String,
    val authorName: String,
    val authorEmail: String,
    val text: String,
    val createdAt: Long
)

data class HistoryEntry(
    val id: Long,
    val ticketId: String,
    val actorName: String,
    val type: HistoryType,
    val detail: String,
    val note: String?,
    val createdAt: Long
)