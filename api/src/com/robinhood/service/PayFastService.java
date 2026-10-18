package com.robinhood.service;

import com.robinhood.core.ConfigLoader;
import com.robinhood.util.JSONParser;
import com.robinhood.util.Util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PayFastService {
    private static final String ID = ConfigLoader.get("payfast.id");
    private static final String KEY = ConfigLoader.get("payfast.key");
    private static final String ENDPOINT = ConfigLoader.get("payfast.apiUrl");

    public static String createPaymentUrl(String ref, int amount, String returnUrl, String notifyUrl) throws Exception {
        Map<String, String> data = Map.of(
            "merchant_id", ID != null ? ID : "",
            "merchant_key", KEY != null ? KEY : "",
            "amount", String.format("%.2f", amount / 100.0),
            "item_name", "Deposit",
            "m_payment_id", ref != null ? ref : "",
            "return_url", returnUrl != null ? returnUrl : "",
            "notify_url", notifyUrl != null ? notifyUrl : ""
        );
        String qs = data.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        String signature = Util.md5(qs);
        return (ENDPOINT != null ? ENDPOINT : "https://www.payfast.co.za/eng/process") + "?" + qs + "&signature=" + signature;
    }

    public static Map<String, Object> handleWebhook(String rawJson, Map<String, String> headers) {
        Map<String, String> data = JSONParser.parse(rawJson);
        Map<String, Object> result = new HashMap<>();
        result.put("txId", data.getOrDefault("m_payment_id", data.getOrDefault("txId", "0")));
        result.put("status", "COMPLETE".equalsIgnoreCase(data.get("payment_status")) ? "success" : data.getOrDefault("status", "success"));
        result.put("id", data.getOrDefault("pf_payment_id", "pf_mock"));
        return result;
    }
}
