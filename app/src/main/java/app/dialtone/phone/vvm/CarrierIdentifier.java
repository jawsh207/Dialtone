/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * Modified by Dialtone (2026); see NOTICE.
 */

package app.dialtone.phone.vvm;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build.VERSION_CODES;
import androidx.annotation.Nullable;
import android.telecom.PhoneAccountHandle;
import android.telephony.TelephonyManager;
import java.util.Objects;
import java.util.Optional;

/** Identifies a carrier. */
@TargetApi(VERSION_CODES.O)
@SuppressWarnings({"missingpermission"})
public final class CarrierIdentifier {

  private final String mccMnc;
  private final String gid1;

  private CarrierIdentifier(String mccMnc, String gid1) {
    this.mccMnc = Objects.requireNonNull(mccMnc);
    this.gid1 = Objects.requireNonNull(gid1);
  }

  public String mccMnc() {
    return mccMnc;
  }

  /**
   * Group ID Level 1. Used to identify MVNO(Mobile Virtual Network Operators) who subleases other
   * carrier's network and share their mccMnc. MVNO should have a GID1 different from the host.
   */
  public String gid1() {
    return gid1;
  }

  /** Builder for the identifier */
  public static final class Builder {
    private String mccMnc;
    private String gid1;

    public Builder setMccMnc(String mccMnc) {
      this.mccMnc = mccMnc;
      return this;
    }

    public Builder setGid1(String gid1) {
      this.gid1 = gid1;
      return this;
    }

    public CarrierIdentifier build() {
      return new CarrierIdentifier(mccMnc == null ? "" : mccMnc, gid1 == null ? "" : gid1);
    }
  }

  public static Builder builder() {
    return new Builder().setGid1("");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CarrierIdentifier)) return false;
    CarrierIdentifier that = (CarrierIdentifier) o;
    return mccMnc.equals(that.mccMnc) && gid1.equals(that.gid1);
  }

  @Override
  public int hashCode() {
    return Objects.hash(mccMnc, gid1);
  }

  @Override
  public String toString() {
    return "CarrierIdentifier{mccMnc=" + mccMnc + ", gid1=" + gid1 + "}";
  }

  /** Create a identifier for a {@link PhoneAccountHandle}. Absent if the handle is not valid. */
  public static Optional<CarrierIdentifier> forHandle(
      Context context, @Nullable PhoneAccountHandle phoneAccountHandle) {
    if (phoneAccountHandle == null) {
      return Optional.empty();
    }
    TelephonyManager telephonyManager =
        context
            .getSystemService(TelephonyManager.class)
            .createForPhoneAccountHandle(phoneAccountHandle);
    if (telephonyManager == null) {
      return Optional.empty();
    }
    String gid1 = telephonyManager.getGroupIdLevel1();
    if (gid1 == null) {
      gid1 = "";
    }

    return Optional.of(
        builder().setMccMnc(telephonyManager.getSimOperator()).setGid1(gid1).build());
  }
}
