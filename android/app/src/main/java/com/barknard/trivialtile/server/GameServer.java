package com.barknard.trivialtile.server;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The whole game server: static hosting for the web app, the handful of /api
 * endpoints it calls, and the /ws relay. Pure Java on purpose - no Android
 * imports - so it can be run and tested on a desktop JVM too.
 */
public final class GameServer {

    private static final String TAG = "http";

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int HTTP_IDLE_TIMEOUT_MS = 30_000;
    private static final int COPY_BUFFER = 64 * 1024;

    private static final Map<String, String> MIME_TYPES = new HashMap<>();

    static {
        MIME_TYPES.put("html", "text/html; charset=utf-8");
        MIME_TYPES.put("htm", "text/html; charset=utf-8");
        MIME_TYPES.put("js", "text/javascript; charset=utf-8");
        MIME_TYPES.put("mjs", "text/javascript; charset=utf-8");
        MIME_TYPES.put("css", "text/css; charset=utf-8");
        MIME_TYPES.put("json", "application/json; charset=utf-8");
        MIME_TYPES.put("webmanifest", "application/manifest+json; charset=utf-8");
        MIME_TYPES.put("map", "application/json; charset=utf-8");
        MIME_TYPES.put("txt", "text/plain; charset=utf-8");
        MIME_TYPES.put("svg", "image/svg+xml");
        MIME_TYPES.put("png", "image/png");
        MIME_TYPES.put("jpg", "image/jpeg");
        MIME_TYPES.put("jpeg", "image/jpeg");
        MIME_TYPES.put("gif", "image/gif");
        MIME_TYPES.put("webp", "image/webp");
        MIME_TYPES.put("ico", "image/x-icon");
        MIME_TYPES.put("mp3", "audio/mpeg");
        MIME_TYPES.put("wav", "audio/wav");
        MIME_TYPES.put("ogg", "audio/ogg");
        MIME_TYPES.put("m4a", "audio/mp4");
        MIME_TYPES.put("mp4", "video/mp4");
        MIME_TYPES.put("woff", "font/woff");
        MIME_TYPES.put("woff2", "font/woff2");
        MIME_TYPES.put("ttf", "font/ttf");
    }

