package com.robinhood.db;

import com.robinhood.model.User;

import java.sql.*;

public class DBManager {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/robinhood";
    private static final String USER = "root";
    private static final String PASSWORD = "your_password_here"; // change this

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // for MySQL 8.x
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static boolean userExists(String email) {
        String query = "SELECT id FROM users WHERE email = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        }
    }

    public static void saveUser(User user) {
        String query = "INSERT INTO users (email, password_hash) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPasswordHash());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static User getUserByEmail(String email) {
        String query = "SELECT email, password_hash FROM users WHERE email = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new User(rs.getString("email"), rs.getString("password_hash"), true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }
}
