package com.example.evaluacion1.repository

import androidx.room.withTransaction
import com.example.evaluacion1.model.HistoryEntry
import com.example.evaluacion1.model.HistoryType
import com.example.evaluacion1.model.NewTicketDraft
import com.example.evaluacion1.model.Comment
import com.example.evaluacion1.model.Role
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.model.User
import com.example.evaluacion1.repository.local.AppDatabase
import com.example.evaluacion1.repository.local.CommentEntity
import com.example.evaluacion1.repository.local.HistoryEntity
import com.example.evaluacion1.repository.local.TicketEntity
import com.example.evaluacion1.repository.local.toEntity
import com.example.evaluacion1.repository.local.toModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

/** Tickets and history persisted in Room: they survive closing the app. */
class RoomTicketRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) : TicketRepository {

    private val tickets = database.ticketDao()
    private val history = database.historyDao()

    /** Loads the demo tickets only the first time the app runs. */
    suspend fun seedIfEmpty() {
        if (tickets.count() > 0) return
        val (seedTickets, seedHistory) = DemoTickets.build(clock())
        database.withTransaction {
            tickets.upsertAll(seedTickets.map { it.toEntity() })
            history.insertAll(seedHistory.map { it.toEntity() })
        }
    }

    override fun observeTickets(user: User): Flow<List<Ticket>> =
        tickets.observeAll().map { list ->
            list.map { it.toModel() }.filter { Permissions.canView(user, it) }
        }

    override fun observeTicket(ticketId: String): Flow<Ticket?> =
        tickets.observeById(ticketId).map { it?.toModel() }

    override fun observeHistory(ticketId: String): Flow<List<HistoryEntry>> =
        history.observeForTicket(ticketId).map { list -> list.map { it.toModel() } }

    override suspend fun createTicket(actor: User, draft: NewTicketDraft): AppResult<String> {
        if (!Permissions.has(actor.role, Capability.CREATE_TICKET)) {
            return AppResult.Failure(AppError.PERMISSION_DENIED)
        }
        val invalid = draft.title.trim().length < TITLE_MIN ||
                draft.description.trim().length < DESCRIPTION_MIN ||
                draft.branch.isBlank()
        if (invalid) return AppResult.Failure(AppError.INVALID_INPUT)

        val now = clock()
        val id = database.withTransaction {
            val newId = nextTicketId()
            tickets.upsert(
                TicketEntity(
                    id = newId,
                    title = draft.title.trim(),
                    description = draft.description.trim(),
                    category = draft.category,
                    priority = draft.priority,
                    status = TicketStatus.NEW,
                    branch = draft.branch,
                    requesterEmail = actor.email,
                    requesterName = actor.name,
                    assigneeEmail = null,
                    assigneeName = null,
                    createdAt = now,
                    updatedAt = now
                )
            )
            history.insert(
                HistoryEntity(
                    ticketId = newId,
                    actorName = actor.name,
                    type = HistoryType.CREATED,
                    detail = "",
                    note = null,
                    createdAt = now
                )
            )
            newId
        }
        return AppResult.Success(id)
    }

    override suspend fun changeStatus(
        actor: User,
        ticketId: String,
        newStatus: TicketStatus,
        note: String
    ): AppResult<Unit> = database.withTransaction {
        val ticket = tickets.getById(ticketId)?.toModel()
            ?: return@withTransaction AppResult.Failure(AppError.NOT_FOUND)
        if (!Permissions.canView(actor, ticket)) {
            return@withTransaction AppResult.Failure(AppError.PERMISSION_DENIED)
        }
        val allowed = Permissions.availableStatuses(actor, ticket)
        if (allowed.isEmpty()) return@withTransaction AppResult.Failure(AppError.PERMISSION_DENIED)
        if (newStatus !in allowed) return@withTransaction AppResult.Failure(AppError.INVALID_TRANSITION)
        if (note.trim().length < NOTE_MIN) return@withTransaction AppResult.Failure(AppError.INVALID_INPUT)

        val now = clock()
        val takesOwnership = newStatus == TicketStatus.ASSIGNED && ticket.assigneeEmail == null
        tickets.upsert(
            ticket.copy(
                status = newStatus,
                assigneeEmail = if (takesOwnership) actor.email else ticket.assigneeEmail,
                assigneeName = if (takesOwnership) actor.name else ticket.assigneeName,
                updatedAt = now
            ).toEntity()
        )
        history.insert(
            HistoryEntity(
                ticketId = ticketId,
                actorName = actor.name,
                type = HistoryType.STATUS_CHANGED,
                detail = "${ticket.status.name}>${newStatus.name}",
                note = note.trim(),
                createdAt = now
            )
        )
        AppResult.Success(Unit)
    }

    override suspend fun assignTicket(actor: User, ticketId: String, assigneeId: Long): AppResult<Unit> =
        database.withTransaction {
            val ticket = tickets.getById(ticketId)?.toModel()
                ?: return@withTransaction AppResult.Failure(AppError.NOT_FOUND)
            if (!Permissions.canAssign(actor, ticket)) {
                return@withTransaction AppResult.Failure(AppError.PERMISSION_DENIED)
            }
            val assignee = DemoAccounts.users.firstOrNull {
                it.id == assigneeId && it.role == Role.OPERATOR && it.active
            } ?: return@withTransaction AppResult.Failure(AppError.NOT_FOUND)

            val now = clock()
            val newStatus = if (ticket.status == TicketStatus.NEW) TicketStatus.ASSIGNED else ticket.status
            tickets.upsert(
                ticket.copy(
                    assigneeEmail = assignee.email,
                    assigneeName = assignee.name,
                    status = newStatus,
                    updatedAt = now
                ).toEntity()
            )
            history.insert(
                HistoryEntity(
                    ticketId = ticketId,
                    actorName = actor.name,
                    type = HistoryType.ASSIGNED,
                    detail = assignee.name,
                    note = null,
                    createdAt = now
                )
            )
            AppResult.Success(Unit)
        }

    private suspend fun nextTicketId(): String {
        val last = tickets.lastId()?.removePrefix("PNL-")?.toIntOrNull() ?: 0
        return String.format(Locale.ROOT, "PNL-%04d", last + 1)
    }

    private companion object {
        const val TITLE_MIN = 5
        const val DESCRIPTION_MIN = 10
        const val NOTE_MIN = 5
    }
}

/** Comments persisted in Room. */
class RoomCommentRepository(
    database: AppDatabase,
    private val tickets: TicketRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : CommentRepository {

    private val comments = database.commentDao()

    override fun observeComments(ticketId: String): Flow<List<Comment>> =
        comments.observeForTicket(ticketId).map { list -> list.map { it.toModel() } }

    override suspend fun addComment(actor: User, ticketId: String, text: String): AppResult<Unit> {
        val ticket = tickets.observeTicket(ticketId).first()
            ?: return AppResult.Failure(AppError.NOT_FOUND)
        if (!Permissions.canComment(actor, ticket)) return AppResult.Failure(AppError.PERMISSION_DENIED)
        val trimmed = text.trim()
        if (trimmed.length < COMMENT_MIN || trimmed.length > COMMENT_MAX) {
            return AppResult.Failure(AppError.INVALID_INPUT)
        }
        comments.insert(
            CommentEntity(
                ticketId = ticketId,
                authorName = actor.name,
                authorEmail = actor.email,
                text = trimmed,
                createdAt = clock()
            )
        )
        return AppResult.Success(Unit)
    }

    private companion object {
        const val COMMENT_MIN = 3
        const val COMMENT_MAX = 500
    }
}