package com.robinhood.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {
    private static final AtomicInteger totalRequests = new AtomicInteger(0);
    private static final AtomicInteger activeSessions = new AtomicInteger(0);
    private static final AtomicInteger totalErrors = new AtomicInteger(0);
    private static final AtomicLong totalUptimeMillis = new AtomicLong(System.currentTimeMillis());

    public static void incrementRequests() {
        totalRequests.incrementAndGet();
    }

    public static void incrementErrors() {
        totalErrors.incrementAndGet();
    }

    public static void setActiveSessions(int count) {
        activeSessions.set(count);
    }

    public static String scrape() {
        long uptime = System.currentTimeMillis() - totalUptimeMillis.get();
        return  "# HELP robinhood_requests_total Total HTTP requests\n" +
                "# TYPE robinhood_requests_total counter\n" +
                "robinhood_requests_total " + totalRequests.get() + "\n\n" +

                "# HELP robinhood_errors_total Total server errors\n" +
                "# TYPE robinhood_errors_total counter\n" +
                "robinhood_errors_total " + totalErrors.get() + "\n\n" +

                "# HELP robinhood_active_sessions Number of active game sessions\n" +
                "# TYPE robinhood_active_sessions gauge\n" +
                "robinhood_active_sessions " + activeSessions.get() + "\n\n" +

                "# HELP robinhood_uptime_milliseconds JVM uptime since boot\n" +
                "# TYPE robinhood_uptime_milliseconds gauge\n" +
                "robinhood_uptime_milliseconds " + uptime + "\n";
    }
}
