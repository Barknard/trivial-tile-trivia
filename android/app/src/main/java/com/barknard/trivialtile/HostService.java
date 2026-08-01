package com.barknard.trivialtile;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.barknard.trivialtile.server.GameServer;
import com.barknard.trivialtile.server.NetUtils;
import com.barknard.trivialtile.server.Slog;
import com.barknard.trivialtile.update.ApkUpdater;
import com.barknard.trivialtile.update.ContentStore;
import com.barknard.trivialtile.update.ContentUpdater;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Runs the game server for as long as the app is hosting. A foreground service
 * so Android doesn't kill it when the host switches to Chrome to drive the game.
 */
public class HostService extends Service {

    private static final String TAG = "TrivialTile";
    private static final String CHANNEL_ID = "hosting";
    private static final int NOTIFICATION_ID = 7;
    private static final int PREFERRED_PORT = 5000;

    public static final String ACTION_START = "com.barknard.trivialtile.action.START";
    public static final String ACTION_STOP = "com.barknard.trivialtile.action.STOP";
    public static final String ACTION_CHECK_UPDATES = "com.barknard.trivialtile.action.CHECK_UPDATES";

    private static volatile HostService instance;

    private GameServer server;
    private ContentStore store;
    private ContentUpdater contentUpdater;
    private ApkUpdater apkUpdater;
    private WifiManager.WifiLock wifiLock;
    private PowerManager.WakeLock wakeLock;
    private ScheduledExecutorService ticker;
    private volatile boolean starting = false;

    public static boolean isHosting() {
        HostService service = instance;
        return service != null && service.server != null && service.server.isRunning();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        store = new ContentStore(this);
        contentUpdater = new ContentUpdater(this, store);
        apkUpdater = new ApkUpdater(this);
        Slog.setSink((tag, message, error) -> {
            if (error != null) {
                Log.e(TAG, "[" + tag + "] " + message, error);
            } else {
                Log.i(TAG, "[" + tag + "] " + message);
            }
        });
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Must happen immediately - Android gives a foreground service a few
        // seconds to show its notification.
        startForegroundNotification(notification("Starting…", null));

        String action = intent == null ? ACTION_START : String.valueOf(intent.getAction());
        if (ACTION_STOP.equals(action)) {
            stopHosting();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CHECK_UPDATES.equals(action)) {
            new Thread(this::runUpdateChecks, "trivia-update").start();
            return START_STICKY;
        }
        if (server == null && !starting) {
            starting = true;
            new Thread(this::startHosting, "trivia-boot").start();
        }
        return START_STICKY;
    }

    private void startHosting() {
        try {
            int versionCode = appVersionCode();
            if (store.needsUnpack(versionCode)) {
                HostState.set(HostState.Stage.UNPACKING, "Unpacking the game…");
                store.unpackBundledContent(versionCode, (done, total) ->
                        HostState.set(HostState.Stage.UNPACKING, "Unpacking the game… " + (done * 100 / total) + "%"));
            }
            if (contentUpdater.hasPendingUpdate()) {
                HostState.set(HostState.Stage.STARTING, "Applying downloaded update…");
                store.applyStagedUpdate();
            }

            HostState.set(HostState.Stage.STARTING, "Starting the server…");
            GameServer newServer = new GameServer(store.liveDir());
            int port = newServer.start(PREFERRED_PORT, 12);
            server = newServer;
            acquireLocks();
            publishAddresses();
            HostState.set(HostState.Stage.HOSTING, "Hosting on this WiFi");
            updateNotification();
            startTicker();
            runUpdateChecks();
        } catch (Exception e) {
            Log.e(TAG, "Could not start hosting", e);
            HostState.set(HostState.Stage.ERROR, "Could not start: " + e.getMessage());
            updateNotification();
        } finally {
            starting = false;
        }
    }

