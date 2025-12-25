package com.robinhood;

import com.robinhood.core.RobinServer;

public class Main {
    public static void main(String[] args) {
        RobinServer server = new RobinServer(8080);
        server.start();
		
		Timer timer = new Timer(true);
		timer.scheduleAtFixedRate(new SessionCleaner(), 60_000, 60_000);
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override public void run() {
				RealTimeDispatcher.heartbeatAndCleanup();
			}
		}, 15_000, 15_000);
		
    }
}
