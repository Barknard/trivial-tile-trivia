package com.barknard.trivialtile.update;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;
import android.widget.Toast;

import com.barknard.trivialtile.HostState;

/** Relays package-installer results back to the user. */
public class InstallReceiver extends BroadcastReceiver {

    private static final String TAG = "TrivialTile";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        switch (status) {
            case PackageInstaller.STATUS_PENDING_USER_ACTION: {
                // Android wants the user to confirm - show its dialog.
                Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirm);
                }
                break;
            }
            case PackageInstaller.STATUS_SUCCESS:
                HostState.setUpdateStatus("App updated");
                break;
            default: {
                String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                Log.i(TAG, "Install finished with status " + status + ": " + message);
                HostState.setUpdateStatus("App update was not installed");
                Toast.makeText(context, "App update was not installed", Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }
}
