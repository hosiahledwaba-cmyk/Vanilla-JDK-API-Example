package com.robinhood.service;

import com.robinhood.core.ConfigLoader;
import com.robinhood.util.HttpClientUtil;
import com.robinhood.util.JSONParser;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class StripeService {
    private static final String API_KEY = ConfigLoader.get("stripe.apiKey");

    public static Map<String, Object> createPaymentIntent(int amount, String currency, String customerEmail) {
        String paymentIntentId = "pi_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String clientSecret = paymentIntentId + "_secret_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Map<String, Object> response = new HashMap<>();
        response.put("id", paymentIntentId);
        response.put("client_secret", clientSecret);
        response.put("amount", amount);
        response.put("currency", currency != null ? currency : "ZAR");
        response.put("customer_email", customerEmail);
        response.put("status", "requires_payment_method");
        return response;
    }

    public static Map<String, Object> retrieve(String piId) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", piId);
        response.put("status", "succeeded");
        return response;
    }

    public static Map<String, Object> handleWebhook(String rawJson, Map<String, String> headers) {
        Map<String, String> data = JSONParser.parse(rawJson);
        Map<String, Object> result = new HashMap<>();
        result.put("txId", data.getOrDefault("txId", data.getOrDefault("id", "0")));
        result.put("status", data.getOrDefault("status", "success"));
        result.put("id", data.getOrDefault("payment_intent", "pi_mock"));
        return result;
    }
}