    private void runUpdateChecks() {
        GameServer running = server;
        boolean idle = running == null || running.hub().isIdle();
        ContentUpdater.Result result = contentUpdater.sync(idle, HostState::setUpdateStatus);
        if (result.applied) {
            // Files changed underneath us; nothing to restart, the server reads
            // from disk per request.
            HostState.changed();
        }
        ApkUpdater.Release release = apkUpdater.findNewerRelease(appVersionCode());
        if (release != null) {
            HostState.availableAppVersion = release.name;
            HostState.availableApkUrl = release.apkUrl;
            HostState.setUpdateStatus("New app version available: " + release.name);
        } else {
            HostState.availableAppVersion = null;
            HostState.availableApkUrl = null;
        }
    }

    /** Kicked off from the UI. */
    public void installAvailableApk() {
        new Thread(() -> {
            ApkUpdater.Release release = apkUpdater.findNewerRelease(appVersionCode());
            if (release == null) {
                HostState.setUpdateStatus("Already on the newest app version");
                return;
            }
            apkUpdater.downloadAndInstall(release, HostState::setUpdateStatus);
        }, "trivia-apk").start();
    }

    public static HostService peek() {
        return instance;
    }

    public void checkForUpdatesNow() {
        new Thread(this::runUpdateChecks, "trivia-update").start();
    }

    private void startTicker() {
        stopTicker();
        ticker = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "trivia-ticker");
            thread.setDaemon(true);
            return thread;
        });
        ticker.scheduleWithFixedDelay(() -> {
            try {
                GameServer running = server;
                if (running == null) {
                    return;
                }
                int players = running.hub().playerCount();
                boolean addressChanged = publishAddresses();
                if (players != HostState.players || addressChanged) {
                    HostState.players = players;
                    HostState.changed();
                    updateNotification();
                }
            } catch (Exception e) {
                Log.i(TAG, "ticker: " + e);
            }
        }, 3, 3, TimeUnit.SECONDS);
    }

    private void stopTicker() {
        ScheduledExecutorService current = ticker;
        ticker = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    /** @return true when the WiFi address changed since last time */
    private boolean publishAddresses() {
        GameServer running = server;
        if (running == null) {
            return false;
        }
        String ip = NetUtils.bestLanAddress();
        String base = "http://" + ip + ":" + running.port();
        boolean changed = !base.equals(HostState.playerUrl);
        HostState.port = running.port();
        HostState.playerUrl = base;
        HostState.hostUrl = base + "/host";
        HostState.boardUrl = base + "/board";
        if (changed) {
            HostState.changed();
        }
        return changed;
    }

    private void acquireLocks() {
        try {
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null && wifiLock == null) {
                wifiLock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TrivialTile:wifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
            PowerManager power = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (power != null && wakeLock == null) {
                wakeLock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrivialTile:cpu");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception e) {
            Log.i(TAG, "Could not take WiFi/CPU locks: " + e);
        }
    }

    private void releaseLocks() {
        try {
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
            }
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception ignored) {
            // Best effort.
        }
        wifiLock = null;
        wakeLock = null;
    }

    private void stopHosting() {
        stopTicker();
        GameServer running = server;
        server = null;
        if (running != null) {
            running.stop();
        }
        releaseLocks();
        HostState.players = 0;
        HostState.set(HostState.Stage.STOPPED, "Hosting stopped");
    }

    @Override
    public void onDestroy() {
        stopHosting();
        instance = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ----------------------------------------------------------- notification

    private void createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Hosting",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shown while this device is hosting a trivia game");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private void startForegroundNotification(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        String title = HostState.stage == HostState.Stage.HOSTING
                ? "Hosting trivia" + (HostState.players > 0 ? " — " + HostState.players + " joined" : "")
                : HostState.statusMessage;
        manager.notify(NOTIFICATION_ID, notification(title,
                HostState.playerUrl.isEmpty() ? null : "Players: " + HostState.playerUrl));
    }

    private Notification notification(String title, String text) {
        Intent openApp = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openApp, pendingFlags());

        Intent stop = new Intent(this, HostService.class).setAction(ACTION_STOP);
        PendingIntent stopIntent = PendingIntent.getService(this, 1, stop, pendingFlags());

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setContentTitle(title)
                .setSmallIcon(R.drawable.ic_stat_trivia)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat_trivia), "Stop hosting", stopIntent).build());
        if (text != null) {
            builder.setContentText(text);
        }
        return builder.build();
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private int appVersionCode() {
        return BuildConfig.VERSION_CODE;
    }
}
