package com.robinhood.util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {

    private static final Path BASE_DIR = Paths.get("logs");
    private static final Path MAIN_LOG = BASE_DIR.resolve("robinhood.log");
    private static final Path ADMIN_LOG = BASE_DIR.resolve("admin.log");

    private static final boolean LOG_TO_FILE = true;
    private static final Object lock = new Object();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public enum Level {
        INFO, WARN, ERROR
    }

    // --- General logging ---

    public static void info(String msg) {
        log(Level.INFO, msg);
    }

    public static void warn(String msg) {
        log(Level.WARN, msg);
    }

    public static void error(String msg) {
        log(Level.ERROR, msg);
    }

    public static void error(String msg, Throwable t) {
        log(Level.ERROR, msg + "\n" + getStackTrace(t));
    }

    private static void log(Level level, String msg) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String formatted = "[" + timestamp + "] [" + level.name() + "] " + msg;

        synchronized (lock) {
            System.out.println(formatted);
            if (LOG_TO_FILE) {
                writeToFile(MAIN_LOG, formatted);
            }
        }
    }

    // --- Admin-specific audit logging ---
    public static void admin(String actor, String action, String detail) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String line = String.format("%s | %s | %s | %s", timestamp, actor, action, detail);

        synchronized (lock) {
            System.out.println("[ADMIN] " + line);
            if (LOG_TO_FILE) {
                writeToFile(ADMIN_LOG, line);
            }
        }
    }

    // --- Utilities ---
    private static void writeToFile(Path filePath, String line) {
        try {
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Logger failed to write to file: " + e.getMessage());
        }
    }

    private static String getStackTrace(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        return sw.toString();
    }
}