    private final RelayHub hub = new RelayHub();
    private final AtomicInteger liveConnections = new AtomicInteger();
    private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "trivia-conn");
        thread.setDaemon(true);
        return thread;
    });

    private volatile File webRoot;
    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile int port = -1;
    private volatile boolean running = false;

    public GameServer(File webRoot) {
        this.webRoot = webRoot;
    }

    public RelayHub hub() {
        return hub;
    }

    public int port() {
        return port;
    }

    public boolean isRunning() {
        return running;
    }

    public File webRoot() {
        return webRoot;
    }

    /** Swap the directory being served (used after a content update lands). */
    public void setWebRoot(File newRoot) {
        this.webRoot = newRoot;
    }

    /**
     * Binds the first free port starting at {@code preferredPort}.
     *
     * @return the port actually bound
     */
    public synchronized int start(int preferredPort, int attempts) throws IOException {
        if (running) {
            return port;
        }
        IOException last = null;
        for (int candidate = preferredPort; candidate < preferredPort + Math.max(1, attempts); candidate++) {
            try {
                ServerSocket socket = new ServerSocket();
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress("0.0.0.0", candidate), 64);
                serverSocket = socket;
                port = candidate;
                break;
            } catch (IOException e) {
                last = e;
            }
        }
        if (serverSocket == null) {
            throw last != null ? last : new IOException("Could not bind a port");
        }
        running = true;
        Thread thread = new Thread(this::acceptLoop, "trivia-accept");
        thread.setDaemon(true);
        acceptThread = thread;
        thread.start();
        Slog.i(TAG, "serving on port " + port);
        return port;
    }

    public synchronized void stop() {
        running = false;
        ServerSocket socket = serverSocket;
        serverSocket = null;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
        Thread thread = acceptThread;
        if (thread != null) {
            thread.interrupt();
        }
        acceptThread = null;
        port = -1;
    }

    private void acceptLoop() {
        ServerSocket socket = serverSocket;
        while (running && socket != null && !socket.isClosed()) {
            try {
                Socket client = socket.accept();
                client.setTcpNoDelay(true);
                workers.execute(() -> handleConnection(client));
            } catch (IOException e) {
                if (running) {
                    Slog.i(TAG, "accept failed: " + e);
                }
            }
        }
    }

    private void handleConnection(Socket socket) {
        liveConnections.incrementAndGet();
        try {
            socket.setSoTimeout(HTTP_IDLE_TIMEOUT_MS);
            InputStream in = new BufferedInputStream(socket.getInputStream(), 16 * 1024);
            OutputStream out = new BufferedOutputStream(socket.getOutputStream(), COPY_BUFFER);
            while (running && !socket.isClosed()) {
                Request request;
                try {
                    request = Request.read(in);
                } catch (SocketTimeoutException timeout) {
                    return;
                }
                if (request == null) {
                    return;
                }
                if (request.isWebSocketUpgrade() && "/ws".equals(request.path)) {
                    upgradeToWebSocket(socket, in, out, request);
                    return;
                }
                boolean keepAlive = request.wantsKeepAlive();
                serve(request, out, keepAlive);
                out.flush();
                if (!keepAlive) {
                    return;
                }
            }
        } catch (IOException e) {
            // Client vanished mid-request; nothing useful to do.
        } finally {
            liveConnections.decrementAndGet();
            try {
                socket.close();
            } catch (IOException ignored) {
                // Already closed.
            }
        }
    }

    private void upgradeToWebSocket(Socket socket, InputStream in, OutputStream out, Request request)
            throws IOException {
        String key = request.header("sec-websocket-key");
        if (key == null) {
            writeSimple(out, 400, "Bad Request", "text/plain; charset=utf-8",
                    "Missing Sec-WebSocket-Key".getBytes(StandardCharsets.UTF_8), true);
            out.flush();
            return;
        }
        String accept;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest((key.trim() + WS_GUID).getBytes(StandardCharsets.UTF_8));
            accept = Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IOException("SHA-1 unavailable", e);
        }
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 101 Switching Protocols\r\n")
                .append("Upgrade: websocket\r\n")
                .append("Connection: Upgrade\r\n")
                .append("Sec-WebSocket-Accept: ").append(accept).append("\r\n\r\n");
        out.write(response.toString().getBytes(StandardCharsets.US_ASCII));
        out.flush();

        WsConnection connection = new WsConnection(socket, in, out, hub);
        connection.readLoop();
    }

    // ---------------------------------------------------------------- routing

    private void serve(Request request, OutputStream out, boolean keepAlive) throws IOException {
        String path = request.path;
        if (path.startsWith("/api/")) {
            serveApi(request, path, out, keepAlive);
            return;
        }
        serveStatic(request, path, out, keepAlive);
    }

    private void serveApi(Request request, String path, OutputStream out, boolean keepAlive) throws IOException {
        try {
            if (path.equals("/api/server-ip")) {
                List<String> candidates = NetUtils.localIpv4Addresses();
                String ip = NetUtils.bestLanAddress();
                Slog.i("info", "Server IP detected: " + ip + " (candidates: " + String.join(", ", candidates) + ")");
                writeJson(out, 200, new JSONObject().put("ip", ip), keepAlive);
                return;
            }
            if (path.equals("/api/custom-buzzers")) {
                writeJson(out, 200, new JSONObject().put("buzzers", new JSONArray(soundsWithPrefix("buzzer-"))), keepAlive);
                return;
            }
            if (path.equals("/api/music-tracks")) {
                JSONArray tracks = new JSONArray();
                for (String file : soundFiles()) {
                    String lower = file.toLowerCase(Locale.US);
                    if (!lower.startsWith("music-")) {
                        continue;
                    }
                    String id = stripExtension(file).substring("music-".length());
                    String name = id.replace('_', ' ');
                    if (!name.isEmpty()) {
                        name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
                    }
                    tracks.put(new JSONObject().put("id", id).put("name", name).put("file", file));
                }
                writeJson(out, 200, new JSONObject().put("tracks", tracks), keepAlive);
                return;
            }
            if (path.startsWith("/api/room/")) {
                String gameId = path.substring("/api/room/".length());
                writeJson(out, 200, new JSONObject().put("exists", hub.hasRoom(gameId)), keepAlive);
                return;
            }
            if (path.equals("/api/status")) {
                // Also what the app reads to build board and join links.
                String active = hub.activeGameId();
                writeJson(out, 200, new JSONObject()
                        .put("ok", true)
                        .put("rooms", hub.roomCount())
                        .put("players", hub.playerCount())
                        .put("gameId", active == null ? JSONObject.NULL : active), keepAlive);
                return;
            }
        } catch (Exception e) {
            Slog.e("error", "API error on " + path, e);
            writeSimple(out, 500, "Internal Server Error", "application/json; charset=utf-8",
                    "{\"message\":\"Internal Server Error\"}".getBytes(StandardCharsets.UTF_8), keepAlive);
            return;
        }
        writeSimple(out, 404, "Not Found", "application/json; charset=utf-8",
                "{\"message\":\"Not Found\"}".getBytes(StandardCharsets.UTF_8), keepAlive);
    }

    private void serveStatic(Request request, String path, OutputStream out, boolean keepAlive) throws IOException {
        File root = webRoot;
        if (root == null || !root.isDirectory()) {
            writeSimple(out, 503, "Service Unavailable", "text/plain; charset=utf-8",
                    "Game files are still being unpacked - try again in a moment.".getBytes(StandardCharsets.UTF_8),
                    keepAlive);
            return;
        }
        File file = resolve(root, path);
        if (file == null || !file.isFile()) {
            // Client-side routes (/host, /board, /player) fall through to the SPA.
            File index = new File(root, "index.html");
            if (!index.isFile()) {
                writeSimple(out, 404, "Not Found", "text/plain; charset=utf-8",
                        "Not Found".getBytes(StandardCharsets.UTF_8), keepAlive);
                return;
            }
            file = index;
        }
        sendFile(request, file, out, keepAlive);
    }

    private void sendFile(Request request, File file, OutputStream out, boolean keepAlive) throws IOException {
        String name = file.getName().toLowerCase(Locale.US);
        String contentType = MIME_TYPES.getOrDefault(extension(name), "application/octet-stream");
        long length = file.length();

        long start = 0;
        long end = length - 1;
        boolean partial = false;
        String range = request.header("range");
        if (range != null && range.startsWith("bytes=") && length > 0) {
            String spec = range.substring("bytes=".length()).trim();
            int dash = spec.indexOf('-');
            if (dash >= 0) {
                try {
                    String from = spec.substring(0, dash).trim();
                    String to = spec.substring(dash + 1).trim();
                    if (from.isEmpty()) {
                        long suffix = Long.parseLong(to);
                        start = Math.max(0, length - suffix);
                    } else {
                        start = Long.parseLong(from);
                        if (!to.isEmpty()) {
                            end = Math.min(end, Long.parseLong(to));
                        }
                    }
                    partial = start <= end && start < length;
                } catch (NumberFormatException ignored) {
                    partial = false;
                }
            }
            if (!partial) {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Range", "bytes */" + length);
                writeHeaders(out, 416, "Range Not Satisfiable", contentType, 0, headers, keepAlive);
                return;
            }
        }

        long contentLength = end - start + 1;
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Ranges", "bytes");
        headers.put("Cache-Control", cacheControlFor(request.path, name));
        if (partial) {
            headers.put("Content-Range", "bytes " + start + "-" + end + "/" + length);
        }
        writeHeaders(out, partial ? 206 : 200, partial ? "Partial Content" : "OK", contentType, contentLength,
                headers, keepAlive);
        if ("HEAD".equals(request.method)) {
            return;
        }
        try (FileInputStream fileIn = new FileInputStream(file)) {
            if (start > 0) {
                long skipped = 0;
                while (skipped < start) {
                    long step = fileIn.skip(start - skipped);
                    if (step <= 0) {
                        break;
                    }
                    skipped += step;
                }
            }
            byte[] buffer = new byte[COPY_BUFFER];
            long remaining = contentLength;
            while (remaining > 0) {
                int wanted = (int) Math.min(buffer.length, remaining);
                int read = fileIn.read(buffer, 0, wanted);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private String cacheControlFor(String path, String name) {
        // Hashed build assets are immutable; everything else has to stay fresh so
        // an updated question set or UI shows up right away.
        if (path.startsWith("/assets/") && (name.endsWith(".js") || name.endsWith(".css"))) {
            return "public, max-age=31536000, immutable";
        }
        if (path.startsWith("/sounds/")) {
            return "public, max-age=86400";
        }
        return "no-cache, no-store, must-revalidate";
    }

    private File resolve(File root, String path) {
        if (path.isEmpty() || path.equals("/")) {
            return new File(root, "index.html");
        }
        String relative = path.startsWith("/") ? path.substring(1) : path;
        if (relative.isEmpty()) {
            return new File(root, "index.html");
        }
        File candidate = new File(root, relative);
        try {
            String rootPath = root.getCanonicalPath() + File.separator;
            String filePath = candidate.getCanonicalFile().getPath();
            if (!(filePath + File.separator).startsWith(rootPath)) {
                return null;
            }
        } catch (IOException e) {
            return null;
        }
        return candidate;
    }

    private List<String> soundFiles() {
        File root = webRoot;
        if (root == null) {
            return new ArrayList<>();
        }
        File dir = new File(root, "sounds");
        String[] names = dir.isDirectory() ? dir.list() : null;
        if (names == null) {
            return new ArrayList<>();
        }
        List<String> files = new ArrayList<>();
        for (String name : names) {
            String lower = name.toLowerCase(Locale.US);
            if (lower.endsWith(".wav") || lower.endsWith(".mp3")) {
                files.add(name);
            }
        }
        files.sort(String::compareToIgnoreCase);
        return files;
    }

    private List<String> soundsWithPrefix(String prefix) {
        Set<String> unique = new LinkedHashSet<>();
        for (String file : soundFiles()) {
            if (file.toLowerCase(Locale.US).startsWith(prefix)) {
                unique.add(stripExtension(file).substring(prefix.length()));
            }
        }
        return new ArrayList<>(unique);
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    // ---------------------------------------------------------------- writing

    private void writeJson(OutputStream out, int status, JSONObject body, boolean keepAlive) throws IOException {
        writeSimple(out, status, status == 200 ? "OK" : "Error", "application/json; charset=utf-8",
                body.toString().getBytes(StandardCharsets.UTF_8), keepAlive);
    }

    private void writeSimple(OutputStream out, int status, String reason, String contentType, byte[] body,
                             boolean keepAlive) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
        writeHeaders(out, status, reason, contentType, body.length, headers, keepAlive);
        out.write(body);
    }

    private void writeHeaders(OutputStream out, int status, String reason, String contentType, long contentLength,
                              Map<String, String> extra, boolean keepAlive) throws IOException {
        StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(status).append(' ').append(reason).append("\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(contentLength).append("\r\n");
        head.append("Connection: ").append(keepAlive ? "keep-alive" : "close").append("\r\n");
        // Everything is same-origin, but players sometimes land on the board over a
        // different host name; keep this permissive like the old Express server.
        head.append("Access-Control-Allow-Origin: *\r\n");
        if (extra != null) {
            for (Map.Entry<String, String> entry : extra.entrySet()) {
                head.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
            }
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
    }

    // ---------------------------------------------------------------- request

    private static final class Request {
        String method = "GET";
        String path = "/";
        String query = "";
        String version = "HTTP/1.1";
        final Map<String, String> headers = new HashMap<>();

        String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }

        boolean isWebSocketUpgrade() {
            String upgrade = header("upgrade");
            String connection = header("connection");
            return upgrade != null && upgrade.toLowerCase(Locale.US).contains("websocket")
                    && connection != null && connection.toLowerCase(Locale.US).contains("upgrade");
        }

        boolean wantsKeepAlive() {
            String connection = header("connection");
            if (connection == null) {
                return version.endsWith("1.1");
            }
            String lower = connection.toLowerCase(Locale.US);
            if (lower.contains("close")) {
                return false;
            }
            return version.endsWith("1.1") || lower.contains("keep-alive");
        }

        static Request read(InputStream in) throws IOException {
            String requestLine = readLine(in);
            if (requestLine == null) {
                return null;
            }
            while (requestLine.isEmpty()) {
                requestLine = readLine(in);
                if (requestLine == null) {
                    return null;
                }
            }
            Request request = new Request();
            String[] parts = requestLine.split(" ");
            if (parts.length >= 1) {
                request.method = parts[0].toUpperCase(Locale.US);
            }
            String target = parts.length >= 2 ? parts[1] : "/";
            if (parts.length >= 3) {
                request.version = parts[2];
            }
            int questionMark = target.indexOf('?');
            if (questionMark >= 0) {
                request.query = target.substring(questionMark + 1);
                target = target.substring(0, questionMark);
            }
            request.path = decodePath(target);

            String line;
            int headerCount = 0;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                if (++headerCount > 100) {
                    throw new IOException("too many headers");
                }
                int colon = line.indexOf(':');
                if (colon > 0) {
                    request.headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                            line.substring(colon + 1).trim());
                }
            }

            // The web client never posts, but drain any body so a keep-alive
            // connection stays in sync.
            String lengthHeader = request.header("content-length");
            if (lengthHeader != null) {
                try {
                    long remaining = Long.parseLong(lengthHeader.trim());
                    byte[] sink = new byte[8192];
                    while (remaining > 0) {
                        int read = in.read(sink, 0, (int) Math.min(sink.length, remaining));
                        if (read < 0) {
                            break;
                        }
                        remaining -= read;
                    }
                } catch (NumberFormatException ignored) {
                    // Malformed; nothing to drain.
                }
            }
            return request;
        }

        private static String decodePath(String target) {
            String decoded;
            try {
                decoded = URLDecoder.decode(target, StandardCharsets.UTF_8.name());
            } catch (Exception e) {
                decoded = target;
            }
            if (!decoded.startsWith("/")) {
                decoded = "/" + decoded;
            }
            return decoded;
        }

        private static String readLine(InputStream in) throws IOException {
            StringBuilder line = new StringBuilder(128);
            int value;
            while ((value = in.read()) >= 0) {
                if (value == '\n') {
                    int end = line.length();
                    if (end > 0 && line.charAt(end - 1) == '\r') {
                        line.setLength(end - 1);
                    }
                    return line.toString();
                }
                if (line.length() > 8192) {
                    throw new IOException("request line too long");
                }
                line.append((char) value);
            }
            return line.length() == 0 ? null : line.toString();
        }
    }
}
