package com.kitoko.packer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PackedOrderStore(context: Context) {
    private val dataStore: DataStore<Preferences> =
        androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("packed_orders") }
        )

    private val lastOrderKey = stringPreferencesKey("last_order_id")
    private val packedSetKey = stringSetPreferencesKey("packed_orders")

    val packedOrders: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[packedSetKey] ?: emptySet()
        }

    val lastOrderId: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> prefs[lastOrderKey] }

    suspend fun markPacked(orderId: String) {
        dataStore.edit { prefs ->
            val current = prefs[packedSetKey]?.toMutableSet() ?: mutableSetOf()
            current += orderId
            prefs[packedSetKey] = current
            prefs[lastOrderKey] = orderId
        }
    }

    suspend fun resetOrder(orderId: String) {
        dataStore.edit { prefs ->
            val current = prefs[packedSetKey]?.toMutableSet() ?: mutableSetOf()
            current -= orderId
            prefs[packedSetKey] = current
            if (prefs[lastOrderKey] == orderId) {
                prefs.remove(lastOrderKey)
            }
        }
    }

    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.remove(packedSetKey)
            prefs.remove(lastOrderKey)
        }
    }
}
