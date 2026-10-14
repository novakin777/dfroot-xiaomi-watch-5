package df.root;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small append-only diagnostic log that survives process death and reboot. */
final class PersistentLog {
    private static final String FILE_NAME = "dfroot.log";
    private static final long MAX_BYTES = 512 * 1024;
    private static Uri activeExportUri;
    private static boolean exportSessionInitialized;

    private PersistentLog() {}

    static synchronized void append(Context context, String message) {
        if (!exportSessionInitialized) beginExportSession(context);
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
        String text = message == null ? "<null>" : message;
        if (!text.endsWith("\n")) text += "\n";
        byte[] bytes = (timestamp + "  " + text).getBytes(StandardCharsets.UTF_8);
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (file.length() > MAX_BYTES) {
                File old = new File(file.getParentFile(), FILE_NAME + ".old");
                if (old.exists()) old.delete();
                if (!file.renameTo(old)) file.delete();
            }
            try (FileOutputStream out = new FileOutputStream(file, true)) {
                out.write(bytes);
                out.getFD().sync();
            }
        } catch (Exception ignored) {
            // Logging must never interrupt the exploit or application flow.
        }
        if (activeExportUri != null) {
            try (java.io.OutputStream out = context.getContentResolver()
                    .openOutputStream(activeExportUri, "wa")) {
                if (out == null) throw new java.io.IOException("Downloads provider returned no stream");
                out.write(bytes);
                out.flush();
            } catch (Exception e) {
                activeExportUri = null;
                android.util.Log.w("dfroot", "Could not append to exported run log", e);
            }
        }
    }

    static synchronized String beginExportSession(Context context) {
        activeExportUri = null;
        exportSessionInitialized = true;
        Uri created = null;
        try {
            String name = "dfroot-log-" + System.currentTimeMillis() + ".txt";
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/DFRoot/App/");
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            created = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (created == null) return null;
            try (java.io.OutputStream out = context.getContentResolver().openOutputStream(created, "wa")) {
                if (out == null) throw new java.io.IOException("Downloads provider returned no stream");
            }
            activeExportUri = created;
            return activeExportUri == null ? null : "Download/DFRoot/App/" + name;
        } catch (Exception e) {
            if (created != null) context.getContentResolver().delete(created, null, null);
            android.util.Log.w("dfroot", "Could not create exported run log", e);
            return null;
        }
    }

    static synchronized String read(Context context) {
        try {
            File file = new File(context.getFilesDir(), FILE_NAME);
            if (!file.isFile()) return "No saved DFRoot log yet.";
            byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Could not read saved log: " + e;
        }
    }

    static synchronized boolean clear(Context context) {
        File file = new File(context.getFilesDir(), FILE_NAME);
        File old = new File(file.getParentFile(), FILE_NAME + ".old");
        boolean activeCleared = !file.exists() || file.delete();
        boolean oldCleared = !old.exists() || old.delete();
        return activeCleared && oldCleared;
    }
}
