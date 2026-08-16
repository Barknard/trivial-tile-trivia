package com.barknard.trivialtile.update;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Minimal HTTP helpers for talking to GitHub. */
public final class Net {

    private static final String USER_AGENT = "TrivialTileTrivia-Android";
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private Net() {
    }

    public static String getString(String url) throws IOException {
        HttpURLConnection connection = open(url, "application/vnd.github+json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("HTTP " + status + " for " + url);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            copy(connection.getInputStream(), buffer);
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    public static void download(String url, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        HttpURLConnection connection = open(url, "*/*");
        try {
            int status = connection.getResponseCode();
            if (status != 200) {
                throw new IOException("HTTP " + status + " for " + url);
            }
            File temp = new File(destination.getPath() + ".part");
            try (OutputStream out = new FileOutputStream(temp)) {
                copy(connection.getInputStream(), out);
            }
            if (destination.exists() && !destination.delete()) {
                throw new IOException("Could not replace " + destination);
            }
            if (!temp.renameTo(destination)) {
                throw new IOException("Could not move " + temp + " into place");
            }
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String accept) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", accept);
        return connection;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        out.flush();
    }
}
