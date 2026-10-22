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

import java.util.Objects;
import java.util.Optional;

/**
 * Matches a {@link CarrierIdentifier}. Full equality check on CarrierIdentifiers is often unfit
 * because non-MVNO carriers usually just specify the {@link CarrierIdentifier#mccMnc()} while their
 * {@link CarrierIdentifier#gid1()} could be anything. This matcher ignore fields that are not
 * specified in the matcher.
 */
public final class CarrierIdentifierMatcher {

  private final String mccMnc;
  private final Optional<String> gid1;

  private CarrierIdentifierMatcher(String mccMnc, Optional<String> gid1) {
    this.mccMnc = Objects.requireNonNull(mccMnc);
    this.gid1 = gid1;
  }

  public String mccMnc() {
    return mccMnc;
  }

  public Optional<String> gid1() {
    return gid1;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for the matcher */
  public static final class Builder {
    private String mccMnc;
    private Optional<String> gid1 = Optional.empty();

    public Builder setMccMnc(String mccMnc) {
      this.mccMnc = mccMnc;
      return this;
    }

    public Builder setGid1(String gid1) {
      this.gid1 = Optional.ofNullable(gid1);
      return this;
    }

    public CarrierIdentifierMatcher build() {
      return new CarrierIdentifierMatcher(mccMnc == null ? "" : mccMnc, gid1);
    }
  }

  public boolean matches(CarrierIdentifier carrierIdentifier) {
    if (!mccMnc().equals(carrierIdentifier.mccMnc())) {
      return false;
    }
    if (gid1().isPresent()) {
      if (!gid1().get().equalsIgnoreCase(carrierIdentifier.gid1())) {
        return false;
      }
    }
    return true;
  }
}
