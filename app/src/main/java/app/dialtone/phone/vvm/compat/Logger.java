/* Replaces Dialer's analytics logger: events go to logcat only, nothing leaves the device. */
package app.dialtone.phone.vvm.compat;

import android.content.Context;
import android.util.Log;

public final class Logger {
  private static final Logger INSTANCE = new Logger();

  private Logger() {}

  public static Logger get(Context context) {
    return INSTANCE;
  }

  public void logImpression(DialerImpression.Type type) {
    Log.d("DialtoneVvm", "event: " + type);
  }
}
