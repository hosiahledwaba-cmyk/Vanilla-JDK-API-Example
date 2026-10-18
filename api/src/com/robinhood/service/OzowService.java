package com.robinhood.service;

import com.robinhood.core.ConfigLoader;
import com.robinhood.util.HttpClientUtil;
import com.robinhood.util.JSONParser;
import com.robinhood.util.TimeUtils;
import com.robinhood.util.Util;

import java.util.HashMap;
import java.util.Map;

public class OzowService {
    private static final String MERCHANT = ConfigLoader.get("ozow.merchantCode");
    private static final String KEY = ConfigLoader.get("ozow.securityKey");
    private static final String ENDPOINT = ConfigLoader.get("ozow.apiUrl");

    public static String createPaymentLink(String ref, int amount, String returnUrl) throws Exception {
        String timestamp = TimeUtils.nowAsString();
        String data = MERCHANT + "|" + ref + "|" + amount + "|" + returnUrl + "|" + timestamp + "|" + KEY;
        String hash = Util.sha512(data);
        var resp = HttpClientUtil.formPost(ENDPOINT + "/ProcessPayment", Map.of(
            "MerchantCode", MERCHANT != null ? MERCHANT : "",
            "TransactionReference", ref != null ? ref : "",
            "Amount", String.valueOf(amount),
            "ReturnUrl", returnUrl != null ? returnUrl : "",
            "SecurityHash", hash,
            "Timestamp", timestamp
        ));
        return resp.getOrDefault("PaymentURL", ENDPOINT + "/pay/" + ref);
    }

    public static Map<String, Object> handleWebhook(String rawJson, Map<String, String> headers) {
        Map<String, String> data = JSONParser.parse(rawJson);
        Map<String, Object> result = new HashMap<>();
        result.put("txId", data.getOrDefault("txId", data.getOrDefault("id", "0")));
        result.put("status", data.getOrDefault("Status", data.getOrDefault("status", "success")));
        result.put("id", data.getOrDefault("TransactionReference", "ozow_mock"));
        return result;
    }
}
