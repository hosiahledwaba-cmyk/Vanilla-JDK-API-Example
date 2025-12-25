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

    public static String deposit(String token, String body) {
        String email = AuthManager.getEmailFromToken(token);
        Map<String, String> data = JSONParser.parse(body);
        int amount = Integer.parseInt(data.get("amount"));

        Wallet wallet = wallets.getOrDefault(email, new Wallet(email, 0));
        wallet.deposit(amount);
        wallets.put(email, wallet);

        LedgerManager.log(new LedgerEntry(email, LedgerEntry.Type.DEPOSIT, amount, "Manual deposit"));

        return ResponseBuilder.json(200, "{\"balance\":" + wallet.getBalance() + "}");
    }

    public static String balance(String token) {
        String email = AuthManager.getEmailFromToken(token);
        Wallet wallet = wallets.getOrDefault(email, new Wallet(email, 0));
        return ResponseBuilder.json(200, "{\"balance\":" + wallet.getBalance() + "}");
    }

    public static boolean deduct(String email, int amount) {
        Wallet wallet = wallets.get(email);
        if (wallet == null || !wallet.deduct(amount)) return false;
        wallets.put(email, wallet);
        return true;
    }

    public static void credit(String email, int amount) {
        Wallet wallet = wallets.getOrDefault(email, new Wallet(email, 0));
        wallet.deposit(amount);
        wallets.put(email, wallet);
    }

    public static int getBalance(String email) {
        Wallet wallet = wallets.getOrDefault(email, new Wallet(email, 0));
        return wallet.getBalance();
    }
}
