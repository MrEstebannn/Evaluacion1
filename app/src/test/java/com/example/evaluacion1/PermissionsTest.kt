package com.example.evaluacion1

import com.example.evaluacion1.model.Category
import com.example.evaluacion1.model.Priority
import com.example.evaluacion1.model.Role
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.model.User
import com.example.evaluacion1.repository.Capability
import com.example.evaluacion1.repository.Permissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionsTest {

    private val requester = User(1, "Sofía", "solicitante@panalbit.test", Role.REQUESTER)
    private val otherRequester = User(2, "Tomás", "solicitante2@panalbit.test", Role.REQUESTER)
    private val operator = User(3, "Diego", "resolutor@panalbit.test", Role.OPERATOR)
    private val supervisor = User(5, "Carla", "supervisor@panalbit.test", Role.SUPERVISOR)

    private fun ticket(status: TicketStatus = TicketStatus.NEW) = Ticket(
        id = "PNL-0001",
        title = "Falla en POS",
        description = "El terminal no procesa pagos",
        category = Category.STORE,
        priority = Priority.HIGH,
        status = status,
        branch = "Sucursal Maipú",
        requesterEmail = requester.email,
        requesterName = requester.name,
        assigneeEmail = null,
        assigneeName = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Test
    fun requester_cannotChangeStatus() {
        assertFalse(Permissions.has(Role.REQUESTER, Capability.CHANGE_STATUS))
    }

    @Test
    fun requester_seesOnlyOwnTickets() {
        assertTrue(Permissions.canView(requester, ticket()))
        assertFalse(Permissions.canView(otherRequester, ticket()))
    }

    @Test
    fun supervisor_canAssignOpenTicketsOnly() {
        assertTrue(Permissions.canAssign(supervisor, ticket(TicketStatus.NEW)))
        assertFalse(Permissions.canAssign(supervisor, ticket(TicketStatus.CLOSED)))
    }

    @Test
    fun closedTicket_hasNoNextStatuses() {
        assertTrue(Permissions.availableStatuses(supervisor, ticket(TicketStatus.CLOSED)).isEmpty())
    }

    @Test
    fun operator_canTakeNewTicket() {
        assertEquals(
            listOf(TicketStatus.ASSIGNED),
            Permissions.availableStatuses(operator, ticket(TicketStatus.NEW))
        )
    }
}
