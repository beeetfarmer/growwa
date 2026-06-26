package com.nl.growwa.data.contacts

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.nl.growwa.core.model.ContactInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Searches the device contacts by name (requires READ_CONTACTS). */
class ContactsRepository(context: Context) {
    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun search(query: String, limit: Int = 8): List<ContactInfo> {
        if (query.isBlank() || !hasPermission()) return emptyList()
        return withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(
                ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query.trim())
            )
            val projection = arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
            )
            val out = mutableListOf<ContactInfo>()
            runCatching {
                appContext.contentResolver.query(
                    uri, projection, null, null,
                    "${ContactsContract.Contacts.DISPLAY_NAME} ASC",
                )?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                    val keyIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
                    val nameIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIdx = c.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
                    val seen = HashSet<Long>()
                    while (c.moveToNext() && out.size < limit) {
                        val id = c.getLong(idIdx)
                        if (!seen.add(id)) continue
                        val name = c.getString(nameIdx) ?: continue
                        out += ContactInfo(
                            id = id,
                            lookupKey = c.getString(keyIdx).orEmpty(),
                            name = name,
                            photoUri = c.getString(photoIdx),
                        )
                    }
                }
            }
            out
        }
    }
}
