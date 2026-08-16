package com.barknard.trivialtile.server;

/**
 * Tiny logging shim so the server package stays free of Android imports and can
 * run (and be tested) on a desktop JVM.
 */
public final class Slog {

    public interface Sink {
        void log(String tag, String message, Throwable error);
    }

    private static volatile Sink sink = (tag, message, error) -> {
        System.out.println("[" + tag + "] " + message);
        if (error != null) {
            error.printStackTrace(System.out);
        }
    };

    private Slog() {
    }

    public static void setSink(Sink newSink) {
        sink = newSink;
    }

    public static void i(String tag, String message) {
        Sink s = sink;
        if (s != null) {
            s.log(tag, message, null);
        }
    }

    public static void e(String tag, String message, Throwable error) {
        Sink s = sink;
        if (s != null) {
            s.log(tag, message, error);
        }
    }
}
