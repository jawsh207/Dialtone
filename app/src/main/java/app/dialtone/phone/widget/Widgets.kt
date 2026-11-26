package app.dialtone.phone.widget

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.VoicemailContract
import android.util.Log
import androidx.glance.appwidget.updateAll
import app.dialtone.phone.shortcuts.AppShortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Keeps the home screen widgets and the favorites app shortcuts up to date.
 *
 * Widgets don't refresh themselves, and the launcher only asks every half hour at best. So a
 * JobScheduler job watches contacts (favorites, names, photos), the call log (which favorites are
 * called most) and the voicemail store, and redraws everything shortly after any of them changes,
 * whether Dialtone is open or not. Content-watching jobs fire once and don't survive a reboot, so
 * the job re-arms itself after each run and the app re-arms it whenever its process starts.
 *
 * Glance keeps a widget's composition alive for a while after drawing it and, when asked to
 * update, only recomposes it. So the widgets read their data in composition, keyed on [tick],
 * and [refreshAll] bumps the tick before asking for the update.
 */
object Widgets {
    private const val TAG = "DialtoneWidgets"

    // Distinct from the voicemail engine's (50–52) and transcription's (60–61) job ids.
    private const val JOB_WATCH = 70

    private val _tick = MutableStateFlow(0)
    val tick: StateFlow<Int> = _tick

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Redraws every widget and republishes the favorites shortcuts. */
    suspend fun refreshAll(context: Context) {
        val app = context.applicationContext
        _tick.value++
        listOf(FavoritesWidget(), VoicemailWidget(), SpeedDialWidget()).forEach { widget ->
            runCatching { widget.updateAll(app) }.onFailure { Log.w(TAG, "widget update failed", it) }
        }
        runCatching { AppShortcuts.update(app) }.onFailure { Log.w(TAG, "shortcut update failed", it) }
    }

    /** [refreshAll] without waiting, for callers that aren't coroutines. */
    fun refreshAllAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { refreshAll(app) }
    }

    /** (Re)arms the watcher job. Cheap; call at app start and after every run. */
    fun schedule(context: Context) {
        val js = context.getSystemService(JobScheduler::class.java) ?: return
        val flags = JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS
        runCatching {
            js.schedule(
                JobInfo.Builder(JOB_WATCH, ComponentName(context, WidgetRefreshJobService::class.java))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(ContactsContract.Contacts.CONTENT_URI, flags))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(CallLog.Calls.CONTENT_URI, flags))
                    .addTriggerContentUri(JobInfo.TriggerContentUri(VoicemailContract.Voicemails.CONTENT_URI, flags))
                    // A contacts sync or a new voicemail changes many rows at once: wait for quiet.
                    .setTriggerContentUpdateDelay(2_000)
                    .setTriggerContentMaxDelay(15_000)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "couldn't schedule widget refresh", it) }
    }
}

/** Runs [Widgets.refreshAll] when contacts, calls or voicemail change, then re-arms itself. */
class WidgetRefreshJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            try {
                Widgets.refreshAll(applicationContext)
            } finally {
                jobFinished(params, false)
                Widgets.schedule(applicationContext)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // The next change re-triggers it; make sure the watcher is still armed.
        Widgets.schedule(applicationContext)
        return false
    }
}
