/*
 * Copyright (C) 2017 The Android Open Source Project
 * Modifications Copyright (C) 2026 Dialtone
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */
package app.dialtone.phone.vvm;

import android.telecom.PhoneAccountHandle;

/** Constants shared across the voicemail engine (trimmed from Dialer's VoicemailClient). */
public interface VoicemailClient {

  /** Broadcast to request an upload of local changes (read/deleted) to the server. */
  String ACTION_UPLOAD = "app.dialtone.phone.vvm.VoicemailClient.ACTION_UPLOAD";

  String PARAM_PHONE_ACCOUNT_HANDLE = "phone_account_handle";

  /**
   * Broadcast from the engine to show a "new voicemail" notification when visual voicemail is
   * not available (legacy mode / message-waiting indicator only).
   */
  String ACTION_SHOW_LEGACY_VOICEMAIL =
      "app.dialtone.phone.vvm.VoicemailClient.ACTION_SHOW_LEGACY_VOICEMAIL";

  /** Boolean extra sent with {@link #ACTION_SHOW_LEGACY_VOICEMAIL}. */
  String EXTRA_IS_LEGACY_MODE = "is_legacy_mode";

  /** Listener for changes in the activation state of an account. */
  interface ActivationStateListener {
    void onActivationStateChanged(PhoneAccountHandle phoneAccountHandle, boolean isActivated);
  }
}
