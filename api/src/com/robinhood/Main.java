package com.robinhood;

import com.robinhood.core.RobinServer;
import com.robinhood.core.SessionCleaner;
import com.robinhood.realtime.RealTimeDispatcher;

import java.util.Timer;
import java.util.TimerTask;

public class Main {
    public static void main(String[] args) {
        System.out.println("Starting Robinhood Vanilla JDK Server...");
        RobinServer server = new RobinServer(8080);
        server.start();

        try {
            if (com.robinhood.db.DBManager.isAvailable()) {
                System.out.println("[Main] Connected to PostgreSQL in Docker on port 5433 successfully.");
            } else {
                System.out.println("[Main] Warning: Database connection returned false.");
            }
        } catch (Exception e) {
            System.err.println("[Main] Database connection check error: " + e.getMessage());
        }

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new SessionCleaner(), 60_000, 60_000);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                RealTimeDispatcher.heartbeatAndCleanup();
            }
        }, 15_000, 15_000);

        System.out.println("Robinhood Vanilla JDK Server ready at http://localhost:8080");

        try {
            Thread.currentThread().join();
        } catch (InterruptedException ignored) {}
    }
}
