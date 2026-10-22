package app.dialtone.phone.vvm.compat;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Small on-device log for the voicemail engine, shown in Dialtone's voicemail diagnostics so
 * carrier problems can be debugged without adb. Never leaves the device unless the user copies it.
 */
public final class PersistentLogger {
  private static final long MAX_BYTES = 256 * 1024;
  private static volatile File file;

  private PersistentLogger() {}

  public static void init(Context context) {
    if (file == null) {
      file = new File(context.getApplicationContext().getNoBackupFilesDir(), "vvm.log");
    }
  }

  public static synchronized void logText(String tag, String text) {
    File f = file;
    if (f == null) return;
    String line =
        new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date())
            + " " + tag + ": " + redact(text) + "\n";
    try {
      if (f.length() > MAX_BYTES) trim(f);
      try (FileOutputStream out = new FileOutputStream(f, true)) {
        out.write(line.getBytes(StandardCharsets.UTF_8));
      }
    } catch (IOException ignored) {
      // Diagnostics only.
    }
  }

  public static synchronized String read() {
    File f = file;
    if (f == null || !f.exists()) return "";
    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
      byte[] bytes = new byte[(int) raf.length()];
      raf.readFully(bytes);
      return new String(bytes, StandardCharsets.UTF_8);
    } catch (IOException e) {
      return "";
    }
  }

  public static synchronized void clear() {
    File f = file;
    if (f != null) f.delete();
  }

  /** Keep the newest half of the file. */
  private static void trim(File f) throws IOException {
    byte[] tail;
    try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
      long keep = raf.length() / 2;
      raf.seek(raf.length() - keep);
      tail = new byte[(int) keep];
      raf.readFully(tail);
    }
    try (FileOutputStream out = new FileOutputStream(f, false)) {
      out.write(tail);
    }
  }

  /** Credentials arrive in STATUS SMS fields; never write them to the log. */
  private static String redact(String text) {
    if (text == null) return "";
    return text.replaceAll("(?i)\\b(pw|password|pin|smtp_pw|u|user)=([^;,\\s}]+)", "$1=<redacted>");
  }
}
