package com.example.data

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.example.core.common.PhoneFormatUtils
import com.example.core.common.T9Utils
import com.example.core.database.ContactExtensionDao
import com.example.core.database.ContactExtensionEntity
import com.example.core.database.T9ContactCacheDao
import com.example.core.database.T9ContactCacheEntity
import com.example.core.permissions.PermissionHelper
import com.example.domain.model.ContactGroup
import com.example.domain.model.ContactItem
import com.example.domain.model.DuplicateGroup
import com.example.domain.model.PhoneEntry
import com.example.domain.model.SpamFlag
import com.example.domain.model.T9MatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

class ContactsRepository(
    private val context: Context,
    private val t9Dao: T9ContactCacheDao,
    private val extensionDao: ContactExtensionDao
) {

    private fun observeSystemContactsChange(): Flow<Unit> = callbackFlow {
        trySend(Unit)
        if (!PermissionHelper.has(context, Manifest.permission.READ_CONTACTS)) {
            awaitClose { }
            return@callbackFlow
        }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            observer
        )
        awaitClose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    fun observeContacts(): Flow<List<ContactItem>> {
        return combine(
            observeSystemContactsChange(),
            extensionDao.observeAll()
        ) { _, extensions ->
            loadContactsFromProvider(extensions)
        }.flowOn(Dispatchers.IO)
    }

    fun observeT9Cache(): Flow<List<T9ContactCacheEntity>> = t9Dao.observeAll()

    suspend fun syncT9Cache(contacts: List<ContactItem>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entities = contacts.flatMap { contact ->
            contact.phoneNumbers.map { phone ->
                T9ContactCacheEntity(
                    contactId = contact.contactId,
                    lookupKey = contact.lookupKey,
                    displayName = contact.displayName,
                    t9Digits = T9Utils.toCompactT9Digits(contact.displayName),
                    phoneNumber = phone.number,
                    normalizedNumber = phone.normalizedNumber,
                    photoUri = contact.photoUri,
                    isStarred = contact.isStarred,
                    lastSyncedAt = now
                )
            }
        }
        t9Dao.replaceAll(entities)
    }

    fun searchT9(queryDigits: String, cache: List<T9ContactCacheEntity>): List<T9MatchResult> {
        if (queryDigits.isBlank()) return emptyList()
        return cache.mapNotNull { entry ->
            val score = T9Utils.scoreMatch(
                queryDigits = queryDigits,
                displayName = entry.displayName,
                compactT9Name = entry.t9Digits,
                normalizedNumber = entry.normalizedNumber
            )
            if (score > 0) {
                T9MatchResult(
                    contactId = entry.contactId,
                    lookupKey = entry.lookupKey,
                    displayName = entry.displayName,
                    phoneNumber = entry.phoneNumber,
                    normalizedNumber = entry.normalizedNumber,
                    photoUri = entry.photoUri,
                    isStarred = entry.isStarred,
                    score = score
                )
            } else {
                null
            }
        }
            .sortedWith(compareByDescending<T9MatchResult> { it.isStarred }.thenByDescending { it.score })
            .distinctBy { "${it.lookupKey}:${it.normalizedNumber}" }
            .take(30)
    }

    private fun loadContactsFromProvider(extensions: List<ContactExtensionEntity>): List<ContactItem> {
        if (!PermissionHelper.has(context, Manifest.permission.READ_CONTACTS)) {
            return emptyList()
        }
        val extMap = extensions.associateBy { it.contactLookupKey }
        val resolver = context.contentResolver

        val phoneMap = mutableMapOf<Long, MutableList<PhoneEntry>>()
        val contactMeta = mutableMapOf<Long, ContactMeta>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.CUSTOM_RINGTONE
        )

        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idxId = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val idxLookup = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val idxName = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val idxNum = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val idxType = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val idxLabel = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                val idxPhoto = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
                val idxStar = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)
                val idxRing = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CUSTOM_RINGTONE)

                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idxId)
                    val lookup = cursor.getString(idxLookup) ?: continue
                    val name = cursor.getString(idxName) ?: "Unnamed"
                    val rawNumber = cursor.getString(idxNum)?.trim().orEmpty()
                    if (rawNumber.isEmpty()) continue
                    val norm = PhoneFormatUtils.toE164OrNormalized(rawNumber)
                    val type = cursor.getInt(idxType)
                    val customLabel = cursor.getString(idxLabel)
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        customLabel
                    ).toString()

                    val list = phoneMap.getOrPut(contactId) { mutableListOf() }
                    if (list.none { it.normalizedNumber == norm }) {
                        list += PhoneEntry(rawNumber, norm, typeLabel)
                    }
                    if (!contactMeta.containsKey(contactId)) {
                        contactMeta[contactId] = ContactMeta(
                            lookupKey = lookup,
                            displayName = name,
                            photoUri = cursor.getString(idxPhoto),
                            isStarred = cursor.getInt(idxStar) == 1,
                            ringtoneUri = cursor.getString(idxRing)
                        )
                    }
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        val groupMembership = loadGroupMemberships()

        return contactMeta.entries.mapNotNull { (id, meta) ->
            val ext = extMap[meta.lookupKey]
            if (ext?.isHiddenFromMainList == true) return@mapNotNull null
            ContactItem(
                contactId = id,
                lookupKey = meta.lookupKey,
                displayName = meta.displayName,
                phoneNumbers = phoneMap[id].orEmpty(),
                photoUri = meta.photoUri,
                isStarred = meta.isStarred,
                customRingtoneUri = meta.ringtoneUri,
                groupNames = groupMembership[id].orEmpty(),
                localSpamFlag = ext?.localSpamFlag?.let {
                    runCatching { SpamFlag.valueOf(it) }.getOrDefault(SpamFlag.NONE)
                } ?: SpamFlag.NONE
            )
        }.sortedBy { it.displayName.lowercase() }
    }

    private fun loadGroupMemberships(): Map<Long, List<String>> {
        if (!PermissionHelper.has(context, Manifest.permission.READ_CONTACTS)) return emptyMap()
        val titles = mutableMapOf<Long, String>()
        try {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
                "${ContactsContract.Groups.DELETED} = 0",
                null,
                null
            )?.use { c ->
                val iId = c.getColumnIndex(ContactsContract.Groups._ID)
                val iTitle = c.getColumnIndex(ContactsContract.Groups.TITLE)
                while (c.moveToNext()) {
                    val title = c.getString(iTitle)
                    if (!title.isNullOrBlank()) titles[c.getLong(iId)] = title
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }

        val result = mutableMapOf<Long, MutableList<String>>()
        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.CONTACT_ID,
                    ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID
                ),
                "${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE),
                null
            )?.use { c ->
                val iCid = c.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val iGid = c.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
                while (c.moveToNext()) {
                    val gName = titles[c.getLong(iGid)] ?: continue
                    val list = result.getOrPut(c.getLong(iCid)) { mutableListOf() }
                    if (!list.contains(gName)) list += gName
                }
            }
        } catch (_: Exception) {
            return emptyMap()
        }
        return result
    }

    suspend fun getGroups(contacts: List<ContactItem>): List<ContactGroup> = withContext(Dispatchers.IO) {
        if (!PermissionHelper.has(context, Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val list = mutableListOf<ContactGroup>()
        try {
            context.contentResolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
                "${ContactsContract.Groups.DELETED} = 0",
                null,
                "${ContactsContract.Groups.TITLE} ASC"
            )?.use { c ->
                val iId = c.getColumnIndex(ContactsContract.Groups._ID)
                val iTitle = c.getColumnIndex(ContactsContract.Groups.TITLE)
                while (c.moveToNext()) {
                    val title = c.getString(iTitle)?.takeIf { it.isNotBlank() } ?: continue
                    val count = contacts.count { it.groupNames.contains(title) }
                    list += ContactGroup(c.getLong(iId), title, count)
                }
            }
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        list.distinctBy { it.title }
    }

    suspend fun toggleFavorite(contactId: Long, starred: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!PermissionHelper.has(context, Manifest.permission.WRITE_CONTACTS)) return@withContext false
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
        }
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setCustomRingtone(contactId: Long, ringtoneUri: String?): Boolean = withContext(Dispatchers.IO) {
        if (!PermissionHelper.has(context, Manifest.permission.WRITE_CONTACTS)) return@withContext false
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.CUSTOM_RINGTONE, ringtoneUri)
        }
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        try {
            context.contentResolver.update(uri, values, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createContact(name: String, phone: String): Boolean = withContext(Dispatchers.IO) {
        if (!PermissionHelper.has(context, Manifest.permission.WRITE_CONTACTS)) return@withContext false
        if (name.isBlank() || phone.isBlank()) return@withContext false
        val ops = ArrayList<ContentProviderOperation>().apply {
            add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name.trim())
                    .build()
            )
            add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE
                    )
                    .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone.trim())
                    .withValue(
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    )
                    .build()
            )
        }
        try {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteContact(contactId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!PermissionHelper.has(context, Manifest.permission.WRITE_CONTACTS)) return@withContext false
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        try {
            context.contentResolver.delete(uri, null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun findDuplicates(contacts: List<ContactItem>): List<DuplicateGroup> {
        val groups = mutableListOf<DuplicateGroup>()
        val byNormalizedNumber = mutableMapOf<String, MutableList<ContactItem>>()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { entry ->
                val digits = T9Utils.digitsOnly(entry.normalizedNumber).takeLast(9)
                if (digits.length >= 7) {
                    byNormalizedNumber.getOrPut(digits) { mutableListOf() }.add(contact)
                }
            }
        }
        byNormalizedNumber.forEach { (key, list) ->
            val unique = list.distinctBy { it.contactId }
            if (unique.size > 1) {
                groups += DuplicateGroup(key = "num:$key", reason = "Matching Phone Number", contacts = unique)
            }
        }

        val byName = contacts.groupBy { it.displayName.trim().lowercase() }
        byName.forEach { (name, list) ->
            if (name.isNotBlank() && list.size > 1) {
                val ids = list.map { it.contactId }.toSet()
                if (groups.none { g -> g.contacts.map { it.contactId }.toSet() == ids }) {
                    groups += DuplicateGroup(key = "name:$name", reason = "Identical Name", contacts = list)
                }
            }
        }
        return groups
    }

    suspend fun mergeContacts(primaryContactId: Long, duplicateContactId: Long): Boolean =
        withContext(Dispatchers.IO) {
            if (!PermissionHelper.has(context, Manifest.permission.WRITE_CONTACTS)) return@withContext false
            val raw1 = getFirstRawContactId(primaryContactId) ?: return@withContext false
            val raw2 = getFirstRawContactId(duplicateContactId) ?: return@withContext false
            if (raw1 == raw2) return@withContext false
            val values = ContentValues().apply {
                put(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, raw1)
                put(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, raw2)
            }
            try {
                context.contentResolver.update(
                    ContactsContract.AggregationExceptions.CONTENT_URI,
                    values,
                    null,
                    null
                ) > 0
            } catch (_: Exception) {
                false
            }
        }

    private fun getFirstRawContactId(contactId: Long): Long? {
        return context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null
        )?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    }

    fun exportContactsToVcf(contacts: List<ContactItem>): String {
        return buildString {
            contacts.forEach { contact ->
                appendLine("BEGIN:VCARD")
                appendLine("VERSION:3.0")
                appendLine("FN:${contact.displayName}")
                contact.phoneNumbers.forEach { phone ->
                    appendLine("TEL;TYPE=CELL:${phone.number}")
                }
                appendLine("END:VCARD")
            }
        }
    }

    suspend fun importContactsFromVcf(vcfContent: String): Int = withContext(Dispatchers.IO) {
        if (!PermissionHelper.has(context, Manifest.permission.WRITE_CONTACTS)) return@withContext 0
        var imported = 0
        val blocks = vcfContent.split("BEGIN:VCARD")
        for (block in blocks) {
            if (!block.contains("END:VCARD")) continue
            var name = ""
            val phones = mutableListOf<String>()
            block.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.startsWith("FN:", ignoreCase = true)) {
                    name = trimmed.substringAfter(":").trim()
                } else if (trimmed.startsWith("TEL", ignoreCase = true) && trimmed.contains(":")) {
                    val num = trimmed.substringAfter(":").trim()
                    if (num.isNotBlank()) phones += num
                }
            }
            if (name.isNotBlank() && phones.isNotEmpty()) {
                if (createContact(name, phones.first())) {
                    imported++
                }
            }
        }
        imported
    }

    suspend fun setContactHidden(lookupKey: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        val current = extensionDao.getByLookupKey(lookupKey)
        extensionDao.upsert(
            ContactExtensionEntity(
                contactLookupKey = lookupKey,
                customVibrationPatternJson = current?.customVibrationPatternJson,
                isHiddenFromMainList = hidden,
                localSpamFlag = current?.localSpamFlag ?: SpamFlag.NONE.name,
                spamNote = current?.spamNote,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setCustomVibration(lookupKey: String, patternName: String?) = withContext(Dispatchers.IO) {
        val current = extensionDao.getByLookupKey(lookupKey)
        extensionDao.upsert(
            ContactExtensionEntity(
                contactLookupKey = lookupKey,
                customVibrationPatternJson = patternName,
                isHiddenFromMainList = current?.isHiddenFromMainList ?: false,
                localSpamFlag = current?.localSpamFlag ?: SpamFlag.NONE.name,
                spamNote = current?.spamNote,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun setSpamFlag(lookupKey: String, flag: SpamFlag) = withContext(Dispatchers.IO) {
        val current = extensionDao.getByLookupKey(lookupKey)
        extensionDao.upsert(
            ContactExtensionEntity(
                contactLookupKey = lookupKey,
                customVibrationPatternJson = current?.customVibrationPatternJson,
                isHiddenFromMainList = current?.isHiddenFromMainList ?: false,
                localSpamFlag = flag.name,
                spamNote = current?.spamNote,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private data class ContactMeta(
        val lookupKey: String,
        val displayName: String,
        val photoUri: String?,
        val isStarred: Boolean,
        val ringtoneUri: String?
    )
}
