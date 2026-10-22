/*
 * Copyright (C) 2015 The Android Open Source Project
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
 *
 * Modified for Dialtone: Dialer executors replaced, archive/transcription settings removed.
 */
package app.dialtone.vvm.settings;

import android.content.Context;
import android.provider.VoicemailContract.Voicemails;
import android.telecom.PhoneAccountHandle;
import androidx.annotation.VisibleForTesting;
import app.dialtone.vvm.OmtpVvmCarrierConfigHelper;
import app.dialtone.vvm.VisualVoicemailPreferences;
import app.dialtone.vvm.VvmLog;
import app.dialtone.vvm.compat.Assert;
import app.dialtone.vvm.sync.VvmAccountManager;

/** Save whether or not a particular account is enabled in shared to be retrieved later. */
public class VisualVoicemailSettingsUtil {

  @VisibleForTesting public static final String IS_ENABLED_KEY = "is_enabled";
  private static final String ARCHIVE_ENABLED_KEY = "archive_is_enabled";
  private static final String TRANSCRIBE_VOICEMAILS_KEY = "transcribe_voicemails";
  private static final String DONATE_VOICEMAILS_KEY = "donate_voicemails";

  public static void setEnabled(
      Context context, PhoneAccountHandle phoneAccount, boolean isEnabled) {
    VvmLog.i("VisualVoicemailSettingsUtil.setEnable", phoneAccount + " enabled:" + isEnabled);
    new VisualVoicemailPreferences(context, phoneAccount)
        .edit()
        .putBoolean(IS_ENABLED_KEY, isEnabled)
        .apply();
    OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, phoneAccount);
    if (isEnabled) {
      config.startActivation();
    } else {
      VvmAccountManager.removeAccount(context, phoneAccount);
      config.startDeactivation();
      // Remove voicemails this app downloaded.
      final Context appContext = context.getApplicationContext();
      new Thread(
              () -> {
                try {
                  int deleted =
                      appContext
                          .getContentResolver()
                          .delete(Voicemails.buildSourceUri(appContext.getPackageName()), null, null);
                  VvmLog.i("VisualVoicemailSettingsUtil", "deleted " + deleted + " voicemails");
                } catch (RuntimeException e) {
                  VvmLog.e("VisualVoicemailSettingsUtil", "delete voicemails", e);
                }
              },
              "vvm-delete")
          .start();
    }
  }

  public static boolean isEnabled(Context context, PhoneAccountHandle phoneAccount) {
    if (phoneAccount == null) {
      VvmLog.i("VisualVoicemailSettingsUtil.isEnabled", "phone account is null");
      return false;
    }

    VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context, phoneAccount);
    if (prefs.contains(IS_ENABLED_KEY)) {
      // isEnableByDefault is a bit expensive, so don't use it as default value of
      // getBoolean(). The "false" here should never be actually used.
      return prefs.getBoolean(IS_ENABLED_KEY, false);
    }
    return new OmtpVvmCarrierConfigHelper(context, phoneAccount).isEnabledByDefault();
  }

  /** Archiving to local storage is a Dialer-only feature; always off in Dialtone. */
  public static boolean isArchiveEnabled(Context context, PhoneAccountHandle phoneAccount) {
    Assert.isNotNull(phoneAccount);
    return new VisualVoicemailPreferences(context, phoneAccount).getBoolean(ARCHIVE_ENABLED_KEY, false);
  }

  /** Google cloud transcription is not available; carrier transcripts still sync. */
  public static boolean isVoicemailTranscriptionEnabled(
      Context context, PhoneAccountHandle phoneAccount) {
    Assert.isNotNull(phoneAccount);
    return new VisualVoicemailPreferences(context, phoneAccount)
        .getBoolean(TRANSCRIBE_VOICEMAILS_KEY, false);
  }

  public static boolean isVoicemailDonationEnabled(
      Context context, PhoneAccountHandle phoneAccount) {
    Assert.isNotNull(phoneAccount);
    return new VisualVoicemailPreferences(context, phoneAccount)
        .getBoolean(DONATE_VOICEMAILS_KEY, false);
  }

  /**
   * Whether the client enabled status is explicitly set by user or by default(Whether carrier VVM
   * app is installed). This is used to determine whether to disable the client when the carrier VVM
   * app is installed. If the carrier VVM app is installed the client should give priority to it if
   * the settings are not touched.
   */
  public static boolean isEnabledUserSet(Context context, PhoneAccountHandle phoneAccount) {
    if (phoneAccount == null) {
      return false;
    }
    VisualVoicemailPreferences prefs = new VisualVoicemailPreferences(context, phoneAccount);
    return prefs.contains(IS_ENABLED_KEY);
  }
}
