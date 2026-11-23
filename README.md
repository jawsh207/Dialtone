# Dialtone

An Android phone app built with Jetpack Compose and **Material 3 Expressive**, styled after the
modern Pixel dialer: dynamic color, shape-morphing buttons, expressive avatar shapes, grouped
"segmented" lists, wavy progress, and expressive motion throughout.

## What's in it

New here? [How Dialtone works](docs/HOW-IT-WORKS.md) is a plain-language tour of what the
app does and where each part lives in the code.
Working on the code? See [CONTRIBUTING.md](CONTRIBUTING.md) for how changes are documented.

- **Home** — call log filters (All, Missed, Dialed, Contacts, Unknown), a collapsible Favorites
  row (expressive shapes; **+ Add** at the end, long-press a favorite to remove it), a Contacts
  button, and recents grouped by Today / Yesterday / Older. Back-to-back calls collapse into one
  row. Tap a call for Message / Add contact / Copy; long-press it for Send to voicemail, Block,
  Copy number, Add to contacts and Delete.
- **Keypad** — the dial pad slides up over Suggested contacts (people you call most, then
  favorites); scrolling lowers it, tapping Keypad raises it. DTMF tones, haptics, T9 matching,
  long-press **0** for `+`, **1** for voicemail, backspace to clear; Call redials when empty.
- **Contacts** (from Home) — Favorites section, A–Z list with a quick-scroll strip, call and text
  buttons, Add to / Remove from Favorites, Send to voicemail.
- **Voicemail** — built-in visual voicemail (see below): inbox, carrier transcripts (or your
  speech-to-text app's), playback with a wavy progress bar, call back, message, save, share,
  delete, and new-voicemail notifications.
- **In-call screen** — works over the lock screen: Mute, Keypad (DTMF), Audio (earpiece, speaker,
  wired headset or a Bluetooth device), Hold, Add call. Call waiting offers Hold & answer, End &
  answer or Decline; a held call shows with Swap, and Merge when the network allows a conference.
  Reply with message declines with a text. SIM picker for dual-SIM phones with no default.
- **Notifications** — full-screen incoming calls (Answer / Decline), an ongoing call with Hang up,
  missed calls with Call back / Message, and new voicemail.
- **Settings** — visual voicemail per SIM (with mailbox space and Change voicemail PIN), transcription, Send to voicemail, blocked numbers,
  calling accounts, quick responses, and the voicemail log.

## Build & install

Every push to `main` produces an optimized release APK (R8-shrunk, non-debuggable) signed with
Dialtone's release key; download it from the run's **Artifacts** under **Actions → Build**.
Each build has a higher version code, so it installs as an update over the last one.

**Releases:** open **Actions → Build release → Run workflow**, pick the version bump (minor by
default, or patch/major, or type an exact version) and optionally a one-line summary. It builds
the signed APK, tags the commit `vX.Y.Z`, and publishes a GitHub Release with the APK and a change
log of every commit since the previous release, plus a full-changelog link. For hand-written
notes, push `release-notes/vX.Y.Z.md` to `main` instead (or before running it); creating a
release on GitHub with a new `vX.Y.Z` tag works too. Version codes are minutes since Nov 2023,
so every build, test or release, installs as an update over the previous one.

1. Install the APK on a **real phone with a SIM** (Android 10+). Emulators can't place real calls.
2. Open Dialtone, tap **Set as default** and pick it. Grant the permissions it asks for.

To go back, change *Settings → Apps → Default apps → Phone app*.

### Signing

CI needs four repository secrets (*Settings → Secrets and variables → Actions*). Without them
the code still compiles but no APK is published.

| Secret | Value |
| --- | --- |
| `DIALTONE_KEYSTORE_BASE64` | the keystore file, base64-encoded on one line |
| `DIALTONE_KEYSTORE_PASSWORD` | keystore password |
| `DIALTONE_KEY_ALIAS` | `dialtone` |
| `DIALTONE_KEY_PASSWORD` | key password (same as the keystore password for PKCS12) |

Keep the keystore and its password backed up privately: Android only accepts updates signed
with the same key, and anyone holding it can sign APKs that install over Dialtone. To make a
new one: `keytool -genkeypair -keystore dialtone.jks -storetype PKCS12 -alias dialtone
-keyalg RSA -keysize 4096 -validity 10000`, then `base64 -w0 dialtone.jks`.

### Building locally

Open the folder in a recent Android Studio that supports AGP 9.1, or run `./gradlew assembleDebug`
for a local debug build. Release builds use R8 with the rules in `app/proguard-rules.pro`.

### Versions

`gradle/libs.versions.toml` pins AGP 9.1.1 (Gradle 9.3.1, built-in Kotlin 2.2.10), compileSdk 37,
Compose BOM 2026.09.00 and `material3 1.5.0-beta01` (the 1.5 line requires AGP 9.1+ and
compileSdk 37). The Expressive APIs used are `MaterialExpressiveTheme`,
`MotionScheme.expressive()`, `MaterialShapes` + `toShape()`, `LoadingIndicator` and
`LinearWavyProgressIndicator`.

## Visual voicemail

Dialtone has its own visual voicemail client, so it works with the stock dialer disabled.
It's the engine from the AOSP Dialer, as shipped in GrapheneOS, ported into `vvm/`:

- **Protocols:** OMTP, CVVM (T-Mobile style) and VVM3 (Verizon and its MVNOs, including
  Spectrum Mobile), including Verizon's self-provisioning for brand-new lines.
- **Carriers:** the SIM's own carrier config is used first; otherwise the AOSP carrier table
  in `res/xml/vvm_config.xml` (about two dozen carrier entries across the US, Europe and
  elsewhere).
- **How it works:** Android routes the carrier's hidden voicemail texts to Dialtone's
  `VisualVoicemailService`. The engine activates the mailbox by SMS, then logs in to the
  carrier's IMAP server over mobile data, downloads audio and transcripts into the system
  voicemail store, and pushes read/delete changes back.
- **Carrier apps win:** if your carrier's own visual voicemail app is installed, Dialtone
  steps aside for that SIM (same rule as the stock dialer), and still shows its messages.
- **No visual voicemail?** Dialtone shows the carrier's basic "new voicemail" notification
  with a tap-to-call action instead.

- **Change voicemail PIN:** Settings › Visual voicemail › Change voicemail PIN, once a SIM's
  visual voicemail is on and set up. The request goes to the carrier over mobile data and
  follows the carrier's PIN length rules. On a brand-new VVM3 mailbox the engine replaces the
  carrier's default PIN with a random one; the screen fills that in so you can pick your own.
- **Mailbox space:** the SIM's row shows e.g. "4 of 20 messages in mailbox", as the carrier
  reported at the last sync (shown only when the carrier reports it). The Voicemail tab shows it
  too, at the top right of the list.
- **Save / Share:** an open voicemail's Save copies its audio to a folder you pick (Android's
  file picker, no storage permission); Share sends it to any app. Neither changes anything on the
  carrier's server.

