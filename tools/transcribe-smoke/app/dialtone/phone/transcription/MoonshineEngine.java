package app.dialtone.phone.transcription;

import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * CI smoke test for the voicemail transcriber's native bridge. Same package, class and
 * native signatures as the app's MoonshineEngine, so it exercises the exact JNI symbols
 * in libdialtone_transcriber on the build machine.
 *
 * Usage: java -Djava.library.path=<dir> app.dialtone.phone.transcription.MoonshineEngine model.gguf a.wav [b.wav ...]
 * Each WAV (16 kHz mono 16-bit) is transcribed twice: as-is, and degraded to 8 kHz phone
 * bandwidth then upsampled again, the way voicemail audio reaches the model.
 */
public final class MoonshineEngine {
    private static native long nativeOpen(String modelPath, int nThreads);
    private static native long nativeMaxAudioMs(long handle);
    private static native String nativeTranscribe(long handle, float[] pcm);
    private static native void nativeClose(long handle);
    private static native String nativeLastError();

    public static void main(String[] args) throws IOException {
        System.loadLibrary("dialtone_transcriber");
        long handle = nativeOpen(args[0], 4);
        if (handle == 0) {
            System.out.println("::error title=Transcription smoke test::model load failed: " + nativeLastError());
            System.exit(1);
        }
        long maxMs = nativeMaxAudioMs(handle);
        System.out.println("max audio per call: " + (maxMs == 0 ? "unlimited" : maxMs + " ms"));
        boolean ok = true;
        for (int i = 1; i < args.length; i++) {
            float[] wide = readWav16k(args[i]);
            float[] phone = upsample2x(downsample2x(wide));
            String name = args[i].substring(args[i].lastIndexOf('/') + 1);
            ok &= run(handle, maxMs, name + " (16 kHz)", wide);
            ok &= run(handle, maxMs, name + " (8 kHz phone)", phone);
        }
        nativeClose(handle);
        if (!ok) System.exit(1);
    }

    private static boolean run(long handle, long maxMs, String label, float[] pcm) {
        long start = System.nanoTime();
        StringBuilder text = new StringBuilder();
        for (int[] r : chunks(pcm, maxMs)) {
            float[] piece = new float[r[1] - r[0]];
            System.arraycopy(pcm, r[0], piece, 0, piece.length);
            String t = nativeTranscribe(handle, piece);
            if (t == null) {
                System.out.println("::error title=Transcription smoke test::" + label + ": " + nativeLastError());
                return false;
            }
            if (text.length() > 0) text.append(' ');
            text.append(t.trim());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        String out = text.toString().trim();
        System.out.println("::notice title=Transcript: " + label + " (" + pcm.length / 16000 + " s audio, "
                + ms + " ms)::" + out.replace("%", "%25"));
        return !out.isEmpty();
    }

    // Same rule as the app's Chunker: <= 20 s pieces, cut at the quietest 20 ms in the last 40%.
    private static List<int[]> chunks(float[] pcm, long maxAudioMs) {
        int limit = maxAudioMs > 0 ? (int) Math.min(maxAudioMs / 1000 - 1, 20) : 20;
        int maxLen = Math.max(limit, 5) * 16000, frame = 320;
        List<int[]> out = new ArrayList<>();
        int start = 0;
        while (pcm.length - start > maxLen) {
            int from = start + (int) (maxLen * 0.6), to = start + maxLen, best = to;
            float bestE = Float.MAX_VALUE;
            for (int i = from; i + frame <= to; i += frame) {
                float e = 0;
                for (int j = i; j < i + frame; j++) e += pcm[j] * pcm[j];
                if (e < bestE) { bestE = e; best = i + frame / 2; }
            }
            out.add(new int[] {start, best});
            start = best;
        }
        if (start < pcm.length) out.add(new int[] {start, pcm.length});
        return out;
    }

    private static float[] downsample2x(float[] in) {
        float[] out = new float[in.length / 2];
        for (int i = 0; i < out.length; i++) out[i] = (in[2 * i] + in[2 * i + 1]) / 2f;
        return out;
    }

    private static float[] upsample2x(float[] in) {
        float[] out = new float[in.length * 2];
        for (int i = 0; i < out.length; i++) {
            double pos = i / 2.0;
            int i0 = (int) pos, i1 = Math.min(i0 + 1, in.length - 1);
            float frac = (float) (pos - i0);
            out[i] = in[i0] * (1 - frac) + in[i1] * frac;
        }
        return out;
    }

    private static float[] readWav16k(String path) throws IOException {
        byte[] all;
        try (DataInputStream in = new DataInputStream(new FileInputStream(path))) {
            all = in.readAllBytes();
        }
        ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        b.position(12);
        while (b.remaining() >= 8) {
            int id = b.getInt(), size = b.getInt();
            if (id == 0x61746164) { // "data"
                int n = size / 2;
                float[] pcm = new float[n];
                for (int i = 0; i < n; i++) pcm[i] = b.getShort() / 32768f;
                return pcm;
            }
            b.position(b.position() + size + (size & 1));
        }
        throw new IOException("no data chunk in " + path);
    }
}
