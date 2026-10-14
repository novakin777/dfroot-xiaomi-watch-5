package df.root;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import rikka.shizuku.Shizuku;

/** Reads shell-UID logcat through Shizuku and syncs it into a visible Download file. */
final class ShizukuLogCapture {
    private static final String TAG = "dfroot";

    private ShizukuLogCapture() {}

    static Capture start(Context context, IReporter reporter) throws Exception {
        if (!Shizuku.pingBinder()) throw new IllegalStateException("Shizuku binder unavailable");
        if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Shizuku API permission not granted");

        String name = "dfroot-system-" + System.currentTimeMillis() + ".log";
        Uri mediaUri = createVisibleLogFile(context, name);
        String[] command = {"/system/bin/logcat", "-b", "all", "-v", "threadtime", "-T", "1"};

        rikka.shizuku.ShizukuRemoteProcess process;
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            process = (rikka.shizuku.ShizukuRemoteProcess) newProcess.invoke(null, command, null, null);
        } catch (InvocationTargetException e) {
            context.getContentResolver().delete(mediaUri, null, null);
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw e;
        } catch (Exception e) {
            context.getContentResolver().delete(mediaUri, null, null);
            throw e;
        }

        Capture capture = new Capture(process, context, mediaUri,
                "Download/DFRoot/System/" + name);
        capture.drainErrors(reporter);
        capture.copyOutput(reporter);
        if (!capture.awaitReady(4, TimeUnit.SECONDS)) {
            capture.stop();
            context.getContentResolver().delete(mediaUri, null, null);
            throw new java.io.IOException("No system log bytes were saved; see logcat errors above");
        }
        return capture;
    }

    private static Uri createVisibleLogFile(Context context, String name) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/DFRoot/System/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        Uri uri = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new java.io.IOException("Downloads provider could not register system log");
        try (java.io.OutputStream out = context.getContentResolver().openOutputStream(uri, "w")) {
            if (out == null) throw new java.io.IOException("Downloads provider returned no system-log stream");
            out.flush();
        } catch (Exception e) {
            context.getContentResolver().delete(uri, null, null);
            throw e;
        }
        return uri;
    }

    static final class Capture {
        private final Process process;
        private final Context context;
        private final Uri mediaUri;
        private final String path;
        private final CountDownLatch ready = new CountDownLatch(1);
        private final CountDownLatch writerFinished = new CountDownLatch(1);
        private final AtomicBoolean started = new AtomicBoolean(false);
        private final AtomicLong bytesSaved = new AtomicLong();

        Capture(Process process, Context context, Uri mediaUri, String path) {
            this.process = process;
            this.context = context;
            this.mediaUri = mediaUri;
            this.path = path;
        }

        String getPath() {
            return path;
        }

        long getBytesSaved() {
            return bytesSaved.get();
        }

        boolean awaitReady(long timeout, TimeUnit unit) throws InterruptedException {
            return ready.await(timeout, unit) && started.get();
        }

        void stop() {
            try {
                process.destroy();
            } catch (Exception e) {
                Log.w(TAG, "Could not stop Shizuku logcat", e);
            }
            try {
                if (!writerFinished.await(3, TimeUnit.SECONDS))
                    Log.w(TAG, "Timed out waiting for system log writer to finish");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted while waiting for system log writer", e);
            }
        }

        private void drainErrors(IReporter reporter) {
            Thread thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    for (String line; (line = reader.readLine()) != null; )
                        reporter.report("logcat: " + line + "\n");
                } catch (Exception e) {
                    Log.w(TAG, "Error reading Shizuku logcat stderr", e);
                }
            }, "dfroot-logcat-errors");
            thread.setDaemon(true);
            thread.start();
        }

        private void copyOutput(IReporter reporter) {
            Thread thread = new Thread(() -> {
                try (ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(mediaUri, "w");
                     InputStream source = process.getInputStream()) {
                    if (fd == null) throw new java.io.IOException("Downloads provider returned no system-log fd");
                    FileOutputStream out = new FileOutputStream(fd.getFileDescriptor());
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = source.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        out.flush();
                        fd.getFileDescriptor().sync();
                        bytesSaved.addAndGet(count);
                        if (started.compareAndSet(false, true)) ready.countDown();
                    }
                } catch (Exception e) {
                    reporter.report("system log write failed: " + e + "\n");
                    Log.w(TAG, "Could not save Shizuku logcat output", e);
                } finally {
                    ready.countDown();
                    writerFinished.countDown();
                }
            }, "dfroot-logcat-writer");
            thread.setDaemon(true);
            thread.start();
        }
    }
}
