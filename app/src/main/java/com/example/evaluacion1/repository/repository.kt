package com.example.evaluacion1.repository

ppackage com.example.evaluacion1.repository

import com.example.evaluacion1.model.Category
import com.example.evaluacion1.model.Comment
import com.example.evaluacion1.model.HistoryEntry
import com.example.evaluacion1.model.HistoryType
import com.example.evaluacion1.model.NewTicketDraft
import com.example.evaluacion1.model.Priority
import com.example.evaluacion1.model.Role
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.Locale
import kotlin.random.Random

enum class AppError {
    INVALID_CREDENTIALS,
    INACTIVE_USER,
    PERMISSION_DENIED,
    NOT_FOUND,
    INVALID_TRANSITION,
    INVALID_INPUT
}

sealed interface AppResult<out T> {
    data class Success<out T>(val data: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}

enum class Capability {
    CREATE_TICKET,
    VIEW_ALL_TICKETS,
    CHANGE_STATUS,
    ASSIGN_TICKET,
    COMMENT,
    MANAGE_USERS
}

object Permissions {

    private val matrix: Map<Role, Set<Capability>> = mapOf(
        Role.REQUESTER to setOf(Capability.CREATE_TICKET, Capability.COMMENT),
        Role.OPERATOR to setOf(Capability.CHANGE_STATUS, Capability.COMMENT),
        Role.SUPERVISOR to setOf(
            Capability.VIEW_ALL_TICKETS,
            Capability.CHANGE_STATUS,
            Capability.ASSIGN_TICKET,
            Capability.COMMENT
        ),
        Role.MANAGEMENT to setOf(Capability.VIEW_ALL_TICKETS),
        Role.ADMIN to setOf(Capability.VIEW_ALL_TICKETS, Capability.MANAGE_USERS)
    )

    fun has(role: Role, capability: Capability): Boolean =
        matrix[role].orEmpty().contains(capability)

    fun canView(user: User, ticket: Ticket): Boolean = when (user.role) {
        Role.REQUESTER -> ticket.requesterEmail == user.email
        Role.OPERATOR -> ticket.assigneeEmail == null || ticket.assigneeEmail == user.email
        else -> has(user.role, Capability.VIEW_ALL_TICKETS)
    }

    fun availableStatuses(user: User, ticket: Ticket): List<TicketStatus> {
        if (!has(user.role, Capability.CHANGE_STATUS)) return emptyList()
        val ownedByAnotherOperator = user.role == Role.OPERATOR &&
                ticket.assigneeEmail != null &&
                ticket.assigneeEmail != user.email
        if (ownedByAnotherOperator) return emptyList()
        // Supervisors assign tickets instead of moving NEW to ASSIGNED.
        return ticket.status.nextStatuses()
            .filter { it != TicketStatus.ASSIGNED || user.role == Role.OPERATOR }
    }

    fun canAssign(user: User, ticket: Ticket): Boolean =
        has(user.role, Capability.ASSIGN_TICKET) && ticket.status.isOpen

