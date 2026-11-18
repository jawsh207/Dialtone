# How Dialtone works

A plain-language tour of what Dialtone does and where in the code each part lives. No
programming knowledge needed; file names are given so you can look closer if you want to.
All paths are under `app/src/main/java/app/dialtone/phone/`.

## The big picture

Dialtone is a **phone app** (a "dialer"): it places and answers calls, shows your call
history and contacts, and plays your voicemail. Android only lets one app do this at a time,
the **default phone app**, so the first thing Dialtone does is ask to become it
(`ui/onboarding/SetDefaultScreen.kt`). Until you say yes, nothing else works.

Dialtone doesn't make calls itself. Android has a built-in calling service ("Telecom") that
talks to the mobile network. Dialtone is the face of it: Telecom tells Dialtone when a call
starts, rings or ends, and Dialtone shows the screens and buttons and tells Telecom what you
tapped.

Most of the time Dialtone isn't even open. Android starts it in the background when
something happens (a call comes in, the carrier sends a voicemail alert, a scheduled job is
due), and `DialtoneApplication.kt` gets everything ready for that before anything else runs.

## The screens

| What you see | Where it lives |
|---|---|
| The whole app: search bar, the three tabs, the bottom bar | `ui/DialerApp.kt` |
| **Home**: call log filters, Favorites, Contacts button, recent calls | `ui/home/HomeScreen.kt` |
| **Keypad**: the dial pad and Suggested contacts | `ui/keypad/KeypadScreen.kt`, `ui/components/DialPad.kt` |
| **Voicemail**: your messages, playback, transcripts | `ui/voicemail/VoicemailScreen.kt`, `ui/voicemail/VoicemailPlayer.kt` |
| **Contacts** list (from Home), with Favorites and the A–Z strip | `ui/contacts/ContactsScreen.kt` |
| **Settings** | `ui/settings/SettingsScreen.kt` |
| The screen during a call | `incall/InCallScreen.kt` |
| Colours, font and shapes | `ui/theme/Theme.kt` |

The screens get their data (calls, contacts, voicemails) from `ui/DialerViewModel.kt`, which
in turn reads it from Android through the files in `data/`. When something changes in
Android (a new call in the log, a contact edited elsewhere), Dialtone notices and refreshes
by itself.

## Making and receiving calls

- **Placing a call:** tapping Call hands the number to Android's calling service
  (`telecom/Dialer.kt`).
- **During a call:** Android connects to Dialtone's in-call service
  (`telecom/DialtoneInCallService.kt`) and passes it every call. `telecom/CallManager.kt` keeps
  track of them (ringing, active, on hold) for the in-call screen (`incall/InCallScreen.kt`),
  which offers mute, keypad, audio, hold, add call and hang up.
- **Audio:** with only the phone's earpiece and speaker, the button is a simple Speaker switch.
  With a wired headset or Bluetooth device connected it becomes **Audio**, listing every place
  the call can play, so switching the speaker off never drops your headset.
- **Two calls at once:** if someone calls while you're on a call, you can **Hold & answer**,
  **End & answer** or **Decline**. A call on hold shows in a card with **Swap**, and **Merge**
  when the network allows joining them into a conference call. **Add call** opens the keypad to
  call someone else; Android puts the first call on hold for you.
- **Incoming calls** ring through a full-screen notification that shows over the lock screen
  (`telecom/CallNotifications.kt`). Answer opens the in-call screen; Decline and Hang up are
  handled without opening the app (`telecom/CallActionReceiver.kt`).
- **Reply with message:** on an incoming call you can decline and text back one of your
  quick responses (`telecom/QuickResponses.kt`). Android sends the text through your default
  messaging app, so Dialtone needs no texting permission for it.
- **Missed calls** get Dialtone's own notification, with Call back and Message
  (`telecom/MissedCalls.kt`). It clears once you open the Home tab, dismiss it, or call back.
- **Notification icons** tell the notifications apart in the status bar: a ringing handset for
  an incoming call, a plain handset during a call, a paused handset when it's on hold, a
  handset with a missed arrow for missed calls, and the voicemail symbol for voicemail
  (`res/drawable/ic_stat_*.xml`).
- **Caller names** come from your contacts (`telecom/CallerLookup.kt`).

## The call log

