package com.robinhood.controller;

import com.robinhood.db.PaymentTransactionManager;
import com.robinhood.model.PaymentTransaction;
import com.robinhood.security.AuthManager;
import com.robinhood.service.PaymentGatewayService;
import com.robinhood.util.JSONParser;
import com.robinhood.util.ResponseBuilder;

import java.util.Map;
import java.util.UUID;

public class PaymentController {
    
    public static String deposit(String gateway, String token, String body) {
        try {
            String email = AuthManager.getEmailFromToken(token);
            var data = JSONParser.parse(body);
            
            PaymentTransaction tx = new PaymentTransaction();
            tx.setGateway(gateway);
            tx.setEmail(email);
            tx.setAmount(Integer.parseInt(data.getOrDefault("amount", "0")));
            tx.setCurrency(data.getOrDefault("currency", "ZAR"));
            tx.setReference(data.getOrDefault("reference", UUID.randomUUID().toString()));
            tx.setReturnUrl(data.getOrDefault("returnUrl", ""));
            tx.setNotifyUrl(data.getOrDefault("notifyUrl", ""));
            tx.setAppleToken(data.getOrDefault("appleToken", null));
            tx.setVoucherCode(data.getOrDefault("voucherCode", null));
            tx.setStatus("pending");
            tx.setMetadata(data);

            int id = PaymentTransactionManager.create(tx);
            var resp = PaymentGatewayService.initiateDeposit(tx);
            PaymentTransactionManager.updateStatus(id, "pending", resp.getOrDefault("id", "").toString());

            return ResponseBuilder.json(201, JSONParser.stringify(resp));
        } catch (Exception e) {
            return ResponseBuilder.json(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public static String webhook(String gateway, String body, Map<String, String> headers) {
        try {
            var evt = PaymentGatewayService.handleWebhook(gateway, body, headers);
            int txId = 0;
            if (evt.containsKey("txId")) {
                try {
                    txId = Integer.parseInt(evt.get("txId").toString());
                } catch (NumberFormatException ignored) {}
            }
            String status = evt.getOrDefault("status", "success").toString();
            PaymentTransactionManager.updateStatus(txId, status, evt.getOrDefault("id", "").toString());

            if ("success".equalsIgnoreCase(status) && txId > 0) {
                PaymentTransaction tx = PaymentTransactionManager.get(txId);
                if (tx != null) {
                    WalletController.credit(tx.getEmail(), tx.getAmount());
                }
            }

            return ResponseBuilder.json(200, "{\"status\":\"OK\"}");
        } catch (Exception e) {
            return ResponseBuilder.json(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}
