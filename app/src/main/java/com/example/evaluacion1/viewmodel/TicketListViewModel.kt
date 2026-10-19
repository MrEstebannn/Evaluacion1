package com.example.evaluacion1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.model.User
import com.example.evaluacion1.repository.Capability
import com.example.evaluacion1.repository.Permissions
import com.example.evaluacion1.repository.RepositoryProvider
import com.example.evaluacion1.repository.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TicketSummary(
    val open: Int = 0,
    val inProgress: Int = 0,
    val onHold: Int = 0,
    val finished: Int = 0
)

data class TicketListUiState(
    val isLoading: Boolean = true,
    val tickets: List<Ticket> = emptyList(),
    val totalCount: Int = 0,
    val filter: TicketStatus? = null,
    val query: String = "",
    val summary: TicketSummary = TicketSummary(),
    val workload: List<Pair<String, Int>> = emptyList(),
    val canCreate: Boolean = false,
    val showSummary: Boolean = false
)

class TicketListViewModel(
    repository: TicketRepository,
    val user: User
) : ViewModel() {

    private val filter = MutableStateFlow<TicketStatus?>(null)
    private val query = MutableStateFlow("")
    private val canCreate = Permissions.has(user.role, Capability.CREATE_TICKET)
    private val showSummary = Permissions.has(user.role, Capability.VIEW_ALL_TICKETS)

    val state: StateFlow<TicketListUiState> = combine(
        repository.observeTickets(user),
        filter,
        query
    ) { all, status, text ->
        TicketListUiState(
            isLoading = false,
            tickets = all.filter { (status == null || it.status == status) && it.matches(text) },
            totalCount = all.size,
            filter = status,
            query = text,
            summary = TicketSummary(
                open = all.count { it.status.isOpen },
                inProgress = all.count { it.status == TicketStatus.IN_PROGRESS },
                onHold = all.count { it.status == TicketStatus.ON_HOLD },
                finished = all.count { !it.status.isOpen }
            ),
            workload = all
                .filter { it.status.isOpen }
                .mapNotNull { it.assigneeName }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second },
            canCreate = canCreate,
            showSummary = showSummary
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TicketListUiState(canCreate = canCreate, showSummary = showSummary)
    )

    fun onFilterChange(status: TicketStatus?) {
        filter.value = status
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    private fun Ticket.matches(text: String): Boolean {
        val q = text.trim()
        return q.isEmpty() || listOf(id, title, branch).any { it.contains(q, ignoreCase = true) }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L

        fun factory(user: User): ViewModelProvider.Factory = viewModelFactory {
            initializer { TicketListViewModel(RepositoryProvider.tickets, user) }
        }
    }
}
