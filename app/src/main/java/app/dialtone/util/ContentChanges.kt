package app.dialtone.util

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Emits once immediately, then whenever [uri] changes. If registering fails
 * (no permission yet), it just emits the initial value.
 */
fun contentChanges(context: Context, uri: Uri): Flow<Unit> = callbackFlow {
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(Unit)
        }
    }
    val registered = runCatching {
        context.contentResolver.registerContentObserver(uri, true, observer)
    }.isSuccess
    trySend(Unit)
    awaitClose {
        if (registered) context.contentResolver.unregisterContentObserver(observer)
    }
}.conflate()
