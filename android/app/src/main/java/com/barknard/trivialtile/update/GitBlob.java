package com.barknard.trivialtile.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Git's object id for a file: sha1("blob &lt;length&gt;\0" + contents).
 *
 * <p>Computing this locally is what lets the app diff its own copy of the game
 * against a GitHub tree listing and download only the files that really changed
 * - no manifest has to be shipped with the content. Kept free of Android
 * imports so it can be checked against {@code git hash-object} on a desktop.
 */
public final class GitBlob {

    private GitBlob() {
    }

    public static String sha(File file) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(("blob " + file.length() + "\0").getBytes(StandardCharsets.UTF_8));
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                sha1.update(buffer, 0, read);
            }
        }
        byte[] digest = sha1.digest();
        StringBuilder hex = new StringBuilder(40);
        for (byte b : digest) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
