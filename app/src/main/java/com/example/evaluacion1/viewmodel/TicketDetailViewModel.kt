package com.example.evaluacion1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evaluacion1.model.Comment
import com.example.evaluacion1.model.HistoryEntry
import com.example.evaluacion1.model.Role
import com.example.evaluacion1.model.Ticket
import com.example.evaluacion1.model.TicketStatus
import com.example.evaluacion1.model.User
import com.example.evaluacion1.repository.AppError
import com.example.evaluacion1.repository.AppResult
import com.example.evaluacion1.repository.CommentRepository
import com.example.evaluacion1.repository.DemoAccounts
import com.example.evaluacion1.repository.Permissions
import com.example.evaluacion1.repository.RepositoryProvider
import com.example.evaluacion1.repository.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DetailMessage {
    STATUS_UPDATED,
    ASSIGNED,
    COMMENT_ADDED,
    NO_PERMISSION,
    INVALID_TRANSITION,
    INVALID_INPUT,
    NOT_FOUND
}

data class DetailForm(
    val pendingStatus: TicketStatus? = null,
    val note: String = "",
    val noteError: FieldError? = null,
    val showAssignDialog: Boolean = false,
    val commentText: String = "",
    val commentError: FieldError? = null,
    val isBusy: Boolean = false,
    val message: DetailMessage? = null
)

data class TicketDetailUiState(
    val isLoading: Boolean = true,
    val ticket: Ticket? = null,
    val history: List<HistoryEntry> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val availableStatuses: List<TicketStatus> = emptyList(),
    val canAssign: Boolean = false,
    val canComment: Boolean = false,
    val operators: List<User> = emptyList(),
    val form: DetailForm = DetailForm()
)

class TicketDetailViewModel(
    private val tickets: TicketRepository,
    private val comments: CommentRepository,
    private val user: User,
    private val ticketId: String
) : ViewModel() {

    private val form = MutableStateFlow(DetailForm())

    val state: StateFlow<TicketDetailUiState> = combine(
        tickets.observeTicket(ticketId),
        tickets.observeHistory(ticketId),
        comments.observeComments(ticketId),
        form
    ) { ticket, history, commentList, formState ->
        val visible = ticket?.takeIf { Permissions.canView(user, it) }
        TicketDetailUiState(
            isLoading = false,
            ticket = visible,
            history = history,
            comments = commentList,
            availableStatuses = visible?.let { Permissions.availableStatuses(user, it) }.orEmpty(),
            canAssign = visible?.let { Permissions.canAssign(user, it) } ?: false,
            canComment = visible?.let { Permissions.canComment(user, it) } ?: false,
            operators = DemoAccounts.users.filter { it.role == Role.OPERATOR && it.active },
            form = formState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = TicketDetailUiState()
    )

    // ---- Status change

    fun startStatusChange(status: TicketStatus) {
        form.update { it.copy(pendingStatus = status, note = "", noteError = null) }
    }

    fun cancelStatusChange() {
        form.update { it.copy(pendingStatus = null, note = "", noteError = null) }
    }

    fun onNoteChange(value: String) {
        form.update { it.copy(note = value, noteError = null) }
    }

    fun confirmStatusChange() {
        val current = form.value
        val target = current.pendingStatus ?: return
        val error = Validators.length(current.note, NOTE_MIN)
        if (error != null) {
            form.update { it.copy(noteError = error) }
            return
        }
        runAction(
            successMessage = DetailMessage.STATUS_UPDATED,
            onSuccess = { it.copy(pendingStatus = null, note = "", noteError = null) }
        ) { tickets.changeStatus(user, ticketId, target, current.note) }
    }

    // ---- Assignment

    fun openAssignDialog() {
        form.update { it.copy(showAssignDialog = true) }
    }

    fun closeAssignDialog() {
        form.update { it.copy(showAssignDialog = false) }
    }

    fun assign(operator: User) {
        runAction(
            successMessage = DetailMessage.ASSIGNED,
            onSuccess = { it.copy(showAssignDialog = false) }
        ) { tickets.assignTicket(user, ticketId, operator.id) }
    }

    // ---- Comments

    fun onCommentChange(value: String) {
        form.update { it.copy(commentText = value.take(COMMENT_MAX), commentError = null) }
    }

    fun sendComment() {
        val text = form.value.commentText
        val error = Validators.length(text, COMMENT_MIN, COMMENT_MAX)
        if (error != null) {
            form.update { it.copy(commentError = error) }
            return
        }
        runAction(
            successMessage = DetailMessage.COMMENT_ADDED,
            onSuccess = { it.copy(commentText = "", commentError = null) }
        ) { comments.addComment(user, ticketId, text) }
    }

    fun consumeMessage() {
        form.update { it.copy(message = null) }
    }

    private fun runAction(
        successMessage: DetailMessage,
        onSuccess: (DetailForm) -> DetailForm,
        block: suspend () -> AppResult<Unit>
    ) {
        if (form.value.isBusy) return
        form.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            when (val result = block()) {
                is AppResult.Success ->
                    form.update { onSuccess(it).copy(isBusy = false, message = successMessage) }

                is AppResult.Failure ->
                    form.update {
                        it.copy(
                            pendingStatus = null,
                            showAssignDialog = false,
                            isBusy = false,
                            message = result.error.toMessage()
                        )
                    }
            }
        }
    }

    private fun AppError.toMessage(): DetailMessage = when (this) {
        AppError.PERMISSION_DENIED -> DetailMessage.NO_PERMISSION
        AppError.INVALID_TRANSITION -> DetailMessage.INVALID_TRANSITION
        AppError.NOT_FOUND -> DetailMessage.NOT_FOUND
        else -> DetailMessage.INVALID_INPUT
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val NOTE_MIN = 5
        private const val COMMENT_MIN = 3
        const val COMMENT_MAX = 500

        fun factory(user: User, ticketId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                TicketDetailViewModel(
                    RepositoryProvider.tickets,
                    RepositoryProvider.comments,
                    user,
                    ticketId
                )
            }
        }
    }
}
