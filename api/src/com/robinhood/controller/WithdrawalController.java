package com.robinhood.controller;

import com.robinhood.db.LedgerManager;
import com.robinhood.db.WithdrawalManager;
import com.robinhood.model.LedgerEntry;
import com.robinhood.security.AuthManager;
import com.robinhood.util.JSONParser;
import com.robinhood.util.ResponseBuilder;

import java.util.Map;

public class WithdrawalController {

    public static String request(String token, String body) {
        String email = AuthManager.getEmailFromToken(token);
        Map<String, String> data = JSONParser.parse(body);

        int amount = Integer.parseInt(data.getOrDefault("amount", "0"));
        String method = data.getOrDefault("method", "bank");
        String details = data.getOrDefault("details", "");

        if (amount < 50) {
            return ResponseBuilder.json(400, "Minimum withdrawal is 50 tokens");
        }

        boolean ok = WithdrawalManager.requestWithdrawalAtomically(email, amount, method, details);
        if (!ok) {
            return ResponseBuilder.json(403, "Insufficient balance or error processing request");
        }

        // Log to ledger
        LedgerManager.log(new LedgerEntry(email, LedgerEntry.Type.WITHDRAWAL, amount, "Withdrawal requested"));

        return ResponseBuilder.json(200, "{\"status\":\"pending\"}");
    }

    public static String history(String token) {
        String email = AuthManager.getEmailFromToken(token);
        return ResponseBuilder.json(200, WithdrawalManager.getUserRequests(email));
    }
}
