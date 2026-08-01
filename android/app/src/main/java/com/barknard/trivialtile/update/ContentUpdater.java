package com.barknard.trivialtile.update;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the hosted web app in sync with GitHub without needing git on the
 * device. It asks GitHub for the file tree of public/ on the default branch,
 * compares each entry's blob id against the local copy, and downloads only what
 * actually changed (usually just the question set).
 */
public final class ContentUpdater {

    private static final String TAG = "TrivialTile";

    public static final String REPO = "Barknard/trivial-tile-trivia";
    public static final String BRANCH = "master";
    private static final String CONTENT_PREFIX = "public/";

    public interface Progress {
        void onStatus(String message);
    }

    public static final class Result {
        public final boolean checked;
        public final int changedFiles;
        public final boolean applied;
        public final boolean pending;
        public final String commitSha;
        public final String message;

        Result(boolean checked, int changedFiles, boolean applied, boolean pending, String commitSha, String message) {
            this.checked = checked;
            this.changedFiles = changedFiles;
            this.applied = applied;
            this.pending = pending;
            this.commitSha = commitSha;
            this.message = message;
        }
    }

    private final Context context;
    private final ContentStore store;

    public ContentUpdater(Context context, ContentStore store) {
        this.context = context.getApplicationContext();
        this.store = store;
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences(ContentStore.PREFS, Context.MODE_PRIVATE);
    }

    public String currentCommit() {
        return prefs().getString(ContentStore.KEY_CONTENT_SHA, null);
    }

    public boolean hasPendingUpdate() {
        return prefs().getBoolean(ContentStore.KEY_PENDING_UPDATE, false);
    }

    /**
     * Downloads any changed files into staging.
     *
     * @param canApplyNow true when no game is running, so the update can be swapped in immediately
     */
    public Result sync(boolean canApplyNow, Progress progress) {
        try {
            status(progress, "Checking GitHub for updates…");
            String commitJson = Net.getString("https://api.github.com/repos/" + REPO + "/commits/" + BRANCH);
            String commitSha = new JSONObject(commitJson).optString("sha", "");
            if (commitSha.isEmpty()) {
                return new Result(false, 0, false, false, null, "GitHub did not return a commit");
            }
            String known = currentCommit();
            if (commitSha.equals(known) && !hasPendingUpdate()) {
                status(progress, "Up to date (" + shortSha(commitSha) + ")");
                markChecked();
                return new Result(true, 0, false, false, commitSha, "Up to date");
            }

            String treeJson = Net.getString(
                    "https://api.github.com/repos/" + REPO + "/git/trees/" + commitSha + "?recursive=1");
            JSONObject tree = new JSONObject(treeJson);
            if (tree.optBoolean("truncated", false)) {
                Log.i(TAG, "GitHub tree listing was truncated");
            }
            JSONArray entries = tree.optJSONArray("tree");
            if (entries == null) {
                return new Result(false, 0, false, false, null, "GitHub returned no file list");
            }

            Map<String, String> remote = new HashMap<>();
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.optJSONObject(i);
                if (entry == null || !"blob".equals(entry.optString("type"))) {
                    continue;
                }
                String path = entry.optString("path", "");
                if (!path.startsWith(CONTENT_PREFIX)) {
                    continue;
                }
                remote.put(path.substring(CONTENT_PREFIX.length()), entry.optString("sha", ""));
            }
            if (remote.isEmpty()) {
                return new Result(false, 0, false, false, null, "No web content found on GitHub");
            }

            Map<String, String> local = store.localManifest();
            List<String> toDownload = new ArrayList<>();
            for (Map.Entry<String, String> entry : remote.entrySet()) {
                String localSha = local.get(entry.getKey());
                if (localSha == null || !localSha.equals(entry.getValue())) {
                    toDownload.add(entry.getKey());
                }
            }
            Set<String> toDelete = new HashSet<>(local.keySet());
            toDelete.removeAll(remote.keySet());

            if (toDownload.isEmpty() && toDelete.isEmpty()) {
                // Same files, new commit id (a change elsewhere in the repo).
                prefs().edit().putString(ContentStore.KEY_CONTENT_SHA, commitSha).apply();
                status(progress, "Up to date (" + shortSha(commitSha) + ")");
                markChecked();
                return new Result(true, 0, false, false, commitSha, "Up to date");
            }

            File staging = store.stagingDir();
            ContentStore.deleteTree(staging);
            if (!staging.mkdirs()) {
                return new Result(false, 0, false, false, null, "Could not create staging folder");
            }

            int done = 0;
            for (String relative : toDownload) {
                done++;
                status(progress, "Downloading update " + done + "/" + toDownload.size() + "…");
                String url = "https://raw.githubusercontent.com/" + REPO + "/" + commitSha + "/"
                        + CONTENT_PREFIX + encodePath(relative);
                Net.download(url, new File(staging, relative));
            }
            if (!toDelete.isEmpty()) {
                StringBuilder deletions = new StringBuilder();
                for (String relative : toDelete) {
                    deletions.append(relative).append('\n');
                }
                writeText(new File(staging, ".deletions"), deletions.toString());
            }

            prefs().edit()
                    .putBoolean(ContentStore.KEY_PENDING_UPDATE, true)
                    .putString(ContentStore.KEY_STAGED_SHA, commitSha)
                    .apply();
            markChecked();

            int changed = toDownload.size() + toDelete.size();
            if (canApplyNow) {
                boolean applied = store.applyStagedUpdate();
                status(progress, applied
                        ? "Updated " + changed + " file" + (changed == 1 ? "" : "s") + " (" + shortSha(commitSha) + ")"
                        : "Update ready");
                return new Result(true, changed, applied, !applied, commitSha, "Updated");
            }
            status(progress, "Update ready — applies next launch");
            return new Result(true, changed, false, true, commitSha, "Update staged");
        } catch (Exception e) {
            Log.e(TAG, "Content update failed", e);
            status(progress, "Offline — playing the version on this device");
            return new Result(false, 0, false, hasPendingUpdate(), null, String.valueOf(e.getMessage()));
        }
    }

    private void markChecked() {
        prefs().edit().putLong(ContentStore.KEY_LAST_CHECK, System.currentTimeMillis()).apply();
    }

    private void writeText(File file, String text) throws java.io.IOException {
        try (java.io.OutputStream out = new java.io.FileOutputStream(file)) {
            out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static String encodePath(String path) {
        // Keep the slashes, escape everything else that could upset a URL.
        StringBuilder encoded = new StringBuilder(path.length() + 8);
        for (String segment : path.split("/", -1)) {
            if (encoded.length() > 0) {
                encoded.append('/');
            }
            try {
                encoded.append(java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20"));
            } catch (Exception e) {
                encoded.append(segment);
            }
        }
        return encoded.toString();
    }

    public static String shortSha(String sha) {
        return sha == null ? "?" : sha.substring(0, Math.min(7, sha.length()));
    }

    private void status(Progress progress, String message) {
        if (progress != null) {
            progress.onStatus(message);
        }
    }
}
