// PaymentController.java
package com.robinhood.controller;

public class PaymentController {
    
    // Add gateway param
    public static String deposit(String gateway, String token, String body) {
        String email = AuthManager.getEmailFromToken(token);
        var data = JSONParser.parse(body);
        
        PaymentTransaction tx = new PaymentTransaction();
        tx.setGateway(gateway);
        tx.setEmail(email);
        tx.setAmount(Integer.parseInt(data.get("amount")));
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
    }

    public static String webhook(String gateway, String body, Map<String,String> headers) {
        var evt = PaymentGatewayService.handleWebhook(gateway, body, headers);
        int txId = Integer.parseInt(evt.get("txId").toString());
        String status = evt.get("status").toString();
        PaymentTransactionManager.updateStatus(txId, status, evt.getOrDefault("id", "").toString());

        if ("success".equalsIgnoreCase(status)) {
			PaymentTransaction tx = PaymentTransactionManager.get(txId);
			WalletController.credit(tx.getEmail(), tx.getAmount());
		}


        return ResponseBuilder.json(200, "OK");
    }
}
