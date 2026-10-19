package com.example.evaluacion1.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evaluacion1.model.User
import com.example.evaluacion1.repository.AppError
import com.example.evaluacion1.repository.AppResult
import com.example.evaluacion1.repository.AuthRepository
import com.example.evaluacion1.repository.DemoAccounts
import com.example.evaluacion1.repository.RepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val isLoading: Boolean = false,
    val loginError: AppError? = null,
    val loggedInUser: User? = null
)

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value, emailError = null, loginError = null) }
    }

    fun onPasswordChange(value: String) {
        _state.update { it.copy(password = value, passwordError = null, loginError = null) }
    }

    fun fillDemoAccount(email: String) {
        _state.update {
            it.copy(
                email = email,
                password = DemoAccounts.DEMO_PASSWORD,
                emailError = null,
                passwordError = null,
                loginError = null
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isLoading) return

        val emailError = Validators.email(current.email)
        val passwordError = Validators.required(current.password)
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _state.update { it.copy(isLoading = true, loginError = null) }
        viewModelScope.launch {
            when (val result = auth.login(current.email, current.password)) {
                is AppResult.Success ->
                    _state.update { it.copy(isLoading = false, loggedInUser = result.data) }

                is AppResult.Failure ->
                    _state.update { it.copy(isLoading = false, loginError = result.error) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { LoginViewModel(RepositoryProvider.auth) }
        }
    }
}
