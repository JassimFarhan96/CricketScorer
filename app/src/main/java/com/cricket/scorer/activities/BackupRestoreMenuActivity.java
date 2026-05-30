package com.cricket.scorer.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.cricket.scorer.R;
import com.cricket.scorer.utils.AppLogger;
import com.cricket.scorer.utils.DataExportUtils;
import com.cricket.scorer.utils.DataImportUtils;

/**
 * BackupRestoreMenuActivity
 *
 * Submenu for Backup and Restore operations.
 * Adds AppLogger statements at every stage and shows a toast with the
 * support email on restore failure.
 */
public class BackupRestoreMenuActivity extends BaseNavActivity {

    private static final String TAG = "BackupRestoreUI";
    private static final String SUPPORT_EMAIL = "cricketscorer.support@gmail.com";

    private ActivityResultLauncher<String[]> restorePickerLauncher;

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_backup_restore_menu);

        restorePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onRestoreFilePicked);

        LinearLayout backup  = findViewById(R.id.layout_backup);
        LinearLayout restore = findViewById(R.id.layout_restore);

        backup.setOnClickListener(v  -> runBackup());
        restore.setOnClickListener(v -> confirmAndLaunchRestorePicker());
    }

    // ── Backup ───────────────────────────────────────────────────────────────

    private void runBackup() {
        AppLogger.d(TAG, "runBackup: user initiated backup");

        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Creating backup\u2026");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            AppLogger.d(TAG, "runBackup: calling DataExportUtils.exportAll()");
            DataExportUtils.Result result = DataExportUtils.exportAll(this);
            runOnUiThread(() -> {
                progress.dismiss();
                if (result.success) {
                    AppLogger.d(TAG, "runBackup: SUCCESS — " + result.fileCount
                            + " file(s) saved to " + result.displayPath);
                    new AlertDialog.Builder(this)
                            .setTitle("Backup complete")
                            .setMessage("Bundled " + result.fileCount + " file"
                                    + (result.fileCount == 1 ? "" : "s") + " into:\n\n"
                                    + result.displayPath
                                    + "\n\nOpen any file manager \u2192 Downloads to find it.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
                    AppLogger.e(TAG, "runBackup: FAILED — " + result.errorMessage);
                    new AlertDialog.Builder(this)
                            .setTitle("Backup failed")
                            .setMessage(result.errorMessage != null
                                    ? result.errorMessage : "Unknown error.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }

    // ── Restore ──────────────────────────────────────────────────────────────

    private void confirmAndLaunchRestorePicker() {
        AppLogger.d(TAG, "confirmAndLaunchRestorePicker: showing confirmation dialog");
        new AlertDialog.Builder(this)
                .setTitle("Restore from backup")
                .setMessage("Select a Cricket Scorer backup file (.cscbak) to restore.\n\n"
                        + "Matches and tournaments with the same name as those in the backup "
                        + "will be replaced. Existing matches not present in the backup will "
                        + "remain untouched.\n\nContinue?")
                .setPositiveButton("Choose file", (d, w) -> launchRestorePicker())
                .setNegativeButton("Cancel", (d, w) ->
                        AppLogger.d(TAG, "confirmAndLaunchRestorePicker: user cancelled"))
                .show();
    }

    private void launchRestorePicker() {
        AppLogger.d(TAG, "launchRestorePicker: opening system file picker");
        String[] mimes = {
                "application/octet-stream",
                "application/zip",
                "application/x-zip-compressed",
                "*/*"
        };
        try {
            restorePickerLauncher.launch(mimes);
        } catch (Exception e) {
            AppLogger.e(TAG, "launchRestorePicker: FAILED to open file picker — "
                    + e.getMessage(), e);
            new AlertDialog.Builder(this)
                    .setTitle("Cannot open file picker")
                    .setMessage("Your device did not respond to the file picker request: "
                            + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void onRestoreFilePicked(Uri uri) {
        if (uri == null) {
            AppLogger.d(TAG, "onRestoreFilePicked: user cancelled file picker — no uri");
            return;
        }

        AppLogger.d(TAG, "onRestoreFilePicked: file picked — uri=" + uri);

        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Restoring backup\u2026");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            AppLogger.d(TAG, "onRestoreFilePicked: calling DataImportUtils.restoreFromZip()");
            DataImportUtils.Result result = DataImportUtils.restoreFromZip(this, uri);

            runOnUiThread(() -> {
                progress.dismiss();

                if (result.success) {
                    AppLogger.d(TAG, "onRestoreFilePicked: restore SUCCESS — "
                            + result.filesRestored + " file(s) restored, "
                            + result.filesSkipped + " skipped");

                    // Rebuild player name suggestion cache for restored players
                    com.cricket.scorer.utils.PlayerNameSuggestionsUtil.forceRebuild(
                            BackupRestoreMenuActivity.this);
                    AppLogger.d(TAG, "onRestoreFilePicked: player name cache force-rebuild"
                            + " triggered after successful restore");

                    StringBuilder msg = new StringBuilder();
                    msg.append("Restored ").append(result.filesRestored).append(" file");
                    if (result.filesRestored != 1) msg.append("s");
                    msg.append(" from the selected backup.");
                    if (result.filesSkipped > 0) {
                        msg.append("\n\n").append(result.filesSkipped)
                           .append(" non-JSON or unsafe entries were skipped.");
                    }
                    msg.append("\n\nYour matches and tournaments are now available "
                            + "from the Recent screens.");

                    new AlertDialog.Builder(this)
                            .setTitle("Restore complete")
                            .setMessage(msg.toString())
                            .setPositiveButton("OK", (d, w) -> {
                                Intent intent = new Intent(this, HomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                              | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                                finish();
                            })
                            .setCancelable(false)
                            .show();

                } else {
                    String errMsg = result.errorMessage != null
                            ? result.errorMessage : "Unknown error.";

                    AppLogger.e(TAG, "onRestoreFilePicked: restore FAILED — "
                            + errMsg
                            + " [filesRestored=" + result.filesRestored
                            + ", filesSkipped=" + result.filesSkipped + "]");

                    // Show toast with support contact as required
                    Toast.makeText(this,
                            "Restore failed! Report the bug to " + SUPPORT_EMAIL,
                            Toast.LENGTH_LONG).show();

                    new AlertDialog.Builder(this)
                            .setTitle("Restore failed")
                            .setMessage(errMsg
                                    + "\n\nIf this problem persists, please report it to:\n"
                                    + SUPPORT_EMAIL)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }
}
