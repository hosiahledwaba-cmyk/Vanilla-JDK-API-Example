package com.robinhood.controller;

import com.robinhood.db.DBManager;
import com.robinhood.db.LedgerManager;
import com.robinhood.model.LedgerEntry;
import com.robinhood.model.User;
import com.robinhood.model.Wallet;
import com.robinhood.security.AuthManager;
import com.robinhood.util.JSONParser;
import com.robinhood.util.ResponseBuilder;

import java.util.HashMap;
import java.util.Map;

public class WalletController {
    private static final Map<String, Wallet> wallets = new HashMap<>();

    private static synchronized Wallet getOrCreateWallet(String email) {
        return wallets.computeIfAbsent(email, e -> new Wallet(e, 500));
    }

    public static String deposit(String token, String body) {
        String email = AuthManager.getEmailFromToken(token);
        Map<String, String> data = JSONParser.parse(body);
        int amount = Integer.parseInt(data.getOrDefault("amount", "0"));

        Wallet wallet = getOrCreateWallet(email);
        wallet.deposit(amount);

        LedgerManager.log(new LedgerEntry(email, LedgerEntry.Type.DEPOSIT, amount, "Manual deposit"));

        return ResponseBuilder.json(200, "{\"balance\":" + wallet.getBalance() + ",\"message\":\"Deposited successfully\"}");
    }

    public static String balance(String token) {
        String email = AuthManager.getEmailFromToken(token);
        Wallet wallet = getOrCreateWallet(email);
        return ResponseBuilder.json(200, "{\"balance\":" + wallet.getBalance() + ",\"email\":\"" + email + "\"}");
    }

    public static String withdraw(String token, String body) {
        String email = AuthManager.getEmailFromToken(token);
        Map<String, String> data = JSONParser.parse(body);
        int amount = Integer.parseInt(data.getOrDefault("amount", "0"));

        if (!deduct(email, amount)) {
            return ResponseBuilder.json(400, "{\"error\":\"Insufficient balance\",\"balance\":" + getBalance(email) + "}");
        }

        LedgerManager.log(new LedgerEntry(email, LedgerEntry.Type.WITHDRAWAL, amount, "Wallet withdrawal"));
        return ResponseBuilder.json(200, "{\"balance\":" + getBalance(email) + ",\"message\":\"Withdrawn successfully\"}");
    }

    public static synchronized boolean deduct(String email, int amount) {
        Wallet wallet = getOrCreateWallet(email);
        return wallet.deduct(amount);
    }

    public static synchronized void credit(String email, int amount) {
        Wallet wallet = getOrCreateWallet(email);
        wallet.deposit(amount);
    }

    public static int getBalance(String email) {
        return getOrCreateWallet(email).getBalance();
    }
}
