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

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new SessionCleaner(), 60_000, 60_000);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                RealTimeDispatcher.heartbeatAndCleanup();
            }
        }, 15_000, 15_000);

        System.out.println("Robinhood Vanilla JDK Server ready at http://localhost:8080");
    }
}
