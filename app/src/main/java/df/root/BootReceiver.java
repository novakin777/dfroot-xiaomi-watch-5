package df.root;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

public class BootReceiver extends BroadcastReceiver implements IReporter {
    private static final String TAG = "dfroot";
    private volatile Context logContext;

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
        Context ctx = logContext;
        if (ctx != null) PersistentLog.append(ctx, msg);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        logContext = context.createDeviceProtectedStorageContext();
        report("boot receiver invoked: " + intent.getAction() + "\n");
        if (new File("/dev/df").exists()) {
            report("boot: already hooked, skipping\n");
            return;
        }
        report("boot: starting exploit after boot\n");
        final Context deCtx = context.createDeviceProtectedStorageContext();
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "dfroot:boot");
        wl.acquire();
        new Thread(() -> {
            try {
                int rc = ExploitRunner.run(deCtx, this);
                report("boot: exploit rc=" + rc + "\n");
            } catch (Exception e) {
                Log.e(TAG, "boot: exploit exception", e);
                report("boot: exploit exception: " + Log.getStackTraceString(e) + "\n");
            } finally {
                wl.release();
            }
        }, "dfroot-boot").start();
    }
}
