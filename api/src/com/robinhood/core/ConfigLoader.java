package com.robinhood.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    private static final Properties props = new Properties();

    static {
        // Safe default properties
        props.setProperty("server.port", "8080");
        props.setProperty("max.threads", "200");
        props.setProperty("tls.enabled", "false");
        props.setProperty("admin.token", "super-secret-admin-token");
        props.setProperty("admin.viewer.token", "viewer-secret");
        props.setProperty("admin.operator.token", "operator-secret");
        props.setProperty("admin.super.token", "super-secret");
        props.setProperty("db.url", "jdbc:mysql://localhost:3306/robinhood");
        props.setProperty("db.user", "root");
        props.setProperty("db.pass", "yourpass");

        String[] possiblePaths = {
            "config.properties",
            "api/src/com/robinhood/config.properties",
            "src/com/robinhood/config.properties"
        };

        boolean loaded = false;
        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists()) {
                try (FileInputStream fis = new FileInputStream(f)) {
                    props.load(fis);
                    loaded = true;
                    break;
                } catch (IOException ignored) {}
            }
        }

        if (!loaded) {
            try (InputStream is = ConfigLoader.class.getResourceAsStream("/com/robinhood/config.properties")) {
                if (is != null) {
                    props.load(is);
                    loaded = true;
                }
            } catch (IOException ignored) {}
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            String val = props.getProperty(key);
            return val != null ? Integer.parseInt(val) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
