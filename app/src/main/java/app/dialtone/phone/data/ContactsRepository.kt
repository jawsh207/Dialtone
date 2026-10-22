package app.dialtone.phone.data

import android.Manifest
import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone
import app.dialtone.phone.util.hasPermission
import app.dialtone.phone.util.numberKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContactsRepository(private val context: Context) {

    suspend fun load(): List<Contact> = withContext(Dispatchers.IO) {
        if (!context.hasPermission(Manifest.permission.READ_CONTACTS)) return@withContext emptyList()
        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.LOOKUP_KEY,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.STARRED,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
        )
        val byId = LinkedHashMap<Long, Contact>()
        runCatching {
            context.contentResolver.query(
                Phone.CONTENT_URI, projection, null, null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val number = c.getString(5) ?: continue
                    val label = Phone.getTypeLabel(context.resources, c.getInt(6), c.getString(7)).toString()
                    val existing = byId[id]
                    if (existing == null) {
                        byId[id] = Contact(
                            id = id,
                            lookupKey = c.getString(1).orEmpty(),
                            name = c.getString(2) ?: number,
                            photoUri = c.getString(3),
                            starred = c.getInt(4) == 1,
                            numbers = listOf(PhoneNumber(number, label)),
                        )
                    } else if (existing.numbers.none { numberKey(it.number) == numberKey(number) }) {
                        byId[id] = existing.copy(numbers = existing.numbers + PhoneNumber(number, label))
                    }
                }
            }
        }
        byId.values.toList()
    }
}
