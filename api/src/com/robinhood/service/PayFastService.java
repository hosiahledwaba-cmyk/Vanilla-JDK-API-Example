package com.robinhood.service;

import com.robinhood.util.ConfigLoader;
import com.robinhood.util.HttpClientUtil;
import com.robinhood.util.Util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class PayFastService {
    private static final String ID = ConfigLoader.get("payfast.id");
    private static final String KEY = ConfigLoader.get("payfast.key");
    private static final String ENDPOINT = ConfigLoader.get("payfast.apiUrl");

    public static String createPaymentUrl(String ref, int amount, String returnUrl, String notifyUrl) throws Exception {
        Map<String, String> data = Map.of(
            "merchant_id", ID,
            "merchant_key", KEY,
            "amount", String.format("%.2f", amount / 100.0),
            "item_name", "Deposit",
            "m_payment_id", ref,
            "return_url", returnUrl,
            "notify_url", notifyUrl
        );
        String qs = data.entrySet().stream()
            .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
        String signature = Util.md5(qs);
        return ENDPOINT + "/eng/process?" + qs + "&signature=" + signature;
    }
}
