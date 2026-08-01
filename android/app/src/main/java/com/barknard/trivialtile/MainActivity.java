package com.barknard.trivialtile;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.barknard.trivialtile.util.Qr;

import java.util.Objects;

/**
 * One screen: it starts hosting the moment the app opens and shows players how
 * to join.
 */
public class MainActivity extends android.app.Activity {

    private static final String TAG = "TrivialTile";
    private static final String CHROME = "com.android.chrome";

    private TextView statusView;
    private TextView urlView;
    private TextView playersView;
    private TextView updateView;
    private ImageView qrView;
    private Button appUpdateButton;
    private Button hostingToggle;
    private String qrEncodedUrl = "";

    private final Runnable stateListener = this::render;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        statusView = findViewById(R.id.status);
        urlView = findViewById(R.id.url);
        playersView = findViewById(R.id.players);
        updateView = findViewById(R.id.update_status);
        qrView = findViewById(R.id.qr);
        appUpdateButton = findViewById(R.id.install_update);
        hostingToggle = findViewById(R.id.stop_hosting);

        findViewById(R.id.open_host).setOnClickListener(v -> openHost());
        findViewById(R.id.open_board).setOnClickListener(v -> openBoard());
        findViewById(R.id.copy_link).setOnClickListener(v -> copyPlayerLink());
        findViewById(R.id.share_link).setOnClickListener(v -> sharePlayerLink());
        findViewById(R.id.check_updates).setOnClickListener(v -> checkForUpdates());
        hostingToggle.setOnClickListener(v -> toggleHosting());
        appUpdateButton.setOnClickListener(v -> installAppUpdate());

        askForNotificationPermission();
        startHosting();
    }

    @Override
    protected void onResume() {
        super.onResume();
        HostState.addListener(stateListener);
        // Restart only if the service died on us - if the user tapped "Stop
        // hosting", leave it stopped until they ask for it again.
        if (!HostService.isHosting() && HostState.stage == HostState.Stage.HOSTING) {
            startHosting();
        }
        render();
    }

    @Override
    protected void onPause() {
        HostState.removeListener(stateListener);
        super.onPause();
    }

    private void startHosting() {
        Intent intent = new Intent(this, HostService.class).setAction(HostService.ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void toggleHosting() {
        if (HostService.isHosting() || HostState.stage == HostState.Stage.STARTING
                || HostState.stage == HostState.Stage.UNPACKING) {
            startService(new Intent(this, HostService.class).setAction(HostService.ACTION_STOP));
            Toast.makeText(this, R.string.hosting_stopped, Toast.LENGTH_SHORT).show();
        } else {
            HostState.set(HostState.Stage.STARTING, getString(R.string.status_starting));
            startHosting();
        }
    }

    private void askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    // ------------------------------------------------------------------- view

    private void render() {
        if (isFinishing()) {
            return;
        }
        HostState.Stage stage = HostState.stage;
        String url = HostState.playerUrl;

        switch (stage) {
            case HOSTING:
                statusView.setText(R.string.status_hosting);
                statusView.setTextColor(getColor(R.color.accent_green));
                break;
            case ERROR:
                statusView.setText(HostState.statusMessage);
                statusView.setTextColor(getColor(R.color.accent_red));
                break;
            case STOPPED:
                statusView.setText(R.string.status_stopped);
                statusView.setTextColor(getColor(R.color.text_dim));
                break;
            default:
                statusView.setText(HostState.statusMessage.isEmpty()
                        ? getString(R.string.status_starting) : HostState.statusMessage);
                statusView.setTextColor(getColor(R.color.text_dim));
                break;
        }

        boolean live = stage == HostState.Stage.HOSTING || stage == HostState.Stage.STARTING
                || stage == HostState.Stage.UNPACKING;
        hostingToggle.setText(live ? R.string.stop_hosting : R.string.start_hosting);

        urlView.setText(url.isEmpty() ? getString(R.string.url_placeholder) : url);
        playersView.setText(getResources().getQuantityString(R.plurals.players_joined,
                HostState.players, HostState.players));
        updateView.setText(HostState.updateStatus);
        appUpdateButton.setVisibility(HostState.availableApkUrl == null ? View.GONE : View.VISIBLE);
        if (HostState.availableAppVersion != null) {
            appUpdateButton.setText(getString(R.string.install_app_update, HostState.availableAppVersion));
        }

        if (!url.isEmpty() && !Objects.equals(url, qrEncodedUrl)) {
            Bitmap qr = Qr.encode(url, 640);
            if (qr != null) {
                qrView.setImageBitmap(qr);
                qrEncodedUrl = url;
            }
        }
    }

    // ---------------------------------------------------------------- actions

    private void openHost() {
        if (requireUrl()) {
            openInBrowser(HostState.hostUrl, false);
        }
    }

    /**
     * The board is meant to live in its own Chrome window so it can be cast to a
     * TV while the host keeps control on the tablet. Try an incognito window
     * first (that is always a separate window), and leave the link on the
     * clipboard either way.
     */
    private void openBoard() {
        if (!requireUrl()) {
            return;
        }
        copyToClipboard("Board link", HostState.boardUrl);
        openInBrowser(HostState.boardUrl, true);
        Toast.makeText(this, R.string.board_hint, Toast.LENGTH_LONG).show();
    }

    private void openInBrowser(String url, boolean separateWindow) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (separateWindow) {
            // Ask for a new document so Chrome gives the board its own window
            // instead of reusing the tab the host is in.
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        }
        if (isInstalled(CHROME)) {
            intent.setPackage(CHROME);
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            Log.i(TAG, "Could not open " + url + ": " + e);
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception fallbackFailure) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void copyPlayerLink() {
        if (requireUrl()) {
            copyToClipboard("Trivia link", HostState.playerUrl);
            Toast.makeText(this, R.string.link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void sharePlayerLink() {
        if (!requireUrl()) {
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.share_text, HostState.playerUrl));
        startActivity(Intent.createChooser(share, getString(R.string.share_title)));
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }

    private void checkForUpdates() {
        HostState.setUpdateStatus("Checking GitHub…");
        HostService service = HostService.peek();
        if (service != null) {
            service.checkForUpdatesNow();
        } else {
            Intent intent = new Intent(this, HostService.class).setAction(HostService.ACTION_CHECK_UPDATES);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }
    }

    private void installAppUpdate() {
        HostService service = HostService.peek();
        if (service == null) {
            Toast.makeText(this, R.string.update_not_ready, Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            // Android needs "install unknown apps" for this app before it can
            // update itself.
            Toast.makeText(this, R.string.allow_installs, Toast.LENGTH_LONG).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + getPackageName())));
                return;
            } catch (Exception e) {
                Log.i(TAG, "Could not open install-sources settings: " + e);
            }
        }
        service.installAvailableApk();
    }

    private boolean requireUrl() {
        if (HostState.playerUrl.isEmpty()) {
            Toast.makeText(this, R.string.not_hosting_yet, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
