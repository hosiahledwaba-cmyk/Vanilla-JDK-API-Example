package com.robinhood.service;

import com.robinhood.core.ConfigLoader;
import com.robinhood.util.HttpClientUtil;
import com.robinhood.util.JSONParser;

import java.util.HashMap;
import java.util.Map;

public class BluVoucherService {
    private static final String URL = ConfigLoader.get("bluvoucher.apiUrl");
    private static final String KEY = ConfigLoader.get("bluvoucher.apiKey");

    public static boolean redeemVoucher(String code, String email) throws Exception {
        var resp = HttpClientUtil.postJson(URL + "/redeem",
            JSONParser.stringify(Map.of("voucherCode", code != null ? code : "", "email", email != null ? email : "")),
            Map.of("Authorization", "Bearer " + (KEY != null ? KEY : ""))
        );
        Object status = resp.get("status");
        return status != null && "success".equalsIgnoreCase(status.toString());
    }

    public static Map<String, Object> handleWebhook(String rawJson, Map<String, String> headers) {
        Map<String, String> data = JSONParser.parse(rawJson);
        Map<String, Object> result = new HashMap<>();
        result.put("txId", data.getOrDefault("txId", data.getOrDefault("id", "0")));
        result.put("status", data.getOrDefault("status", "success"));
        result.put("id", data.getOrDefault("voucherCode", "blu_mock"));
        return result;
    }
}
