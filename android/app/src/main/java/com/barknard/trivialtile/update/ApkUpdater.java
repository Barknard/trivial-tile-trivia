package com.barknard.trivialtile.update;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks GitHub Releases for a newer build of this app and installs it through
 * the system package installer. Content updates happen silently; a new APK
 * always needs the user to tap "Install", which Android enforces anyway.
 */
public final class ApkUpdater {

    private static final String TAG = "TrivialTile";
    private static final Pattern TRAILING_NUMBER = Pattern.compile("(\\d+)\\s*$");

    public static final class Release {
        public final int versionCode;
        public final String name;
        public final String apkUrl;

        Release(int versionCode, String name, String apkUrl) {
            this.versionCode = versionCode;
            this.name = name;
            this.apkUrl = apkUrl;
        }
    }

    private final Context context;

    public ApkUpdater(Context context) {
        this.context = context.getApplicationContext();
    }

    /** @return a release newer than {@code currentVersionCode}, or null */
    public Release findNewerRelease(int currentVersionCode) {
        try {
            String json = Net.getString("https://api.github.com/repos/" + ContentUpdater.REPO + "/releases/latest");
            JSONObject release = new JSONObject(json);
            String tag = release.optString("tag_name", "");
            int versionCode = parseVersionCode(tag);
            if (versionCode <= currentVersionCode) {
                return null;
            }
            JSONArray assets = release.optJSONArray("assets");
            if (assets == null) {
                return null;
            }
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.optJSONObject(i);
                if (asset == null) {
                    continue;
                }
                String name = asset.optString("name", "");
                if (name.toLowerCase().endsWith(".apk")) {
                    return new Release(versionCode, release.optString("name", tag),
                            asset.optString("browser_download_url", ""));
                }
            }
        } catch (Exception e) {
            Log.i(TAG, "App update check failed: " + e);
        }
        return null;
    }

    private static int parseVersionCode(String tag) {
        Matcher matcher = TRAILING_NUMBER.matcher(tag);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    public boolean canInstall() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return context.getPackageManager().canRequestPackageInstalls();
    }

    /** Downloads the APK and hands it to the system installer. */
    public void downloadAndInstall(Release release, ContentUpdater.Progress progress) {
        try {
            if (progress != null) {
                progress.onStatus("Downloading app update…");
            }
            File apk = new File(context.getCacheDir(), "update.apk");
            Net.download(release.apkUrl, apk);
            if (progress != null) {
                progress.onStatus("Starting installer…");
            }
            install(apk);
        } catch (Exception e) {
            Log.e(TAG, "App update failed", e);
            if (progress != null) {
                progress.onStatus("App update failed: " + e.getMessage());
            }
        }
    }

    private void install(File apk) throws IOException {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params =
                new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(context.getPackageName());
        int sessionId = installer.createSession(params);
        try (PackageInstaller.Session session = installer.openSession(sessionId)) {
            try (OutputStream out = session.openWrite("app", 0, apk.length());
                 InputStream in = new FileInputStream(apk)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, read);
                }
                session.fsync(out);
            }
            Intent intent = new Intent(context, InstallReceiver.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags |= PendingIntent.FLAG_MUTABLE;
            }
            PendingIntent pending = PendingIntent.getBroadcast(context, sessionId, intent, flags);
            session.commit(pending.getIntentSender());
        }
    }
}
