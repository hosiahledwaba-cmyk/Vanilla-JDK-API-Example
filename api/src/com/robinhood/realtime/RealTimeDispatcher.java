package com.robinhood.realtime;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RealTimeDispatcher {
    private static final Map<String, Set<SocketChannel>> subscribers = new ConcurrentHashMap<>();
    private static final Map<SocketChannel, Instant> lastHeartbeat = new ConcurrentHashMap<>();
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    public static synchronized void subscribe(String sessionId, SocketChannel channel) throws IOException {
        subscribers.computeIfAbsent(sessionId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                   .add(channel);
        lastHeartbeat.put(channel, Instant.now());

        // Send SSE headers
        String headers =
            "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/event-stream\r\n" +
            "Cache-Control: no-cache\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Connection: keep-alive\r\n\r\n";
        channel.write(ByteBuffer.wrap(headers.getBytes()));
    }

    public static synchronized void unsubscribe(String sessionId, SocketChannel channel) {
        Set<SocketChannel> set = subscribers.get(sessionId);
        if (set != null) set.remove(channel);
        lastHeartbeat.remove(channel);
    }

    public static synchronized void publish(String sessionId, String eventJson) {
        Set<SocketChannel> set = subscribers.get(sessionId);
        if (set == null || set.isEmpty()) return;

        String sse = "data: " + eventJson + "\n\n";
        ByteBuffer buf = ByteBuffer.wrap(sse.getBytes());

        for (Iterator<SocketChannel> it = set.iterator(); it.hasNext(); ) {
            SocketChannel ch = it.next();
            try {
                ch.write(buf.duplicate());
            } catch (IOException e) {
                it.remove();
                lastHeartbeat.remove(ch);
                closeSilently(ch);
            }
        }
    }

    public static int getActiveConnectionCount() {
        return lastHeartbeat.size();
    }

    public static void heartbeatAndCleanup() {
        Instant now = Instant.now();
        for (Map.Entry<SocketChannel, Instant> entry : lastHeartbeat.entrySet()) {
            SocketChannel ch = entry.getKey();
            Instant last = entry.getValue();
            try {
                if (Duration.between(last, now).compareTo(HEARTBEAT_INTERVAL) >= 0) {
                    ch.write(ByteBuffer.wrap(":\n\n".getBytes()));  // SSE ping
                    lastHeartbeat.put(ch, now);
                }
                if (Duration.between(last, now).compareTo(TIMEOUT) > 0) {
                    closeChannel(ch);
                }
            } catch (IOException ignored) {
                closeChannel(ch);
            }
        }
    }

    private static void closeChannel(SocketChannel ch) {
        lastHeartbeat.remove(ch);
        subscribers.values().forEach(set -> set.remove(ch));
        closeSilently(ch);
    }

    private static void closeSilently(SocketChannel ch) {
        try { ch.close(); } catch (IOException ignored) {}
    }
}
