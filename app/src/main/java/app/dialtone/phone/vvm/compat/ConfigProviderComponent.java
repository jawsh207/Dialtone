/*
 * Dialer reads server-side feature flags here. Dialtone has no flag server: every flag
 * returns its default, except the carrier-enable flags, which are on so every carrier in
 * vvm_config.xml is supported.
 */
package app.dialtone.phone.vvm.compat;

import android.content.Context;

public final class ConfigProviderComponent {
  private static final ConfigProviderComponent INSTANCE = new ConfigProviderComponent();
  private final ConfigProvider provider = new ConfigProvider();

  private ConfigProviderComponent() {}

  public static ConfigProviderComponent get(Context context) {
    return INSTANCE;
  }

  public ConfigProvider getConfigProvider() {
    return provider;
  }

  public static final class ConfigProvider {
    public String getString(String key, String defaultValue) {
      return defaultValue;
    }

    public long getLong(String key, long defaultValue) {
      return defaultValue;
    }

    public boolean getBoolean(String key, boolean defaultValue) {
      if (key == null) {
        return defaultValue;
      }
      // Carrier entries in vvm_config.xml gated by flags: turn on the "enable" variants
      // (Metro by T-Mobile, Claro PR, Sunrise, Cellcom) and leave their "disable" twins off.
      if (key.startsWith("enable_vvm_carrier") || key.equals("vvm_carrier_cellcom")) {
        return true;
      }
      return defaultValue;
    }
  }
}