    fun canComment(user: User, ticket: Ticket): Boolean =
        has(user.role, Capability.COMMENT) && canView(user, ticket)
}

data class DemoAccount(val user: User, val passwordHash: String)

internal fun hashPassword(password: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(password.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

object DemoAccounts {
    const val DEMO_PASSWORD = "Demo1234!"

    val all: List<DemoAccount> = listOf(
        account(1, "Sofía Rojas", "solicitante@panalbit.test", Role.REQUESTER),
        account(2, "Tomás Vera", "solicitante2@panalbit.test", Role.REQUESTER),
        account(3, "Diego Pérez", "resolutor@panalbit.test", Role.OPERATOR),
        account(4, "Valentina Mora", "resolutor2@panalbit.test", Role.OPERATOR),
        account(5, "Carla Soto", "supervisor@panalbit.test", Role.SUPERVISOR),
        account(6, "Martín León", "gerencia@panalbit.test", Role.MANAGEMENT),
        account(7, "Andrea Díaz", "admin@panalbit.test", Role.ADMIN)
    )

    val users: List<User>
        get() = all.map { it.user }

    private fun account(id: Long, name: String, email: String, role: Role) =
        DemoAccount(User(id, name, email, role), hashPassword(DEMO_PASSWORD))
}

interface AuthRepository {
    val currentUser: StateFlow<User?>
    suspend fun login(email: String, password: String): AppResult<User>
    suspend fun restore(email: String): User?
    fun logout()
}

class InMemoryAuthRepository : AuthRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    override suspend fun login(email: String, password: String): AppResult<User> {
        delay(LOGIN_LATENCY_MS)
        val account = DemoAccounts.all.firstOrNull { it.user.email.equals(email.trim(), ignoreCase = true) }
        if (account == null || account.passwordHash != hashPassword(password)) {
            return AppResult.Failure(AppError.INVALID_CREDENTIALS)
        }
        if (!account.user.active) return AppResult.Failure(AppError.INACTIVE_USER)
        _currentUser.value = account.user
        return AppResult.Success(account.user)
    }

    override suspend fun restore(email: String): User? {
        val account = DemoAccounts.all.firstOrNull { it.user.email.equals(email.trim(), ignoreCase = true) }
        val user = account?.user?.takeIf { it.active }
        _currentUser.value = user
        return user
    }

    override fun logout() {
        _currentUser.value = null
    }

    private companion object {
        const val LOGIN_LATENCY_MS = 400L
    }
}

interface TicketRepository {
    fun observeTickets(user: User): Flow<List<Ticket>>
    fun observeTicket(ticketId: String): Flow<Ticket?>
    fun observeHistory(ticketId: String): Flow<List<HistoryEntry>>
    suspend fun createTicket(actor: User, draft: NewTicketDraft): AppResult<String>
    suspend fun changeStatus(actor: User, ticketId: String, newStatus: TicketStatus, note: String): AppResult<Unit>
    suspend fun assignTicket(actor: User, ticketId: String, assigneeId: Long): AppResult<Unit>
}

private object DemoTickets {
    private const val HOUR = 3_600_000L
    private const val TICKET_COUNT = 24

    private class Template(val title: String, val description: String, val category: Category)

    private val templates = listOf(
        Template("Falla en terminal POS de caja 3", "El terminal no procesa pagos con tarjeta desde la apertura.", Category.STORE),
        Template("Impresora de boletas atascada", "La impresora corta las boletas y detiene la fila de pago.", Category.STORE),
        Template("Receta retenida sin registro", "No se puede registrar la receta retenida de un medicamento controlado.", Category.PHARMACY),
        Template("Alerta de temperatura en refrigerador", "El refrigerador de medicamentos marca una temperatura fuera de rango.", Category.PHARMACY),
        Template("Proveedor no puede cargar guía de despacho", "El portal rechaza la guía de despacho por un error de formato.", Category.SUPPLIER),
        Template("Caída de red Wi-Fi en bodega", "Los lectores de código de barras pierden conexión de forma intermitente.", Category.INFRASTRUCTURE),
        Template("VPN de sucursal intermitente", "La conexión VPN se corta cada pocos minutos en horario punta.", Category.INFRASTRUCTURE),
        Template("Restablecer contraseña de usuario interno", "El usuario quedó bloqueado tras varios intentos fallidos.", Category.INTERNAL_SUPPORT),
        Template("Documento de cobranza sin firma digital", "El documento no puede enviarse al cliente porque falta la firma.", Category.LEGAL)
    )

    private val branches = listOf(
        "Sucursal Providencia",
        "Sucursal Las Condes",
        "Sucursal Maipú",
        "Farmacia Santiago Centro",
        "Bodega Quilicura",
        "Oficina Central Alameda"
    )

    fun build(now: Long): Pair<List<Ticket>, List<HistoryEntry>> {
        val requesters = DemoAccounts.users.filter { it.role == Role.REQUESTER }
        val operators = DemoAccounts.users.filter { it.role == Role.OPERATOR }
        val random = Random(2026)
        val tickets = mutableListOf<Ticket>()
        val history = mutableListOf<HistoryEntry>()
        var historyId = 1L

        for (index in 0 until TICKET_COUNT) {
            val template = templates[index % templates.size]
            val requester = requesters[index % requesters.size]
            val operator = operators[index % operators.size]
            val status = TicketStatus.entries[(index * 5 + index / 4) % TicketStatus.entries.size]
            val createdAt = now - (index + 1) * 5L * HOUR - random.nextLong(0, 3 * HOUR)
            val path = pathTo(status)
            val times = path.indices.map { step ->
                minOf(now - 60_000L, createdAt + step * random.nextLong(HOUR, 3 * HOUR))
            }
            val assigned = status != TicketStatus.NEW
            val id = String.format(Locale.ROOT, "PNL-%04d", index + 1)

            tickets += Ticket(
                id = id,
                title = template.title,
                description = template.description,
                category = template.category,
                priority = Priority.entries[(index * 7 + 2) % Priority.entries.size],
                status = status,
                branch = branches[index % branches.size],
                requesterEmail = requester.email,
                requesterName = requester.name,
                assigneeEmail = if (assigned) operator.email else null,
                assigneeName = if (assigned) operator.name else null,
                createdAt = createdAt,
                updatedAt = times.last()
            )
            history += HistoryEntry(historyId++, id, requester.name, HistoryType.CREATED, "", null, createdAt)
            for (step in 1 until path.size) {
                val isAssignment = step == 1
                history += HistoryEntry(
                    id = historyId++,
                    ticketId = id,
                    actorName = operator.name,
                    type = if (isAssignment) HistoryType.ASSIGNED else HistoryType.STATUS_CHANGED,
                    detail = if (isAssignment) operator.name else "${path[step - 1].name}>${path[step].name}",
                    note = null,
                    createdAt = times[step]
                )
            }
        }
        return tickets to history
    }

    private fun pathTo(status: TicketStatus): List<TicketStatus> = when (status) {
        TicketStatus.NEW -> listOf(TicketStatus.NEW)
        TicketStatus.ASSIGNED -> listOf(TicketStatus.NEW, TicketStatus.ASSIGNED)
        TicketStatus.IN_PROGRESS -> listOf(TicketStatus.NEW, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS)
        TicketStatus.ON_HOLD -> listOf(
            TicketStatus.NEW, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.ON_HOLD
        )
        TicketStatus.RESOLVED -> listOf(
            TicketStatus.NEW, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS, TicketStatus.RESOLVED
        )
        TicketStatus.CLOSED -> listOf(
            TicketStatus.NEW, TicketStatus.ASSIGNED, TicketStatus.IN_PROGRESS,
            TicketStatus.RESOLVED, TicketStatus.CLOSED
        )
    }
}

class InMemoryTicketRepository(
    private val clock: () -> Long = System::currentTimeMillis
) : TicketRepository {

    private val tickets = MutableStateFlow<List<Ticket>>(emptyList())
    private val history = MutableStateFlow<List<HistoryEntry>>(emptyList())
    private val writeLock = Mutex()
    private var nextHistoryId = 1L

    init {
        val (seedTickets, seedHistory) = DemoTickets.build(clock())
        tickets.value = seedTickets
        history.value = seedHistory
        nextHistoryId = seedHistory.size + 1L
    }

    override fun observeTickets(user: User): Flow<List<Ticket>> =
        tickets.map { list ->
            list.filter { Permissions.canView(user, it) }.sortedByDescending { it.updatedAt }
        }

    override fun observeTicket(ticketId: String): Flow<Ticket?> =
        tickets.map { list -> list.firstOrNull { it.id == ticketId } }

    override fun observeHistory(ticketId: String): Flow<List<HistoryEntry>> =
        history.map { list ->
            list.filter { it.ticketId == ticketId }.sortedByDescending { it.createdAt }
        }

    override suspend fun createTicket(actor: User, draft: NewTicketDraft): AppResult<String> {
        if (!Permissions.has(actor.role, Capability.CREATE_TICKET)) {
            return AppResult.Failure(AppError.PERMISSION_DENIED)
        }
        val invalid = draft.title.trim().length < TITLE_MIN ||
                draft.description.trim().length < DESCRIPTION_MIN ||
                draft.branch.isBlank()
        if (invalid) return AppResult.Failure(AppError.INVALID_INPUT)

        return writeLock.withLock {
            val now = clock()
            val id = nextTicketId()
            val ticket = Ticket(
                id = id,
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
            tickets.update { listOf(ticket) + it }
            record(id, actor, HistoryType.CREATED, "", now)
            AppResult.Success(id)
        }
    }

    override suspend fun changeStatus(
        actor: User,
        ticketId: String,
        newStatus: TicketStatus,
        note: String
    ): AppResult<Unit> = writeLock.withLock {
        val ticket = tickets.value.firstOrNull { it.id == ticketId }
            ?: return@withLock AppResult.Failure(AppError.NOT_FOUND)
        if (!Permissions.canView(actor, ticket)) return@withLock AppResult.Failure(AppError.PERMISSION_DENIED)
        val allowed = Permissions.availableStatuses(actor, ticket)
        if (allowed.isEmpty()) return@withLock AppResult.Failure(AppError.PERMISSION_DENIED)
        if (newStatus !in allowed) return@withLock AppResult.Failure(AppError.INVALID_TRANSITION)
        if (note.trim().length < NOTE_MIN) return@withLock AppResult.Failure(AppError.INVALID_INPUT)

        val now = clock()
        val takesOwnership = newStatus == TicketStatus.ASSIGNED && ticket.assigneeEmail == null
        replace(
            ticket.copy(
                status = newStatus,
                assigneeEmail = if (takesOwnership) actor.email else ticket.assigneeEmail,
                assigneeName = if (takesOwnership) actor.name else ticket.assigneeName,
                updatedAt = now
            )
        )
        record(ticketId, actor, HistoryType.STATUS_CHANGED, "${ticket.status.name}>${newStatus.name}", now, note.trim())
        AppResult.Success(Unit)
    }

    override suspend fun assignTicket(actor: User, ticketId: String, assigneeId: Long): AppResult<Unit> =
        writeLock.withLock {
            val ticket = tickets.value.firstOrNull { it.id == ticketId }
                ?: return@withLock AppResult.Failure(AppError.NOT_FOUND)
            if (!Permissions.canAssign(actor, ticket)) return@withLock AppResult.Failure(AppError.PERMISSION_DENIED)
            val assignee = DemoAccounts.users.firstOrNull {
                it.id == assigneeId && it.role == Role.OPERATOR && it.active
            } ?: return@withLock AppResult.Failure(AppError.NOT_FOUND)

            val now = clock()
            val newStatus = if (ticket.status == TicketStatus.NEW) TicketStatus.ASSIGNED else ticket.status
            replace(
                ticket.copy(
                    assigneeEmail = assignee.email,
                    assigneeName = assignee.name,
                    status = newStatus,
                    updatedAt = now
                )
            )
            record(ticketId, actor, HistoryType.ASSIGNED, assignee.name, now)
            AppResult.Success(Unit)
        }

    private fun replace(updated: Ticket) {
        tickets.update { list -> list.map { if (it.id == updated.id) updated else it } }
    }

    private fun record(ticketId: String, actor: User, type: HistoryType, detail: String, now: Long, note: String? = null) {
        val entry = HistoryEntry(nextHistoryId++, ticketId, actor.name, type, detail, note, now)
        history.update { it + entry }
    }

    private fun nextTicketId(): String {
        val last = tickets.value.maxOfOrNull { it.id.removePrefix("PNL-").toIntOrNull() ?: 0 } ?: 0
        return String.format(Locale.ROOT, "PNL-%04d", last + 1)
    }

    private companion object {
        const val TITLE_MIN = 5
        const val DESCRIPTION_MIN = 10
        const val NOTE_MIN = 5
    }
}

interface CommentRepository {
    fun observeComments(ticketId: String): Flow<List<Comment>>
    suspend fun addComment(actor: User, ticketId: String, text: String): AppResult<Unit>
}

class InMemoryCommentRepository(
    private val tickets: TicketRepository,
    private val clock: () -> Long = System::currentTimeMillis
) : CommentRepository {

    private val comments = MutableStateFlow<List<Comment>>(emptyList())
    private val writeLock = Mutex()
    private var nextId = 1L

    override fun observeComments(ticketId: String): Flow<List<Comment>> =
        comments.map { list -> list.filter { it.ticketId == ticketId }.sortedBy { it.createdAt } }

    override suspend fun addComment(actor: User, ticketId: String, text: String): AppResult<Unit> {
        val ticket = tickets.observeTicket(ticketId).first()
            ?: return AppResult.Failure(AppError.NOT_FOUND)
        if (!Permissions.canComment(actor, ticket)) return AppResult.Failure(AppError.PERMISSION_DENIED)
        val trimmed = text.trim()
        if (trimmed.length < COMMENT_MIN || trimmed.length > COMMENT_MAX) {
            return AppResult.Failure(AppError.INVALID_INPUT)
        }
        writeLock.withLock {
            val comment = Comment(nextId++, ticketId, actor.name, actor.email, trimmed, clock())
            comments.update { it + comment }
        }
        return AppResult.Success(Unit)
    }

    private companion object {
        const val COMMENT_MIN = 3
        const val COMMENT_MAX = 500
    }
}

object RepositoryProvider {
    val auth: AuthRepository by lazy { InMemoryAuthRepository() }
    val tickets: TicketRepository by lazy { InMemoryTicketRepository() }
    val comments: CommentRepository by lazy { InMemoryCommentRepository(tickets) }
}