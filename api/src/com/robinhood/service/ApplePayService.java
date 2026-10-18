package com.robinhood.service;

import com.robinhood.core.ConfigLoader;
import com.robinhood.util.JSONParser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ApplePayService {
    private static final String MERCHANT_ID = ConfigLoader.get("braintree.merchantId");

    public static Map<String, Object> processPayment(String appleToken, int amount) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", "ap_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        response.put("status", "success");
        response.put("amount", amount);
        response.put("merchantId", MERCHANT_ID);
        return response;
    }

    public static Map<String, Object> handleWebhook(String rawJson, Map<String, String> headers) {
        Map<String, String> data = JSONParser.parse(rawJson);
        Map<String, Object> result = new HashMap<>();
        result.put("txId", data.getOrDefault("txId", data.getOrDefault("id", "0")));
        result.put("status", data.getOrDefault("status", "success"));
        result.put("id", data.getOrDefault("transaction_id", "ap_mock"));
        return result;
    }
}
