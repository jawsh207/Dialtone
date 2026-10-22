package app.dialtone.vvm.compat;

import java.util.function.Supplier;

/** Dialer relaxes StrictMode around some disk access; Dialtone doesn't enable StrictMode. */
public final class StrictModeUtils {
  private StrictModeUtils() {}

  public static <T> T bypass(Supplier<T> supplier) {
    return supplier.get();
  }

  public static void bypass(Runnable runnable) {
    runnable.run();
  }
}
