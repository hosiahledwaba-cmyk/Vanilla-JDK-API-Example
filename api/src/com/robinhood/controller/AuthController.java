package com.robinhood.controller;

import com.robinhood.model.User;
import com.robinhood.db.DBManager;
import com.robinhood.security.AuthManager;
import com.robinhood.util.JSONParser;
import com.robinhood.util.ResponseBuilder;

import java.util.Map;

public class AuthController {
    public static String signup(String json) {
        Map<String, String> data = JSONParser.parse(json);
        String email = data.get("email");
        String password = data.get("password");

        if (DBManager.userExists(email)) {
            return ResponseBuilder.json(409, "User already exists");
        }

        User user = new User(email, password);
        DBManager.saveUser(user);

        return ResponseBuilder.json(201, "Signup successful");
    }

    public static String login(String json) {
        Map<String, String> data = JSONParser.parse(json);
        String email = data.get("email");
        String password = data.get("password");

        User user = DBManager.getUserByEmail(email);
        if (user == null || !user.checkPassword(password)) {
            return ResponseBuilder.json(401, "Invalid credentials");
        }

        String token = AuthManager.generateToken(user);
        return ResponseBuilder.json(200, "{\"token\": \"" + token + "\"}");
    }
}
