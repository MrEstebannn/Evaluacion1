package com.example.evaluacion1.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.evaluacion1.model.User
import com.example.evaluacion1.repository.AuthRepository
import com.example.evaluacion1.repository.RepositoryProvider
import com.example.evaluacion1.repository.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object LoggedOut : SessionState
    data class LoggedIn(val user: User) : SessionState
}

class SessionViewModel(
    private val auth: AuthRepository,
    private val store: SessionStore
) : ViewModel() {

    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val email = store.savedEmail.first()
            val user = email?.let { auth.restore(it) }
            _state.value = if (user != null) SessionState.LoggedIn(user) else SessionState.LoggedOut
        }
    }

    fun onLoggedIn(user: User) {
        viewModelScope.launch {
            store.save(user.email)
            _state.value = SessionState.LoggedIn(user)
        }
    }

    fun logout() {
        viewModelScope.launch {
            auth.logout()
            store.clear()
            _state.value = SessionState.LoggedOut
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                SessionViewModel(RepositoryProvider.auth, SessionStore(app))
            }
        }
    }
}
