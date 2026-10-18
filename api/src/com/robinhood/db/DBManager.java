package com.robinhood.db;

import com.robinhood.core.ConfigLoader;
import com.robinhood.model.User;

import java.sql.*;

public class DBManager {
    private static final String DEFAULT_DB_URL = "jdbc:mysql://localhost:3306/robinhood";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "yourpass";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // Driver may not be present in pure JDK demo mode; caught when getConnection() is called
        }
    }

    public static Connection getConnection() throws SQLException {
        String url = ConfigLoader.get("db.url");
        String user = ConfigLoader.get("db.user");
        String pass = ConfigLoader.get("db.pass");
        if (url == null) url = DEFAULT_DB_URL;
        if (user == null) user = DEFAULT_USER;
        if (pass == null) pass = DEFAULT_PASSWORD;
        return DriverManager.getConnection(url, user, pass);
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
        String query = "INSERT INTO users (email, password_hash) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPasswordHash());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Database notice (saveUser): " + e.getMessage());
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
}
