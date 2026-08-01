package com.barknard.trivialtile.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the game's web content on disk: unpacks the copy bundled in the APK on
 * first run, and applies content downloaded from GitHub afterwards.
 */
public final class ContentStore {

    private static final String TAG = "TrivialTile";
    public static final String PREFS = "trivia";
    private static final String KEY_BUNDLED_VERSION = "bundled_version";
    public static final String KEY_CONTENT_SHA = "content_sha";
    /** Commit of a download that is waiting in staging, promoted once applied. */
    public static final String KEY_STAGED_SHA = "staged_sha";
    public static final String KEY_PENDING_UPDATE = "pending_update";
    public static final String KEY_LAST_CHECK = "last_check";

    /** Assets are packed under this folder inside the APK. */
    private static final String ASSET_ROOT = "web";

    public interface ProgressListener {
        void onProgress(int filesDone, int filesTotal);
    }

    private final Context context;

    public ContentStore(Context context) {
        this.context = context.getApplicationContext();
    }

    public File liveDir() {
        return new File(context.getFilesDir(), "web");
    }

    public File stagingDir() {
        return new File(context.getFilesDir(), "web-staging");
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** True when the APK carries content the unpacked copy doesn't have yet. */
    public boolean needsUnpack(int currentVersionCode) {
        File index = new File(liveDir(), "index.html");
        if (!index.isFile()) {
            return true;
        }
        return prefs().getInt(KEY_BUNDLED_VERSION, -1) != currentVersionCode;
    }

    /**
     * Copies assets/web out of the APK into internal storage. Anything already
     * present with the same size is left alone so upgrades are quick.
     */
    public void unpackBundledContent(int currentVersionCode, ProgressListener listener) throws IOException {
        AssetManager assets = context.getAssets();
        List<String> files = new ArrayList<>();
        listAssets(assets, ASSET_ROOT, files);
        if (files.isEmpty()) {
            throw new IOException("No web content bundled in the app");
        }
        File root = liveDir();
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IOException("Could not create " + root);
        }
        int done = 0;
        for (String assetPath : files) {
            String relative = assetPath.substring(ASSET_ROOT.length() + 1);
            File destination = new File(root, relative);
            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Could not create " + parent);
            }
            copyAsset(assets, assetPath, destination);
            done++;
            if (listener != null && (done % 5 == 0 || done == files.size())) {
                listener.onProgress(done, files.size());
            }
        }
        // The bundled copy is a fresh baseline: anything staged against the old
        // content could be older than what just shipped in the APK.
        deleteTree(stagingDir());
        prefs().edit()
                .putInt(KEY_BUNDLED_VERSION, currentVersionCode)
                .remove(KEY_CONTENT_SHA)
                .remove(KEY_STAGED_SHA)
                .putBoolean(KEY_PENDING_UPDATE, false)
                .apply();
        Log.i(TAG, "Unpacked " + files.size() + " bundled files into " + root);
    }

    private void copyAsset(AssetManager assets, String assetPath, File destination) throws IOException {
        try (InputStream in = assets.open(assetPath);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    private void listAssets(AssetManager assets, String path, List<String> out) throws IOException {
        String[] children = assets.list(path);
        if (children == null || children.length == 0) {
            out.add(path);
            return;
        }
        for (String child : children) {
            listAssets(assets, path + "/" + child, out);
        }
    }

    /**
     * Moves a downloaded update into the live directory.
     *
     * @return true when something was applied
     */
    public boolean applyStagedUpdate() {
        File staging = stagingDir();
        if (!staging.isDirectory()) {
            return false;
        }
        File live = liveDir();
        boolean changed = false;
        boolean failed = false;
        try {
            File deletions = new File(staging, ".deletions");
            if (deletions.isFile()) {
                for (String relative : readLines(deletions)) {
                    File target = new File(live, relative);
                    if (target.isFile() && target.delete()) {
                        changed = true;
                    }
                }
                //noinspection ResultOfMethodCallIgnored
                deletions.delete();
            }
            changed |= moveTree(staging, live);
        } catch (IOException e) {
            failed = true;
            Log.e(TAG, "Applying staged update failed", e);
        }
        SharedPreferences.Editor edit = prefs().edit();
        if (failed) {
            // Leave the download in place and try again next launch.
            edit.apply();
            return changed;
        }
        deleteTree(staging);
        edit.putBoolean(KEY_PENDING_UPDATE, false);
        // The commit is only "installed" once its files are actually in place.
        String staged = prefs().getString(KEY_STAGED_SHA, null);
        if (staged != null) {
            edit.putString(KEY_CONTENT_SHA, staged).remove(KEY_STAGED_SHA);
        }
        edit.apply();
        if (changed) {
            Log.i(TAG, "Applied staged content update");
        }
        return changed;
    }

    private boolean moveTree(File from, File to) throws IOException {
        File[] entries = from.listFiles();
        if (entries == null) {
            return false;
        }
        boolean changed = false;
        for (File entry : entries) {
            if (entry.getName().startsWith(".")) {
                continue;
            }
            File target = new File(to, entry.getName());
            if (entry.isDirectory()) {
                if (!target.isDirectory() && !target.mkdirs()) {
                    throw new IOException("Could not create " + target);
                }
                changed |= moveTree(entry, target);
            } else {
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("Could not create " + parent);
                }
                if (target.exists() && !target.delete()) {
                    throw new IOException("Could not replace " + target);
                }
                if (!entry.renameTo(target)) {
                    copyFile(entry, target);
                }
                changed = true;
            }
        }
        return changed;
    }

    private void copyFile(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
        }
    }

    public static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private List<String> readLines(File file) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream in = new FileInputStream(file)) {
            java.io.BufferedReader reader =
                    new java.io.BufferedReader(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    lines.add(line.trim());
                }
            }
        }
        return lines;
    }

    /** Relative path -> git blob sha for everything currently served. */
    public Map<String, String> localManifest() {
        Map<String, String> manifest = new HashMap<>();
        collect(liveDir(), "", manifest);
        return manifest;
    }

    private void collect(File dir, String prefix, Map<String, String> out) {
        File[] entries = dir.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            String relative = prefix.isEmpty() ? entry.getName() : prefix + "/" + entry.getName();
            if (entry.isDirectory()) {
                collect(entry, relative, out);
            } else {
                String sha = gitBlobSha(entry);
                if (sha != null) {
                    out.put(relative, sha);
                }
            }
        }
    }

    public static String gitBlobSha(File file) {
        try {
            return GitBlob.sha(file);
        } catch (Exception e) {
            Log.e(TAG, "Could not hash " + file, e);
            return null;
        }
    }
}
