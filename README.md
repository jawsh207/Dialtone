# Dialtone

An Android phone app built with Jetpack Compose and **Material 3 Expressive**, styled after the
modern Pixel dialer: dynamic color, shape-morphing buttons, expressive avatar shapes, grouped
"segmented" lists, wavy progress, and expressive motion throughout.

## What's in it

- **Home** — favorites row (contacts you've starred, in expressive shapes) and recents grouped
  by Today / Yesterday / Older. Back-to-back calls collapse into one row with a count. Tap a row
  for Message / Add contact / Copy.
- **Keypad** — pill keys that morph on press, DTMF feedback tones (respects the system setting),
  haptics, T9 contact suggestions, long-press **0** for `+`, long-press **1** for voicemail,
  long-press backspace to clear, call button redials your last number when empty.
- **Contacts** — alphabetical, expandable to every number, plus search from the top bar.
- **Voicemail** — built-in visual voicemail (see below): inbox with unread badge, carrier
  transcripts, playback with a wavy progress bar (tap to seek), call back, message, delete,
  new-voicemail notifications, and a settings sheet with per-SIM on/off, Sync now and an
  on-device diagnostics log.
- **In-call screen** — works over the lock screen; rotating cookie halo while connecting,
  morphing toggles for Mute / Keypad (DTMF) / Speaker / Hold, big end-call button, proximity
  screen-off, and a SIM picker for dual-SIM phones with no default.
- **Incoming calls** — full-screen `CallStyle` notification with Answer / Decline; an ongoing
  call notification with Hang up while you're elsewhere.

## Build & install

Every push to `main` builds on GitHub Actions (**Actions → Build**). Download the APK from the
run's **Artifacts**, or push a tag like `v0.2.0` to publish a GitHub Release with the APK attached.

1. Install the APK on a **real phone with a SIM** (Android 10+). Emulators can't place real calls.
2. Open Dialtone, tap **Set as default** and pick it. Grant the permissions it asks for.

To go back, change *Settings → Apps → Default apps → Phone app*.

### Signing (so updates install over each other)

Without signing secrets, CI ships a **debug** APK signed with a throwaway key that changes from
run to run, so each new build must be uninstalled before installing. To get a stable, signed
release APK, create a keystore once and add four repository secrets
(*Settings → Secrets and variables → Actions*):

```sh
keytool -genkeypair -v -keystore dialtone.jks -alias dialtone \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 dialtone.jks   # → DIALTONE_KEYSTORE_BASE64
```

| Secret | Value |
| --- | --- |
| `DIALTONE_KEYSTORE_BASE64` | the base64 output above |
| `DIALTONE_KEYSTORE_PASSWORD` | keystore password |
| `DIALTONE_KEY_ALIAS` | `dialtone` |
| `DIALTONE_KEY_PASSWORD` | key password |

Keep `dialtone.jks` and its passwords backed up: Android only accepts updates signed with the
same key.

### Building locally

Open the folder in a recent Android Studio that supports AGP 9.1, or run `./gradlew assembleDebug`.

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

Left out from the stock dialer: Google's cloud transcription (needs Google's private API;
carrier-provided transcripts still work), voicemail archiving, and the PIN-change and greeting
screens.

### If voicemail doesn't show up

Open **Voicemail → ⚙ → Diagnostics**. The log shows each step (carrier config found,
activation SMS, IMAP login, sync). Passwords are stripped and it never leaves the phone unless
you copy it. **Sync now** retries activation or fetches immediately.

Mobile data must be on: most carriers only allow voicemail downloads over their own network.

Android doesn't let regular apps record call audio, so a self-hosted answering machine isn't
possible without carrier or system privileges.

## Layout

```
app/src/main/java/app/dialtone/
  MainActivity.kt              default-dialer onboarding, DIAL intents, permissions
  telecom/                     InCallService, call state, notifications, helpers
  incall/                      in-call activity and screen
  data/                        contacts, call log, voicemail repositories
  ui/                          app shell, tabs, keypad, voicemail, theme, shared components
  voicemail/                   voicemail notifications (new message, message-waiting, boot)
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

## License notes

Code in `app/src/main/java/app/dialtone/vvm/` and `res/xml/vvm_config.xml` comes from the
Android Open Source Project Dialer (via GrapheneOS) under the Apache License 2.0; original
copyright headers are kept and modified files are marked. See `NOTICE`.
