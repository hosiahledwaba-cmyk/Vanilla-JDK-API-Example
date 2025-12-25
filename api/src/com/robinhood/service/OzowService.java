package com.robinhood.service;

import com.robinhood.util.ConfigLoader;
import com.robinhood.util.HttpClientUtil;
import com.robinhood.util.TimeUtils;
import com.robinhood.util.Util;

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
            "MerchantCode", MERCHANT,
            "TransactionReference", ref,
            "Amount", String.valueOf(amount),
            "ReturnUrl", returnUrl,
            "SecurityHash", hash,
            "Timestamp", timestamp
        ));
        return resp.get("PaymentURL");
    }
}
