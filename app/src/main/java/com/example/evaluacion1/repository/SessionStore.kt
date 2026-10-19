package com.example.evaluacion1.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "session")

class SessionStore(private val context: Context) {
    private val emailKey = stringPreferencesKey("saved_email")

    val savedEmail: Flow<String?> = context.dataStore.data
        .map { preferences -> preferences[emailKey] }

    suspend fun save(email: String) {
        context.dataStore.edit { preferences ->
            preferences[emailKey] = email
        }
    }

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.remove(emailKey)
        }
    }
}
