package com.example.evaluacion1.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/** Persists the e-mail of the logged-in demo user so the session survives app restarts. */
class SessionStore(private val context: Context) {

    val savedEmail: Flow<String?> = context.sessionDataStore.data.map { it[EMAIL_KEY] }

    suspend fun save(email: String) {
        context.sessionDataStore.edit { it[EMAIL_KEY] = email }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(EMAIL_KEY) }
    }

    private companion object {
        val EMAIL_KEY = stringPreferencesKey("user_email")
    }
}
