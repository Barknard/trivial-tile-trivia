package com.barknard.trivialtile.server;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single RFC 6455 WebSocket connection, server side. Handles frame decoding
 * (including fragmentation), control frames, and thread-safe sends.
 */
public final class WsConnection {

    public interface Listener {
        void onOpen(WsConnection connection);

        void onMessage(WsConnection connection, String text);

        void onClose(WsConnection connection);
    }

    private static final String TAG = "ws";

    private static final int OP_CONTINUATION = 0x0;
    private static final int OP_TEXT = 0x1;
    private static final int OP_BINARY = 0x2;
    private static final int OP_CLOSE = 0x8;
    private static final int OP_PING = 0x9;
    private static final int OP_PONG = 0xA;

    /** Anything bigger than this is a bug or an attack; drop the connection. */
    private static final int MAX_MESSAGE_BYTES = 8 * 1024 * 1024;

    /** Socket read timeout; each expiry sends a ping to check the peer is alive. */
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int MAX_SILENT_INTERVALS = 3;

    private static final AtomicLong ID_SEQ = new AtomicLong(1);

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;
    private final Listener listener;
    private final Object writeLock = new Object();
    private final String id;

    private volatile boolean open = true;
    private volatile boolean closeNotified = false;

    /** Free-form slot the relay uses to hang per-connection room state off. */
    public volatile Object attachment;

    public WsConnection(Socket socket, InputStream in, OutputStream out, Listener listener) {
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.listener = listener;
        this.id = "ws-" + ID_SEQ.getAndIncrement();
    }

    public String id() {
        return id;
    }

    public boolean isOpen() {
        return open && !socket.isClosed();
    }

    /** Blocking read loop; runs on the connection's own thread until the peer goes away. */
    public void readLoop() {
        try {
            socket.setSoTimeout(READ_TIMEOUT_MS);
        } catch (IOException ignored) {
            // Non-fatal: we just lose the liveness check.
        }
        listener.onOpen(this);
        int silentIntervals = 0;
        ByteArrayOutputStream fragments = null;
        int fragmentOpcode = -1;
        try {
            while (isOpen()) {
                Frame frame;
                try {
                    frame = readFrame();
                } catch (SocketTimeoutException timeout) {
                    silentIntervals++;
                    if (silentIntervals >= MAX_SILENT_INTERVALS) {
                        Slog.i(TAG, id + " idle with no response, closing");
                        break;
                    }
                    sendControl(OP_PING, new byte[0]);
                    continue;
                }
                if (frame == null) {
                    break;
                }
                silentIntervals = 0;

                switch (frame.opcode) {
                    case OP_PING:
                        sendControl(OP_PONG, frame.payload);
                        break;
                    case OP_PONG:
                        break;
                    case OP_CLOSE:
                        sendControl(OP_CLOSE, new byte[0]);
                        return;
                    case OP_TEXT:
                    case OP_BINARY:
                        if (fragments != null) {
                            Slog.i(TAG, id + " sent a new message before finishing the previous one");
                            return;
                        }
                        if (frame.fin) {
                            deliver(frame.opcode, frame.payload);
                        } else {
                            fragmentOpcode = frame.opcode;
                            fragments = new ByteArrayOutputStream();
                            fragments.write(frame.payload);
                        }
                        break;
                    case OP_CONTINUATION:
                        if (fragments == null) {
                            Slog.i(TAG, id + " sent a continuation with nothing to continue");
                            return;
                        }
                        if (fragments.size() + frame.payload.length > MAX_MESSAGE_BYTES) {
                            Slog.i(TAG, id + " message too large, closing");
                            return;
                        }
                        fragments.write(frame.payload);
                        if (frame.fin) {
                            deliver(fragmentOpcode, fragments.toByteArray());
                            fragments = null;
                            fragmentOpcode = -1;
                        }
                        break;
                    default:
                        Slog.i(TAG, id + " sent unknown opcode " + frame.opcode);
                        return;
                }
            }
        } catch (EOFException eof) {
            // Peer hung up - normal.
        } catch (IOException e) {
            if (open) {
                Slog.i(TAG, id + " read ended: " + e);
            }
        } finally {
            close();
        }
    }

    private void deliver(int opcode, byte[] payload) {
        if (opcode != OP_TEXT) {
            // The game protocol is JSON text only.
            return;
        }
        listener.onMessage(this, new String(payload, StandardCharsets.UTF_8));
    }

    public void sendText(String text) {
        if (!isOpen()) {
            return;
        }
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        try {
            synchronized (writeLock) {
                writeFrameHeader(OP_TEXT, payload.length);
                out.write(payload);
                out.flush();
            }
        } catch (IOException e) {
            Slog.i(TAG, id + " send failed: " + e);
            close();
        }
    }

    private void sendControl(int opcode, byte[] payload) {
        if (!open) {
            return;
        }
        byte[] body = payload.length > 125 ? new byte[0] : payload;
        try {
            synchronized (writeLock) {
                out.write(0x80 | opcode);
                out.write(body.length);
                out.write(body);
                out.flush();
            }
        } catch (IOException e) {
            close();
        }
    }

    private void writeFrameHeader(int opcode, int length) throws IOException {
        out.write(0x80 | opcode);
        if (length < 126) {
            out.write(length);
        } else if (length < 65536) {
            out.write(126);
            out.write((length >>> 8) & 0xFF);
            out.write(length & 0xFF);
        } else {
            out.write(127);
            long len = length;
            for (int shift = 56; shift >= 0; shift -= 8) {
                out.write((int) ((len >>> shift) & 0xFF));
            }
        }
    }

    public void close() {
        boolean wasOpen = open;
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already gone.
        }
        if (wasOpen && !closeNotified) {
            closeNotified = true;
            listener.onClose(this);
        }
    }

    private Frame readFrame() throws IOException {
        int b0 = in.read();
        if (b0 < 0) {
            return null;
        }
        int b1 = readByte();
        boolean fin = (b0 & 0x80) != 0;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long length = b1 & 0x7F;
        if (length == 126) {
            length = (readByte() << 8) | readByte();
        } else if (length == 127) {
            length = 0;
            for (int i = 0; i < 8; i++) {
                length = (length << 8) | readByte();
            }
        }
        if (length < 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException("frame too large: " + length);
        }
        if (!masked) {
            // Browsers always mask; an unmasked client frame is a protocol error.
            throw new IOException("client frame was not masked");
        }
        byte[] mask = new byte[4];
        readFully(mask);
        byte[] payload = new byte[(int) length];
        readFully(payload);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (payload[i] ^ mask[i & 3]);
        }
        return new Frame(fin, opcode, payload);
    }

    // Only a timeout at a frame boundary means "idle peer" - once we are part way
    // through a frame the stream can't be resynchronised, so it becomes fatal.
    private int readByte() throws IOException {
        int value;
        try {
            value = in.read();
        } catch (SocketTimeoutException timeout) {
            throw new IOException("timed out mid-frame", timeout);
        }
        if (value < 0) {
            throw new EOFException("socket closed mid-frame");
        }
        return value;
    }

    private void readFully(byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read;
            try {
                read = in.read(buffer, offset, buffer.length - offset);
            } catch (SocketTimeoutException timeout) {
                throw new IOException("timed out mid-frame", timeout);
            }
            if (read < 0) {
                throw new EOFException("socket closed mid-frame");
            }
            offset += read;
        }
    }

    private static final class Frame {
        final boolean fin;
        final int opcode;
        final byte[] payload;

        Frame(boolean fin, int opcode, byte[] payload) {
            this.fin = fin;
            this.opcode = opcode;
            this.payload = payload;
        }
    }
}