Left out from the stock dialer: Google's cloud transcription (needs Google's private API),
automatic archiving (it deletes the oldest messages from the carrier's server once the mailbox is
75% full, leaving them only on the phone), and changing your greeting (the engine never
implemented it; call voicemail to record one).

### Send to voicemail

Numbers on the **Send to voicemail** list skip ringing and go straight to voicemail. Add them
from a contact (Contacts list › expand › Send to voicemail), by long-pressing a call in the call
log, or in Voicemail settings (type a number or pick a contact), where the list is managed too.
Dialtone, as the default phone app, screens each incoming call (`CallScreeningService`) and
rejects listed numbers as if Decline were tapped, so the carrier forwards them to voicemail. The
call still shows in the call log, without a missed-call notification.

### Voicemail transcription

Voicemail settings has a **Transcribe voicemail** switch (off by default). When on, voicemails
that arrive without a carrier transcript are sent to the phone's speech recognizer (the app
chosen as the system's voice input) and the text it returns is saved with the message.
Dialtone bundles no speech engine.

- Carrier transcripts always win: they're never replaced, with the switch on or off.
- Works with recognizers that accept recordings (`RecognizerIntent.EXTRA_AUDIO_SOURCE`,
  Android 13+). [Distillery](https://github.com/jawsh207/Distillery) does, on the phone. The
  audio is passed as 16 kHz mono 16-bit PCM, the format the API documents.
- Dialtone asks for the whole recording to be transcribed (a segmented session that ends with
  the audio, plus minimum-length and long-silence hints for engines that ignore that), in the
  phone's language (retrying in the engine's default if it doesn't offer that language).
- Dialtone deliberately has no microphone permission. Engines must handle the caller's
  permission themselves (Distillery does); others are refused. The upside: an engine that
  ignores the recording and opens the microphone can't record the room into a transcript.
- A recognizer that only listens to the microphone returns an error, and the message is left
  without a transcript. An online recognizer may send the audio off the phone; the settings
  card says which recognizer is in use.
- Runs as a background job when a new voicemail's audio arrives. Temporary failures (model not
  downloaded yet, recognizer busy, refused while Dialtone is in the background) are retried on
  the next voicemail or when Dialtone is opened.

### If voicemail doesn't show up

Open **Voicemail → ⚙ → Diagnostics**. The log shows each step (carrier config found,
activation SMS, IMAP login, sync). Passwords are stripped and it never leaves the phone unless
you copy it. **Sync now** retries activation or fetches immediately.

Mobile data must be on: most carriers only allow voicemail downloads over their own network.

Android doesn't let regular apps record call audio, so a self-hosted answering machine isn't
possible without carrier or system privileges.

## Layout

```
app/src/main/java/app/dialtone/phone/
  MainActivity.kt              default-dialer onboarding, DIAL intents, permissions
  telecom/                     InCallService, call state, call and missed-call notifications
  screening/                   Send to voicemail (call screening) and the system block list
  transcription/               voicemail transcription through the speech-to-text app
  incall/                      in-call activity and screen
  data/                        contacts, call log, voicemail repositories
  ui/                          app shell, tabs, keypad, contacts, voicemail, settings, theme
app/src/test/java/…            unit tests (number matching, duplicates, call log grouping, filters, PIN rules, file names)
  voicemail/                   voicemail notifications, Save/Share, PIN rules
  vvm/                         visual voicemail engine (Java, ported from AOSP Dialer)
    DialtoneVoicemail.java     the app's entry points into the engine
    compat/                    small stand-ins for Dialer-internal helpers
app/src/main/res/xml/vvm_config.xml   per-carrier visual voicemail settings (AOSP)
```

## Look

Dialtone uses the same theme as the Contacts app: wallpaper (dynamic) colour on Android 12+,
Google Sans Flex for all text, `surfaceContainerLow` pages, `surfaceBright` cards, a
`surfaceContainerHighest` search bar and Material 3's 64dp `ShortNavigationBar`.

## App icon

The handset is the GrapheneOS Dialer launcher icon (Apache-2.0), recolored white on green.

## License

Dialtone is licensed under the [Apache License 2.0](LICENSE). Copyright 2026 jawsh207.

Code in `app/src/main/java/app/dialtone/phone/vvm/` and `res/xml/vvm_config.xml` comes from the
Android Open Source Project Dialer (via GrapheneOS) under the Apache License 2.0; original
copyright headers are kept and modified files are marked. See `NOTICE`.
