package com.barknard.trivialtile.server;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The game relay: hosts create rooms, players join them, and messages are
 * forwarded between them. This is a straight port of the old Node
 * server/routes.ts WebSocket handler - the wire protocol is unchanged so the
 * existing web client works against it untouched.
 */
public final class RelayHub implements WsConnection.Listener {

    private static final String TAG = "ws";

    private static final class Room {
        WsConnection host;
        final LinkedHashMap<String, WsConnection> clients = new LinkedHashMap<>();
    }

    private static final class ConnState {
        String gameId;
        String peerId;
        boolean isHost;
    }

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public boolean hasRoom(String gameId) {
        return rooms.containsKey(gameId);
    }

    public int roomCount() {
        return rooms.size();
    }

    public int playerCount() {
        int total = 0;
        for (Room room : rooms.values()) {
            synchronized (room) {
                total += room.clients.size();
            }
        }
        return total;
    }

    /** True when nothing is going on, so content updates can be swapped in safely. */
    public boolean isIdle() {
        return rooms.isEmpty();
    }

    @Override
    public void onOpen(WsConnection connection) {
        connection.attachment = new ConnState();
    }

    @Override
    public void onMessage(WsConnection connection, String text) {
        ConnState state = state(connection);
        try {
            JSONObject message = new JSONObject(text);
            String type = message.optString("type", "");
            switch (type) {
                case "CREATE_ROOM":
                    createRoom(connection, state, message.optString("gameId", ""));
                    break;
                case "JOIN_ROOM":
                    joinRoom(connection, state, message.optString("gameId", ""), message.optString("peerId", ""));
                    break;
                case "TO_HOST":
                    toHost(message);
                    break;
                case "TO_CLIENT":
                    toClient(message);
                    break;
                case "BROADCAST":
                    broadcast(message);
                    break;
                case "PING":
                    connection.sendText("{\"type\":\"PONG\"}");
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            Slog.i(TAG, "WebSocket message error: " + e);
        }
    }

    @Override
    public void onClose(WsConnection connection) {
        ConnState state = state(connection);
        if (state.gameId == null) {
            return;
        }
        Room room = rooms.get(state.gameId);
        if (room == null) {
            return;
        }
        if (state.isHost) {
            List<WsConnection> orphans;
            synchronized (room) {
                orphans = new ArrayList<>(room.clients.values());
                room.clients.clear();
                room.host = null;
            }
            rooms.remove(state.gameId, room);
            String notice = "{\"type\":\"HOST_DISCONNECTED\"}";
            for (WsConnection client : orphans) {
                client.sendText(notice);
            }
            Slog.i(TAG, "Game room closed: " + state.gameId);
        } else if (state.peerId != null) {
            WsConnection host;
            synchronized (room) {
                // Only drop the map entry if it still points at this socket - a
                // reconnect may already have replaced it.
                if (room.clients.get(state.peerId) == connection) {
                    room.clients.remove(state.peerId);
                }
                host = room.host;
            }
            if (host != null && host.isOpen()) {
                host.sendText(json("type", "PEER_DISCONNECTED", "peerId", state.peerId));
            }
            Slog.i(TAG, "Client " + state.peerId + " left room: " + state.gameId);
        }
    }

    private void createRoom(WsConnection connection, ConnState state, String gameId) {
        if (gameId.isEmpty()) {
            connection.sendText(json("type", "ERROR", "message", "Missing gameId"));
            return;
        }
        Room room = new Room();
        room.host = connection;
        Room existing = rooms.putIfAbsent(gameId, room);
        if (existing != null) {
            boolean takeOver;
            synchronized (existing) {
                // A host that reloaded its page leaves a dead room behind; let the
                // new host reclaim it instead of getting stuck on "Room already exists".
                takeOver = existing.host == null || !existing.host.isOpen();
                if (takeOver) {
                    existing.host = connection;
                }
            }
            if (!takeOver) {
                connection.sendText(json("type", "ERROR", "message", "Room already exists"));
                return;
            }
        }
        state.gameId = gameId;
        state.isHost = true;
        Slog.i(TAG, "Game room created: " + gameId);
        connection.sendText(json("type", "ROOM_CREATED", "gameId", gameId));
    }

    private void joinRoom(WsConnection connection, ConnState state, String gameId, String peerId) {
        Room room = rooms.get(gameId);
        if (room == null || peerId.isEmpty()) {
            connection.sendText(json("type", "ERROR", "message", "Room not found"));
            return;
        }
        WsConnection stale;
        int clientCount;
        WsConnection host;
        synchronized (room) {
            stale = room.clients.get(peerId);
            room.clients.put(peerId, connection);
            clientCount = room.clients.size();
            host = room.host;
        }
        if (stale != null && stale != connection) {
            stale.close();
        }
        state.gameId = gameId;
        state.peerId = peerId;
        Slog.i(TAG, "Client " + peerId + " joined room: " + gameId + " (total clients: " + clientCount + ")");
        try {
            connection.sendText(new JSONObject()
                    .put("type", "ROOM_JOINED")
                    .put("gameId", gameId)
                    .put("clientCount", clientCount)
                    .toString());
            if (host != null && host.isOpen()) {
                host.sendText(new JSONObject()
                        .put("type", "PEER_CONNECTED")
                        .put("peerId", peerId)
                        .put("clientCount", clientCount)
                        .toString());
            }
        } catch (Exception e) {
            Slog.i(TAG, "Join notification failed: " + e);
        }
    }

    private void toHost(JSONObject message) throws Exception {
        Room room = rooms.get(message.optString("gameId", ""));
        if (room == null) {
            return;
        }
        WsConnection host;
        synchronized (room) {
            host = room.host;
        }
        if (host != null && host.isOpen()) {
            host.sendText(new JSONObject()
                    .put("type", "FROM_CLIENT")
                    .put("peerId", message.optString("peerId"))
                    .put("payload", message.opt("payload"))
                    .toString());
        }
    }

    private void toClient(JSONObject message) throws Exception {
        Room room = rooms.get(message.optString("gameId", ""));
        if (room == null) {
            return;
        }
        WsConnection client;
        synchronized (room) {
            client = room.clients.get(message.optString("peerId", ""));
        }
        if (client != null && client.isOpen()) {
            client.sendText(new JSONObject()
                    .put("type", "FROM_HOST")
                    .put("payload", message.opt("payload"))
                    .toString());
        }
    }

    private void broadcast(JSONObject message) throws Exception {
        String gameId = message.optString("gameId", "");
        Room room = rooms.get(gameId);
        if (room == null) {
            return;
        }
        // Serialise once, send to everyone.
        String encoded = new JSONObject()
                .put("type", "FROM_HOST")
                .put("payload", message.opt("payload"))
                .toString();
        List<Map.Entry<String, WsConnection>> clients;
        synchronized (room) {
            clients = new ArrayList<>(room.clients.entrySet());
        }
        int sent = 0;
        int failed = 0;
        for (Map.Entry<String, WsConnection> entry : clients) {
            WsConnection client = entry.getValue();
            if (client.isOpen()) {
                client.sendText(encoded);
                sent++;
            } else {
                synchronized (room) {
                    if (room.clients.get(entry.getKey()) == client) {
                        room.clients.remove(entry.getKey());
                    }
                }
                failed++;
            }
        }
        if (failed > 0) {
            Slog.i(TAG, "Broadcast to " + gameId + ": " + sent + " sent, " + failed + " failed/stale");
        }
    }

    private ConnState state(WsConnection connection) {
        Object attachment = connection.attachment;
        if (attachment instanceof ConnState) {
            return (ConnState) attachment;
        }
        ConnState fresh = new ConnState();
        connection.attachment = fresh;
        return fresh;
    }

    private static String json(String k1, String v1, String k2, String v2) {
        try {
            return new JSONObject().put(k1, v1).put(k2, v2).toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
