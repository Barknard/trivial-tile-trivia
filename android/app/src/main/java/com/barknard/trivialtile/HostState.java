package com.barknard.trivialtile;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared, observable state between the hosting service and the UI. Small enough
 * that a handful of volatile fields beats dragging in a whole architecture.
 */
public final class HostState {

    public enum Stage {
        IDLE,
        UNPACKING,
        STARTING,
        HOSTING,
        STOPPED,
        ERROR
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    public static volatile Stage stage = Stage.IDLE;
    public static volatile String statusMessage = "";
    public static volatile String playerUrl = "";
    public static volatile String hostUrl = "";
    /** Board link, carrying the live game code when there is one. */
    public static volatile String boardUrl = "";
    /** Join link for players, with the code pre-filled when there is one. */
    public static volatile String joinUrl = "";
    /** Code of the game currently being hosted, or null before one starts. */
    public static volatile String gameId = null;
    public static volatile int port = -1;
    public static volatile int players = 0;
    public static volatile String updateStatus = "";
    /** Set when a newer APK is published on GitHub. */
    public static volatile String availableAppVersion = null;
    public static volatile String availableApkUrl = null;

    private HostState() {
    }

    public static void addListener(Runnable listener) {
        LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    public static void set(Stage newStage, String message) {
        stage = newStage;
        statusMessage = message == null ? "" : message;
        changed();
    }

    public static void setUpdateStatus(String message) {
        updateStatus = message == null ? "" : message;
        changed();
    }

    public static void changed() {
        for (Runnable listener : LISTENERS) {
            MAIN.post(listener);
        }
    }
}
