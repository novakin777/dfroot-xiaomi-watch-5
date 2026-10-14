package df.root;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements IReporter {

    private static final String TAG = "dfroot";
    private static final int SHIZUKU_PERMISSION_REQUEST = 4108;

    private Button btnRun;
    private ScrollView outputScroll;
    private TextView outputView;
    private Spinner spinnerSuManager;
    private Switch switchSoftReboot;
    private Context mDeCtx;
    private final Handler mMain = new Handler(Looper.getMainLooper());
    private final Executor mExec = Executors.newSingleThreadExecutor();
    private int mValidSuManagerPos = 0;
    private final Shizuku.OnRequestPermissionResultListener mShizukuPermissionListener =
            (requestCode, grantResult) -> {
                if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    beginExploit();
                } else {
                    report("Shizuku permission denied; exploit was not started\n");
                    updateRunButton();
                    Toast.makeText(this, "Grant Shizuku permission to capture system logs", Toast.LENGTH_LONG).show();
                }
            };

    @Override
    public void report(String msg) {
        Log.i(TAG, msg.trim());
        PersistentLog.append(mDeCtx != null ? mDeCtx : this, msg);
        mMain.post(() -> {
            outputView.append(msg);
            outputScroll.post(() -> outputScroll.fullScroll(View.FOCUS_DOWN));
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shizuku.addRequestPermissionResultListener(mShizukuPermissionListener);
        mDeCtx = createDeviceProtectedStorageContext();
        setContentView(R.layout.activity_main);

        if (getActionBar() != null) {
            getActionBar().setSubtitle("@diabl0w github/xda");
        }

        btnRun = findViewById(R.id.btnRun);
        outputScroll = findViewById(R.id.outputScroll);
        outputView = findViewById(R.id.outputView);
        outputView.setText(PersistentLog.read(mDeCtx));
        spinnerSuManager = findViewById(R.id.spinnerSuManager);

        PackageManager pm = getPackageManager();
        List<SuManagerEntry> entries = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (!ExploitRunner.hasKsud(ai)) continue;
            entries.add(new SuManagerEntry(ai.packageName, pm.getApplicationLabel(ai), pm.getApplicationIcon(ai)));
        }
        entries.sort((a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        entries.add(0, new SuManagerEntry(null, "Select a SU Manager", null));

        spinnerSuManager.setAdapter(new SuManagerAdapter(this, entries));

        SharedPreferences prefs = mDeCtx.getSharedPreferences(ExploitRunner.PREFS_NAME, Context.MODE_PRIVATE);
        switchSoftReboot = findViewById(R.id.switchSoftReboot);
        switchSoftReboot.setChecked(prefs.getBoolean(ExploitRunner.PREF_SOFT_REBOOT, false));
        switchSoftReboot.setOnCheckedChangeListener((button, checked) ->
                prefs.edit().putBoolean(ExploitRunner.PREF_SOFT_REBOOT, checked).apply());
        String saved = prefs.getString(ExploitRunner.PREF_SU_MANAGER, null);
        boolean savedFound = false;
        for (int i = 1; i < entries.size(); i++) {
            if (entries.get(i).packageName.equals(saved)) {
                spinnerSuManager.setSelection(i);
                mValidSuManagerPos = i;
                savedFound = true;
                break;
            }
        }
        if (!savedFound && saved != null) { // Saved manager was uninstalled
            prefs.edit().remove(ExploitRunner.PREF_SU_MANAGER).apply();
        }

        spinnerSuManager.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                SuManagerEntry e = entries.get(pos);
                if (e.packageName == null) return;
                mValidSuManagerPos = pos;
                prefs.edit().putString(ExploitRunner.PREF_SU_MANAGER, e.packageName).apply();
                updateRunButton();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        updateRunButton();

        btnRun.setOnClickListener(v -> {
            requestLogCapturePermission();
        });

        findViewById(R.id.btnSaveLog).setOnClickListener(v -> saveLogToDownloads());
        findViewById(R.id.btnClearLog).setOnClickListener(v -> confirmClearLog());
    }

    private void requestLogCapturePermission() {
        if (!Shizuku.pingBinder()) {
            report("Shizuku is not running; exploit was not started\n");
            Toast.makeText(this, "Start Shizuku first; exploit not started", Toast.LENGTH_LONG).show();
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST);
            return;
        }
        beginExploit();
    }

    private void beginExploit() {
        btnRun.setEnabled(false);
        outputView.setText("");
        String appLogPath = PersistentLog.beginExportSession(mDeCtx);
        report("\n=== Launch Root pressed ===\n");
        report(appLogPath == null
                ? "DFRoot log is being saved to app storage; Download export could not be created\n"
                : "DFRoot log is being saved to " + appLogPath + "\n");
        mExec.execute(() -> {
            ShizukuLogCapture.Capture capture = null;
            try {
                capture = ShizukuLogCapture.start(this, this);
                if (capture == null) {
                    report("system log capture failed; exploit was not started\n");
                    mMain.post(() -> {
                        btnRun.setEnabled(true);
                        Toast.makeText(this, "Could not start Shizuku log capture", Toast.LENGTH_LONG).show();
                    });
                    return;
                }
                report("System log saved " + capture.getBytesSaved() + " bytes; path: "
                        + capture.getPath() + "\n");
                runExploit();
                Thread.sleep(3000);
            } catch (Exception e) {
                report("Shizuku capture setup failed: " + Log.getStackTraceString(e) + "\n");
                mMain.post(() -> {
                    updateRunButton();
                    Toast.makeText(this, "System log capture failed; exploit not started", Toast.LENGTH_LONG).show();
                });
            } finally {
                if (capture != null) capture.stop();
            }
        });
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(mShizukuPermissionListener);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateRunButton() {
        btnRun.setEnabled(mValidSuManagerPos >= 1 && !new File("/dev/df").exists());
    }

    private void saveLogToDownloads() {
        String name = "dfroot-log-" + System.currentTimeMillis() + ".txt";
        android.net.Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/DFRoot/App/");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Downloads provider returned no file");
            try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                if (out == null) throw new IllegalStateException("Could not open exported log");
                out.write(PersistentLog.read(mDeCtx).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                out.flush();
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);
            Toast.makeText(this, "Saved to Download/DFRoot/App/" + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            Toast.makeText(this, "Could not save log: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClearLog() {
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("Clear app log history?")
                .setMessage("This clears DFRoot's saved run history. Log files already exported to Downloads are kept.")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    if (PersistentLog.clear(mDeCtx)) {
                        outputView.setText("");
                        Toast.makeText(this, "App log history cleared", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Could not clear all app logs", Toast.LENGTH_LONG).show();
                    }
                })
                .create();
        alertDialog.setOnShowListener(ignored -> {
                    Button clearButton = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    clearButton.setTextColor(0xFFFFFFFF);
                    clearButton.setBackground(dialogButtonBackground(0xFF6FAF7B));
                    Button cancelButton = alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    cancelButton.setTextColor(0xFF37423A);
                    cancelButton.setBackground(dialogButtonBackground(0xFFE8EEE9));
                });
        alertDialog.show();
    }

    private Drawable dialogButtonBackground(int color) {
        float radius = 24 * getResources().getDisplayMetrics().density;
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(radius);
        return background;
    }

    private void runExploit() {
        try {
            int rc = ExploitRunner.run(mDeCtx, this);
            String msg = rc == 0 ? "DFRoot: SUCCESS"
                       : rc == 1 ? "DFRoot: Error - ksud nonzero exit"
                       : rc == 2 ? "DFRoot: Error - check logcat & dmesg"
                       : "DFRoot: Error - failed to patch files";
            mMain.post(() -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show());
        } catch (Exception e) {
            Log.e(TAG, "exploit exception", e);
            report("\nexception: " + Log.getStackTraceString(e) + "\n");
        } finally {
            mMain.post(this::updateRunButton);
        }
    }

    private static class SuManagerEntry {
        final String packageName;
        final CharSequence label;
        final Drawable icon;

        SuManagerEntry(String pkg, CharSequence label, Drawable icon) {
            this.packageName = pkg;
            this.label = label;
            this.icon = icon;
        }
    }

    private static class SuManagerAdapter extends ArrayAdapter<SuManagerEntry> {
        SuManagerAdapter(Context ctx, List<SuManagerEntry> items) {
            super(ctx, R.layout.item_su_manager, items);
        }

        @Override
        public View getView(int pos, View v, ViewGroup parent) {
            if (v == null || v.getTag() != Boolean.FALSE)
                v = LayoutInflater.from(getContext()).inflate(R.layout.item_su_manager_closed, parent, false);
            v.setTag(Boolean.FALSE);
            return bindClosedView(pos, v);
        }

        @Override
        public View getDropDownView(int pos, View v, ViewGroup parent) {
            if (v == null || v.getTag() != Boolean.TRUE)
                v = LayoutInflater.from(getContext()).inflate(R.layout.item_su_manager, parent, false);
            v.setTag(Boolean.TRUE);
            return bindView(pos, v);
        }

        @Override
        public boolean isEnabled(int pos) {
            return getItem(pos).packageName != null;
        }

        private View bindClosedView(int pos, View v) {
            SuManagerEntry e = getItem(pos);
            ImageView icon = v.findViewById(R.id.iconApp);
            TextView label = v.findViewById(R.id.labelApp);
            if (e.packageName == null) {
                icon.setVisibility(View.GONE);
                label.setVisibility(View.VISIBLE);
                label.setText(e.label);
            } else {
                icon.setVisibility(View.VISIBLE);
                label.setVisibility(View.GONE);
                icon.setImageDrawable(e.icon);
            }
            return v;
        }

        private View bindView(int pos, View v) {
            SuManagerEntry e = getItem(pos);
            ((ImageView) v.findViewById(R.id.iconApp)).setImageDrawable(e.icon);
            ((TextView)  v.findViewById(R.id.labelApp)).setText(e.label);
            return v;
        }
    }
}
