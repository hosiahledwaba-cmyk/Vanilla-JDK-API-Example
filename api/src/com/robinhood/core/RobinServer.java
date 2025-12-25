package com.robinhood.core;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RobinServer {
    private final int port;
    private final ExecutorService threadPool;

    public RobinServer(int port) {
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(ConfigLoader.getInt("max.threads", 200));
    }

    public void start() {
        try (ServerSocketChannel server = ServerSocketChannel.open()) {
            
			if (Boolean.parseBoolean(ConfigLoader.get("tls.enabled"))) {
				KeyStore ks = KeyStore.getInstance("JKS");
				try (FileInputStream fis = new FileInputStream(ConfigLoader.get("tls.keystore.path"))) {
					ks.load(fis, ConfigLoader.get("tls.keystore.password").toCharArray());
				}
				KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
				kmf.init(ks, ConfigLoader.get("tls.keystore.password").toCharArray());
				SSLContext sslCtx = SSLContext.getInstance("TLS");
				sslCtx.init(kmf.getKeyManagers(), null, null);

				// Replace ServerSocketChannel with SSLServerSocketChannel wrapper
				server = sslCtx.getServerSocketFactory()
							   .createServerSocket(port);
				((SSLServerSocket)server).setNeedClientAuth(false);
			} else {
				server = ServerSocketChannel.open();
				server.bind(new InetSocketAddress(port));
			}
			
            server.configureBlocking(false);
            Selector selector = Selector.open();
            server.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("🚀 RobinServer NIO running on port " + port);

            while (true) {
                selector.select();
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();

                    if (!key.isValid()) continue;

                    if (key.isAcceptable()) {
                        handleAccept(server, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleAccept(ServerSocketChannel server, Selector selector) throws IOException {
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
    }

    private void handleRead(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(8192);

        try {
            int bytesRead = client.read(buffer);
            if (bytesRead == -1) {
                client.close();
                return;
            }

            threadPool.submit(() -> {
                try {
                    buffer.flip();
                    byte[] data = new byte[buffer.remaining()];
                    buffer.get(data);
                    String request = new String(data);
                    String response = Router.handleRequest(request);
                    client.write(ByteBuffer.wrap(response.getBytes()));
                } catch (IOException e) {
                    try {
                        client.close();
                    } catch (IOException ex) { ex.printStackTrace(); }
                }
            });

        } catch (IOException e) {
            try {
                client.close();
            } catch (IOException ex) { ex.printStackTrace(); }
        }
    }
}
