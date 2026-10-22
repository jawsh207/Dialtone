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

import android.content.Context;
import android.telecom.PhoneAccountHandle;

/**
 * Replaces Dialer's Dagger component. Exposes the few client capabilities the engine queries:
 * the module is always on, and archive/transcription (Google cloud features) are off.
 */
public final class VoicemailComponent {

  private static final VoicemailComponent INSTANCE = new VoicemailComponent();
  private final Client client = new Client();

  private VoicemailComponent() {}

  public static VoicemailComponent get(Context context) {
    return INSTANCE;
  }

  public Client getVoicemailClient() {
    return client;
  }

  /** The subset of Dialer's VoicemailClient the engine calls. */
  public static final class Client {
    public boolean isVoicemailModuleEnabled() {
      return true;
    }

    public boolean isVoicemailArchiveAvailable(Context context) {
      return false;
    }

    public boolean isVoicemailTranscriptionAvailable(
        Context context, PhoneAccountHandle phoneAccountHandle) {
      return false;
    }

    public PinChanger createPinChanger(Context context, PhoneAccountHandle phoneAccountHandle) {
      return new PinChangerImpl(context, phoneAccountHandle);
    }
  }
}
