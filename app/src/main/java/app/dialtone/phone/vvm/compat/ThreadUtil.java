package app.dialtone.phone.vvm.compat;

import android.os.Handler;
import android.os.Looper;

public final class ThreadUtil {
  private static final Handler MAIN = new Handler(Looper.getMainLooper());

  private ThreadUtil() {}

  public static void postOnUiThread(Runnable runnable) {
    MAIN.post(runnable);
  }
}