The Home tab shows Android's call log (`data/CallLogRepository.kt`), with back-to-back calls
from the same person grouped into one row. Long-press a call to send that number to voicemail,
block it, copy it, add it to your contacts, or delete it from the call log (a grouped row
deletes all its calls). Blocking uses Android's own block list (`screening/Blocking.kt`), the
same one as Settings › Blocked numbers: blocked callers are rejected outright and don't reach
voicemail. Favorites are added with the **+ Add** button and removed by long-pressing them.

## Send to voicemail

You can put numbers on a **Send to voicemail** list (`screening/SendToVoicemail.kt`). Before
any call rings, Android asks the default phone app whether to let it through
(`screening/SendToVoicemailScreeningService.kt`). If the number is on the list, Dialtone
declines it, exactly as if you had tapped Decline, and your carrier then sends the caller to
voicemail. The call still shows in your call log, but your phone doesn't ring.

## Visual voicemail

"Visual voicemail" means your voicemails appear in the app as a list you can play in any
order, instead of you calling in to listen.

How it works with your carrier:

1. Your carrier runs a mail server that holds your voicemails as audio attachments.
2. When a new voicemail arrives, the carrier sends your phone a hidden text message.
3. Android passes that text to the default phone app, and Dialtone's **voicemail engine**
   connects to the carrier's server over mobile data, downloads the new message, and saves it
   into Android's voicemail store.
4. The Voicemail tab reads from that store, and a notification tells you a message arrived
   (`voicemail/VoicemailNotifier.kt`).

The voicemail engine lives in the `vvm/` folder. It's Google's own, from the Android Open
Source Project (via GrapheneOS), adapted to run inside Dialtone. It knows the
different systems carriers use (called OMTP, CVVM and VVM3; Spectrum and Verizon use VVM3).
It's written in Java, while the rest of Dialtone is Kotlin, and it's kept close to Google's
original so fixes can be copied across. When you read, delete or mark a voicemail in
Dialtone, the engine passes that back to the carrier on its next sync.

If another phone app (like the stock dialer) also downloaded your voicemails, the same
message can be stored twice. Dialtone matches the copies and shows each message once
(`data/VoicemailDuplicates.kt`).

## Voicemail transcription

If your carrier sends a written transcript with a voicemail, Dialtone shows it, and never
replaces it.

If the carrier doesn't, and you've turned on **Transcribe voicemail**, Dialtone asks your
phone's speech-to-text app (for example Distillery) to transcribe the recording
(`transcription/`):

- `AudioDecoder.kt` converts the voicemail audio into the format Android's speech system
  expects.
- `SystemRecognizer.kt` hands it to whichever speech-to-text app you've chosen as your voice
  input, and collects the text it sends back.
- `VoicemailTranscriber.kt` finds voicemails that need a transcript and saves the results.
- `TranscriptionJobService.kt` runs all this in the background whenever a new voicemail
  arrives. It runs separately from the rest of Dialtone, so a slow or stuck speech app can
  never hold up calls, voicemail downloads or notifications.

Whether it works depends on your speech-to-text app: not every app can transcribe a
recording, and some send audio to an online service.

## Settings

Settings (`ui/settings/SettingsScreen.kt`) gathers:

- **Visual voicemail:** turn it on or off for each SIM, and check for new messages.
- **Transcription:** the Transcribe voicemail switch, and a link to choose your voice input.
- **Send to voicemail:** the list, and ways to add to it.
- **Calls:** links to Android's blocked numbers and calling accounts, and your quick responses.
- **Troubleshooting:** the voicemail log, which records what the voicemail engine,
  transcription and call screening did. It stays on your phone, has passwords removed, and is
  the first thing to look at if voicemail stops working.

## Privacy

Dialtone has no servers, accounts, ads or analytics. It only talks to:

- your **carrier**, to place calls and download your voicemail;
- the **apps you choose**: your speech-to-text app for transcripts, your messaging app for
  quick responses.

It deliberately has no microphone permission of its own, so it can't record anything
(Android's calling service handles the microphone during calls).

## Building and releasing

GitHub builds Dialtone automatically. Every change first runs the automated tests
(`app/src/test/`), which check things like matching one phone number written different ways,
spotting duplicate voicemails, grouping the call log and the call log filters. If a test fails,
no app is built. Then it produces a signed test build. A release is made by adding release
notes or using the **Build release** button under Actions. See the README for details.
