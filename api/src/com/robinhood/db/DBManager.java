package com.robinhood.db;

import com.robinhood.core.ConfigLoader;
import com.robinhood.model.User;

import java.sql.*;

public class DBManager {
    private static final String DEFAULT_DB_URL = "jdbc:postgresql://localhost:5433/robinhood";
    private static final String DEFAULT_USER = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";

    private static volatile boolean schemaInitialized = false;

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException ignored) {}
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {}
    }

    public static Connection getConnection() throws SQLException {
        String envUrl = System.getenv("DB_URL");
        String url = (envUrl != null && !envUrl.isBlank()) ? envUrl : ConfigLoader.get("db.url");
        if (url == null || url.isBlank()) url = DEFAULT_DB_URL;

        String envUser = System.getenv("DB_USER");
        String user = (envUser != null && !envUser.isBlank()) ? envUser : ConfigLoader.get("db.user");
        if (user == null || user.isBlank()) user = DEFAULT_USER;

        String envPass = System.getenv("DB_PASSWORD");
        if (envPass == null || envPass.isBlank()) envPass = System.getenv("DB_PASS");
        String pass = (envPass != null && !envPass.isBlank()) ? envPass : ConfigLoader.get("db.pass");
        if (pass == null) pass = DEFAULT_PASSWORD;

        Connection conn = DriverManager.getConnection(url, user, pass);

        if (!schemaInitialized && url.contains("postgresql")) {
            synchronized (DBManager.class) {
                if (!schemaInitialized) {
                    try {
                        PostgresSchema.initialize(conn);
                        schemaInitialized = true;
                        System.out.println("[DBManager] PostgreSQL schema successfully initialized.");
                    } catch (Exception e) {
                        System.err.println("[DBManager] Schema init notice: " + e.getMessage());
                    }
                }
            }
        }

        return conn;
    }

    public static boolean isAvailable() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean userExists(String email) {
        String query = "SELECT id FROM users WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    public static void saveUser(User user) {
        String query = "INSERT INTO users (email, password_hash) VALUES (?, ?) ON CONFLICT (email) DO NOTHING";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPasswordHash());
            ps.executeUpdate();
        } catch (SQLException e) {
            // Fallback for MySQL if running in MySQL mode
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT IGNORE INTO users (email, password_hash) VALUES (?, ?)")) {
                ps.setString(1, user.getEmail());
                ps.setString(2, user.getPasswordHash());
                ps.executeUpdate();
            } catch (SQLException ex) {
                System.err.println("Database notice (saveUser): " + ex.getMessage());
            }
        }
    }

    public static User getUserByEmail(String email) {
        String query = "SELECT email, password_hash FROM users WHERE email = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(rs.getString("email"), rs.getString("password_hash"), true);
            }
        } catch (SQLException e) {
            System.err.println("Database notice (getUserByEmail): " + e.getMessage());
        }
        return null;
    }

    public static int getUserCount() {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM users")) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {}
        return 0;
    }

    public static int getMatchCount() {
        try (Connection conn = getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM match_sessions")) {
            if (rs.next()) return rs.getInt(1);
        } catch (Exception ignored) {}
        return 0;
    }
}
