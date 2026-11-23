package com.example

import android.content.Context
import com.example.core.database.DialMeDatabase
import com.example.core.datastore.PreferencesRepository
import com.example.core.telecom.TelecomCallPlacer
import com.example.data.CallHistoryRepository
import com.example.data.ContactsRepository
import com.example.data.DialMeLocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DialMeAppContainer(context: Context) {
    val applicationContext: Context = context.applicationContext
    val database: DialMeDatabase = DialMeDatabase.build(applicationContext)
    val preferencesRepository: PreferencesRepository = PreferencesRepository(applicationContext)
    val telecomCallPlacer: TelecomCallPlacer = TelecomCallPlacer(applicationContext)
    val contactsRepository: ContactsRepository = ContactsRepository(
        context = applicationContext,
        t9Dao = database.t9ContactCacheDao(),
        extensionDao = database.contactExtensionDao()
    )
    val callHistoryRepository: CallHistoryRepository = CallHistoryRepository(
        context = applicationContext,
        callNoteDao = database.callNoteDao()
    )
    val localRepository: DialMeLocalRepository = DialMeLocalRepository(
        context = applicationContext,
        db = database,
        prefsRepo = preferencesRepository
    )

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        appScope.launch {
            localRepository.ensureSeedDefaults()
        }
    }
}
