package com.robinhood.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RobinServer {
    private final int port;
    private final ExecutorService threadPool;
    private volatile boolean running = false;
    private ServerSocketChannel serverChannel;
    private Selector selector;

    public RobinServer(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(ConfigLoader.getInt("max.threads", 200));
    }

    public void start() {
        running = true;
        Thread serverThread = new Thread(this::runLoop, "RobinServer-Thread");
        serverThread.start();
    }

    private void runLoop() {
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);

            selector = Selector.open();
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("RobinServer NIO running on port " + port);

            while (running && selector.isOpen()) {
                int selected = selector.select(1000);
                if (selected == 0) continue;

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        handleAccept(serverChannel, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("RobinServer error: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (selector != null) selector.close();
            if (serverChannel != null) serverChannel.close();
        } catch (IOException ignored) {}
        threadPool.shutdown();
    }

    private void handleAccept(ServerSocketChannel server, Selector selector) throws IOException {
        SocketChannel client = server.accept();
        if (client != null) {
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
        }
    }

    private void handleRead(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        try {
            key.cancel(); // Cancel from selector so it doesn't trigger repeatedly
        } catch (Exception ignored) {}

        threadPool.submit(() -> {
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                ByteBuffer buffer = ByteBuffer.allocate(8192);

                int bytesRead = client.read(buffer);
                if (bytesRead <= 0) {
                    client.close();
                    return;
                }
                buffer.flip();
                byte[] firstChunk = new byte[buffer.remaining()];
                buffer.get(firstChunk);
                baos.write(firstChunk);

                String currentStr = baos.toString(StandardCharsets.UTF_8);
                int sepIdx = currentStr.indexOf("\r\n\r\n");
                int sepLen = 4;
                if (sepIdx == -1) {
                    sepIdx = currentStr.indexOf("\n\n");
                    sepLen = 2;
                }

                if (sepIdx != -1) {
                    String headersPart = currentStr.substring(0, sepIdx);
                    if (headersPart.toLowerCase().contains("expect: 100-continue")) {
                        client.write(ByteBuffer.wrap("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.UTF_8)));
                    }

                    int contentLength = 0;
                    for (String line : headersPart.split("\r?\n")) {
                        if (line.toLowerCase().startsWith("content-length:")) {
                            try {
                                contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                            } catch (Exception ignored) {}
                        }
                    }

                    int bodyBytesReceived = baos.size() - (sepIdx + sepLen);
                    while (bodyBytesReceived < contentLength) {
                        buffer.clear();
                        int r = client.read(buffer);
                        if (r <= 0) {
                            try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                            r = client.read(buffer);
                            if (r <= 0) break;
                        }
                        buffer.flip();
                        byte[] chunk = new byte[buffer.remaining()];
                        buffer.get(chunk);
                        baos.write(chunk);
                        bodyBytesReceived += r;
                    }
                }

                String request = baos.toString(StandardCharsets.UTF_8);

                String response = Router.handleRequest(request, client);
                if (response != null) {
                    byte[] respBytes = response.getBytes(StandardCharsets.UTF_8);
                    ByteBuffer respBuf = ByteBuffer.wrap(respBytes);
                    while (respBuf.hasRemaining()) {
                        client.write(respBuf);
                    }
                    client.close();
                }
            } catch (IOException e) {
                try {
                    client.close();
                } catch (IOException ignored) {}
            }
        });
    }
}
