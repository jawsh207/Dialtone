package app.dialtone.phone.ui

import android.app.Application
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.VoicemailContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dialtone.phone.data.CallLogRepository
import app.dialtone.phone.data.Contact
import app.dialtone.phone.data.ContactsRepository
import app.dialtone.phone.data.RecentCall
import app.dialtone.phone.data.Voicemail
import app.dialtone.phone.data.VoicemailRepository
import app.dialtone.phone.data.VvmAccount
import app.dialtone.phone.data.VvmHealth
import app.dialtone.phone.util.contentChanges
import app.dialtone.phone.util.numberKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DialerViewModel(app: Application) : AndroidViewModel(app) {

    private val contactsRepo = ContactsRepository(app)
    private val callLogRepo = CallLogRepository(app)
    private val voicemailRepo = VoicemailRepository(app)

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    /** null while the first load is in flight. */
    private val _recents = MutableStateFlow<List<RecentCall>?>(null)
    val recents: StateFlow<List<RecentCall>?> = _recents.asStateFlow()

    private val _voicemails = MutableStateFlow<List<Voicemail>?>(null)
    val voicemails: StateFlow<List<Voicemail>?> = _voicemails.asStateFlow()

    private val _vvmAccounts = MutableStateFlow<List<VvmAccount>>(emptyList())
    val vvmAccounts: StateFlow<List<VvmAccount>> = _vvmAccounts.asStateFlow()

    private val _vvmHealth = MutableStateFlow(VvmHealth.Unknown)
    val vvmHealth: StateFlow<VvmHealth> = _vvmHealth.asStateFlow()

    /** numberKey → contact, to put names on call log and voicemail rows. */
    val contactIndex: StateFlow<Map<String, Contact>> = _contacts
        .map { list ->
            buildMap {
                list.forEach { c ->
                    c.numbers.forEach { n ->
                        val key = numberKey(n.number)
                        if (key.isNotEmpty() && key !in this) put(key, c)
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        observe(ContactsContract.Contacts.CONTENT_URI) { _contacts.value = contactsRepo.load() }
        observe(CallLog.Calls.CONTENT_URI) { _recents.value = callLogRepo.load() }
        observe(VoicemailContract.Voicemails.CONTENT_URI) { _voicemails.value = voicemailRepo.load() }
        observe(VoicemailContract.Status.CONTENT_URI) { loadVvmState() }
    }

    private fun observe(uri: Uri, load: suspend () -> Unit) {
        viewModelScope.launch {
            contentChanges(getApplication(), uri).collectLatest { load() }
        }
    }

    /** Called on resume — picks up permission grants that observers missed. */
    fun refresh() {
        viewModelScope.launch {
            _contacts.value = contactsRepo.load()
            _recents.value = callLogRepo.load()
            _voicemails.value = voicemailRepo.load()
            loadVvmState()
        }
    }

    private suspend fun loadVvmState() {
        _vvmAccounts.value = voicemailRepo.accounts()
        _vvmHealth.value = voicemailRepo.health()
    }

    fun setVisualVoicemailEnabled(account: VvmAccount, enabled: Boolean) {
        viewModelScope.launch {
            voicemailRepo.setEnabled(account.id, enabled)
            loadVvmState()
        }
    }

    fun syncVoicemail() = voicemailRepo.syncNow()

    fun requestVoicemailAudio(vm: Voicemail) = voicemailRepo.requestContent(vm)

    fun voicemailLog(): String = voicemailRepo.readLog()

    fun clearVoicemailLog() = voicemailRepo.clearLog()

    fun markRead(vm: Voicemail) {
        if (vm.isRead) return
        viewModelScope.launch { voicemailRepo.markRead(vm.id) }
    }

    fun delete(vm: Voicemail) {
        _voicemails.update { list -> list?.filterNot { it.id == vm.id } }
        viewModelScope.launch { voicemailRepo.delete(vm.id) }
    }
}
