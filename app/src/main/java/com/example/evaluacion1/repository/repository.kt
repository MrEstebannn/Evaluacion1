package com.example.evaluacion1.repository

import com.example.evaluacion1.model.Role
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

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

/** Single source of truth for role based access control. */
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

/** Demo-only hashing. A real backend must use a salted slow hash (bcrypt/Argon2). */
internal fun hashPassword(password: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(password.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** Fictional users, one or more per role. */
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

    override fun logout() {
        _currentUser.value = null
    }

    private companion object {
        const val LOGIN_LATENCY_MS = 400L
    }
}