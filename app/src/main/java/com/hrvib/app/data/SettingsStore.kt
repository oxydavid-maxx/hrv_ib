package com.hrvib.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private val keyDemoMode = booleanPreferencesKey("demo_mode")
    private val keyShowExcluded = booleanPreferencesKey("show_excluded")

    val demoMode: Flow<Boolean> = context.dataStore.data.map { it[keyDemoMode] ?: true }
    val showExcluded: Flow<Boolean> = context.dataStore.data.map { it[keyShowExcluded] ?: false }

    suspend fun setDemoMode(enabled: Boolean) {
        context.dataStore.edit { it[keyDemoMode] = enabled }
    }

    suspend fun setShowExcluded(enabled: Boolean) {
        context.dataStore.edit { it[keyShowExcluded] = enabled }
    }
}
