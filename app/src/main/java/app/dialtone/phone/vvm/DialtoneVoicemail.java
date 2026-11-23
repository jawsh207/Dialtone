/*
 * Copyright (C) 2026 Dialtone
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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import app.dialtone.phone.vvm.compat.PersistentLogger;
import app.dialtone.phone.vvm.settings.VisualVoicemailSettingsUtil;
import app.dialtone.phone.vvm.sync.SyncTask;
import app.dialtone.phone.vvm.sync.VvmAccountManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Entry points from Dialtone's app code into the visual voicemail engine. */
public final class DialtoneVoicemail {

  private static final String TAG = "DialtoneVoicemail";

  private DialtoneVoicemail() {}

  /** Snapshot of one SIM's visual voicemail state, for the settings UI. */
  public static final class AccountState {
    public final PhoneAccountHandle handle;
    public final String label;
    /** Carrier has a visual voicemail config (built-in table or carrier config). */
    public final boolean supported;
    /** e.g. vvm_type_omtp, vvm_type_cvvm, vvm_type_vvm3; null when unsupported. */
    @Nullable public final String protocol;
    public final boolean enabled;
    public final boolean activated;
    /** The carrier's own visual voicemail app is installed and handles this SIM instead. */
    public final boolean carrierAppInstalled;

    AccountState(
        PhoneAccountHandle handle,
        String label,
        boolean supported,
        @Nullable String protocol,
        boolean enabled,
        boolean activated,
        boolean carrierAppInstalled) {
      this.handle = handle;
      this.label = label;
      this.supported = supported;
      this.protocol = protocol;
      this.enabled = enabled;
      this.activated = activated;
      this.carrierAppInstalled = carrierAppInstalled;
    }
  }

  public static void init(Context context) {
    PersistentLogger.init(context);
  }

  @MainThread
  public static void onBoot(Context context) {
    init(context);
    OmtpService.onBoot(context);
    StatusCheckJobService.schedule(context);
  }

  @MainThread
  public static void onShutdown(Context context) {
    OmtpService.onShutdown(context);
  }

  /** All SIM accounts that can place calls, with their visual voicemail state. */
  @SuppressLint("MissingPermission")
  public static List<AccountState> getAccounts(Context context) {
    TelecomManager telecom = context.getSystemService(TelecomManager.class);
    if (telecom == null) return Collections.emptyList();
    List<AccountState> out = new ArrayList<>();
    List<PhoneAccountHandle> handles;
    try {
      handles = telecom.getCallCapablePhoneAccounts();
    } catch (SecurityException e) {
      VvmLog.w(TAG, "no permission to list phone accounts");
      return Collections.emptyList();
    }
    for (PhoneAccountHandle handle : handles) {
      PhoneAccount account = telecom.getPhoneAccount(handle);
      if (account == null || !account.hasCapabilities(PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)) {
        continue;
      }
      OmtpVvmCarrierConfigHelper config = new OmtpVvmCarrierConfigHelper(context, handle);
      boolean supported = config.isValid();
      out.add(
          new AccountState(
              handle,
              account.getLabel() == null ? "SIM" : account.getLabel().toString(),
              supported,
              supported ? config.getVvmType() : null,
              supported && VisualVoicemailSettingsUtil.isEnabled(context, handle),
              VvmAccountManager.isAccountActivated(context, handle),
              supported && config.isCarrierAppInstalled()));
    }
    return out;
  }

  /** Turn visual voicemail on or off for one SIM (starts activation or deactivation). */
  public static void setEnabled(Context context, PhoneAccountHandle handle, boolean enabled) {
    VisualVoicemailSettingsUtil.setEnabled(context, handle, enabled);
  }

  /** Fetch new messages now, or retry activation if the SIM isn't activated yet. */
  public static void syncNow(Context context) {
    for (AccountState state : getAccounts(context)) {
      if (!state.supported || !state.enabled) continue;
      if (state.activated) {
        VvmLog.i(TAG, "manual sync for " + state.label);
        SyncTask.start(context, state.handle);
      } else {
        VvmLog.i(TAG, "manual activation for " + state.label);
        ActivationTask.start(context, state.handle, null);
      }
    }
  }

  /** Push local read/delete changes to the voicemail server. */
  public static void requestUpload(Context context) {
    Intent intent = new Intent(VoicemailClient.ACTION_UPLOAD);
    intent.setPackage(context.getPackageName());
    context.sendBroadcast(intent);
  }

  /** True if the account has working visual voicemail (so MWI notifications are redundant). */
  public static boolean isActivated(Context context, @Nullable PhoneAccountHandle handle) {
    return handle != null
        && VisualVoicemailSettingsUtil.isEnabled(context, handle)
        && VvmAccountManager.isAccountActivated(context, handle);
  }

  /** What the PIN screen needs to know about one SIM's voicemail PIN. */
  public static final class PinInfo {
    /** Carrier's PIN length rules; 0 when the carrier didn't send them. */
    public final int minLength;
    public final int maxLength;
    /**
     * The random PIN the engine set when it activated a brand-new VVM3 mailbox (replacing the
     * carrier's default), or null when the user's own PIN is in place. Only Dialtone knows it,
     * so the PIN screen fills it in as the current PIN.
     */
    @Nullable public final String scrambledPin;

    PinInfo(int minLength, int maxLength, @Nullable String scrambledPin) {
      this.minLength = minLength;
      this.maxLength = maxLength;
      this.scrambledPin = scrambledPin;
    }
  }

  public static PinInfo getPinInfo(Context context, PhoneAccountHandle handle) {
    PinChanger changer = VoicemailComponent.get(context).getVoicemailClient().createPinChanger(context, handle);
    PinChanger.PinSpecification spec = changer.getPinSpecification();
    return new PinInfo(spec.minLength, spec.maxLength, changer.getScrambledPin());
  }

  /**
   * Asks the carrier to change the voicemail PIN, over mobile data like a sync. Returns one of
   * the {@link PinChanger} CHANGE_PIN_* results. On success the remembered random PIN (if any)
   * is forgotten, since the user's own PIN now replaces it.
   */
  @WorkerThread
  @PinChanger.ChangePinResult
  public static int changePin(
      Context context, PhoneAccountHandle handle, String oldPin, String newPin) {
    PinChanger changer = VoicemailComponent.get(context).getVoicemailClient().createPinChanger(context, handle);
    int result = changer.changePin(oldPin, newPin);
    VvmLog.i(TAG, "change PIN result " + result);
    if (result == PinChanger.CHANGE_PIN_SUCCESS && changer.getScrambledPin() != null) {
      changer.setScrambledPin(null);
    }
    return result;
  }

  public static String readLog(Context context) {
    init(context);
    return PersistentLogger.read();
  }

  public static void clearLog(Context context) {
    init(context);
    PersistentLogger.clear();
  }
}
