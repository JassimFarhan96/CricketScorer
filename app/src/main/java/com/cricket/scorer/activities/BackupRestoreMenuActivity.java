package com.cricket.scorer.activities;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.cricket.scorer.R;
import com.cricket.scorer.utils.DataExportUtils;
import com.cricket.scorer.utils.DataImportUtils;

/**
 * BackupRestoreMenuActivity
 *
 * Submenu reached from Home → Backup / Restore.
 * Shows two options:
 *   - Backup   → DataExportUtils.exportAll()
 *   - Restore  → System file picker → DataImportUtils.restoreFromZip()
 *
 * Restore prompts a confirmation dialog before the picker to warn that
 * existing files with matching names will be overwritten. Both operations
 * run on a background thread with a progress dialog and surface a clear
 * success/failure dialog on completion.
 */
public class BackupRestoreMenuActivity extends BaseNavActivity {

    /** Launcher for the system file picker — registered in onCreate. */
    private ActivityResultLauncher<String[]> restorePickerLauncher;

    @Override protected int getCurrentNavItem() { return R.id.nav_home; }

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        setNavContentView(R.layout.activity_backup_restore_menu);

        // Register the file picker launcher BEFORE the activity is started.
        restorePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                this::onRestoreFilePicked);

        LinearLayout backup  = findViewById(R.id.layout_backup);
        LinearLayout restore = findViewById(R.id.layout_restore);

        backup.setOnClickListener(v  -> runBackup());
        restore.setOnClickListener(v -> confirmAndLaunchRestorePicker());
    }

    // ─── Backup ─────────────────────────────────────────────────────────────

    private void runBackup() {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Creating backup…");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            DataExportUtils.Result result = DataExportUtils.exportAll(this);
            runOnUiThread(() -> {
                progress.dismiss();
                if (result.success) {
                    new AlertDialog.Builder(this)
                            .setTitle("Backup complete")
                            .setMessage("Bundled " + result.fileCount + " file"
                                    + (result.fileCount == 1 ? "" : "s") + " into:\n\n"
                                    + result.displayPath
                                    + "\n\nOpen any file manager → Downloads to find it.")
                            .setPositiveButton("OK", null)
                            .show();
                } else {
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

    // ─── Restore ────────────────────────────────────────────────────────────

    private void confirmAndLaunchRestorePicker() {
        new AlertDialog.Builder(this)
                .setTitle("Restore from backup")
                .setMessage("Select a Cricket Scorer backup zip to restore.\n\n"
                        + "Matches and tournaments with the same name as those in the backup "
                        + "will be replaced. Existing matches not present in the backup will "
                        + "remain untouched.\n\nContinue?")
                .setPositiveButton("Choose file", (d, w) -> launchRestorePicker())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchRestorePicker() {
        // Accept .zip MIME types. application/octet-stream is included as a fallback
        // because some file managers report zip files with a generic mime.
        String[] mimes = {
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
        };
        try {
            restorePickerLauncher.launch(mimes);
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                    .setTitle("Cannot open file picker")
                    .setMessage("Your device did not respond to the file picker request: "
                            + e.getMessage())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void onRestoreFilePicked(Uri uri) {
        if (uri == null) return;     // user cancelled the picker

        ProgressDialog progress = new ProgressDialog(this);
        progress.setMessage("Restoring backup…");
        progress.setCancelable(false);
        progress.show();

        new Thread(() -> {
            DataImportUtils.Result result = DataImportUtils.restoreFromZip(this, uri);
            runOnUiThread(() -> {
                progress.dismiss();
                if (result.success) {
                    // Force a full cache rebuild so restored players appear in suggestions
                    com.cricket.scorer.utils.PlayerNameSuggestionsUtil.forceRebuild(
                            BackupRestoreMenuActivity.this);
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
                                // Return to home so the user can browse their restored data
                                Intent intent = new Intent(this, HomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                                              | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                startActivity(intent);
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Restore failed")
                            .setMessage(result.errorMessage != null
                                    ? result.errorMessage : "Unknown error.")
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        }).start();
    }
}
