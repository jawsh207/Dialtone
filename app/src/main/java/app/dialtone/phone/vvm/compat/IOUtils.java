/* Minimal replacement for the parts of commons-io IOUtils the voicemail engine uses. */
package app.dialtone.phone.vvm.compat;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class IOUtils {
  private IOUtils() {}

  public static long copyLarge(InputStream in, OutputStream out) throws IOException {
    byte[] buffer = new byte[8192];
    long total = 0;
    int n;
    while ((n = in.read(buffer)) != -1) {
      out.write(buffer, 0, n);
      total += n;
    }
    return total;
  }

  public static int copy(InputStream in, OutputStream out) throws IOException {
    long count = copyLarge(in, out);
    return count > Integer.MAX_VALUE ? -1 : (int) count;
  }

  public static byte[] toByteArray(InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    copyLarge(in, out);
    return out.toByteArray();
  }

  public static void closeQuietly(Closeable closeable) {
    if (closeable == null) return;
    try {
      closeable.close();
    } catch (IOException ignored) {
      // ignored
    }
  }
}
