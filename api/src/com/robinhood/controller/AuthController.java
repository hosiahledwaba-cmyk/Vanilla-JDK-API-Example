package com.robinhood.controller;

import com.robinhood.model.User;
import com.robinhood.db.DBManager;
import com.robinhood.security.AuthManager;
import com.robinhood.util.JSONParser;
import com.robinhood.util.ResponseBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthController {
    private static final Map<String, User> memoryUsers = new ConcurrentHashMap<>();

    public static String signup(String json) {
        Map<String, String> data = JSONParser.parse(json);
        String email = data.get("email");
        if (email == null) email = data.get("username");
        String password = data.get("password");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return ResponseBuilder.json(400, "{\"error\": \"Email/username and password are required\"}");
        }

        if (memoryUsers.containsKey(email) || DBManager.userExists(email)) {
            return ResponseBuilder.json(409, "{\"error\": \"User already exists\"}");
        }

        User user = new User(email, password);
        memoryUsers.put(email, user);
        DBManager.saveUser(user);

        // Credit 100 welcome tokens so player can immediately play
        WalletController.credit(email, 100);

        return ResponseBuilder.json(201, "{\"success\": true, \"message\": \"Signup successful\"}");
    }

    public static String login(String json) {
        Map<String, String> data = JSONParser.parse(json);
        String email = data.get("email");
        if (email == null) email = data.get("username");
        String password = data.get("password");

        if (email == null || password == null) {
            return ResponseBuilder.json(400, "{\"error\": \"Email/username and password are required\"}");
        }

        User user = memoryUsers.get(email);
        if (user == null) {
            user = DBManager.getUserByEmail(email);
        }

        if (user == null || !user.checkPassword(password)) {
            return ResponseBuilder.json(401, "{\"error\": \"Invalid credentials\"}");
        }

        String token = AuthManager.generateToken(user);
        return ResponseBuilder.json(200, "{\"token\": \"" + token + "\", \"email\": \"" + email + "\", \"user\": \"" + email + "\"}");
    }
}
