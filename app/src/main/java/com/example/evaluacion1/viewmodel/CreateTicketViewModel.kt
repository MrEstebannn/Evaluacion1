package com.example.evaluacion1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evaluacion1.model.Category
import com.example.evaluacion1.model.NewTicketDraft
import com.example.evaluacion1.model.Priority
import com.example.evaluacion1.model.User
import com.example.evaluacion1.repository.AppError
import com.example.evaluacion1.repository.AppResult
import com.example.evaluacion1.repository.RepositoryProvider
import com.example.evaluacion1.repository.TicketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateTicketUiState(
    val title: String = "",
    val description: String = "",
    val category: Category = Category.INTERNAL_SUPPORT,
    val priority: Priority = Priority.MEDIUM,
    val branch: String = "",
    val titleError: FieldError? = null,
    val descriptionError: FieldError? = null,
    val branchError: FieldError? = null,
    val isSubmitting: Boolean = false,
    val submitError: AppError? = null,
    val createdTicketId: String? = null
)

class CreateTicketViewModel(
    private val repository: TicketRepository,
    private val user: User
) : ViewModel() {

    private val _state = MutableStateFlow(CreateTicketUiState())
    val state: StateFlow<CreateTicketUiState> = _state.asStateFlow()

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value.take(TITLE_MAX), titleError = null) }
    }

    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value.take(DESCRIPTION_MAX), descriptionError = null) }
    }

    fun onCategoryChange(value: Category) {
        _state.update { it.copy(category = value) }
    }

    fun onPriorityChange(value: Priority) {
        _state.update { it.copy(priority = value) }
    }

    fun onBranchChange(value: String) {
        _state.update { it.copy(branch = value, branchError = null) }
    }

    fun submit() {
        val current = _state.value
        if (current.isSubmitting) return

        val titleError = Validators.length(current.title, TITLE_MIN, TITLE_MAX)
        val descriptionError = Validators.length(current.description, DESCRIPTION_MIN, DESCRIPTION_MAX)
        val branchError = Validators.required(current.branch)
        if (titleError != null || descriptionError != null || branchError != null) {
            _state.update {
                it.copy(
                    titleError = titleError,
                    descriptionError = descriptionError,
                    branchError = branchError
                )
            }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val draft = NewTicketDraft(
                title = current.title,
                description = current.description,
                category = current.category,
                priority = current.priority,
                branch = current.branch
            )
            when (val result = repository.createTicket(user, draft)) {
                is AppResult.Success ->
                    _state.update { it.copy(isSubmitting = false, createdTicketId = result.data) }

                is AppResult.Failure ->
                    _state.update { it.copy(isSubmitting = false, submitError = result.error) }
            }
        }
    }

    companion object {
        const val TITLE_MIN = 5
        const val TITLE_MAX = 80
        const val DESCRIPTION_MIN = 10
        const val DESCRIPTION_MAX = 500

        val BRANCHES = listOf(
            "Sucursal Providencia",
            "Sucursal Las Condes",
            "Sucursal Maipú",
            "Farmacia Santiago Centro",
            "Bodega Quilicura",
            "Oficina Central Alameda"
        )

        fun factory(user: User): ViewModelProvider.Factory = viewModelFactory {
            initializer { CreateTicketViewModel(RepositoryProvider.tickets, user) }
        }
    }
}
