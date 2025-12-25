package com.robinhood.service;

import com.robinhood.util.ConfigLoader;
import com.robinhood.util.HttpClientUtil;
import com.robinhood.util.JSONParser;

import java.util.Map;

public class BluVoucherService {
    private static final String URL = ConfigLoader.get("bluvoucher.apiUrl");
    private static final String KEY = ConfigLoader.get("bluvoucher.apiKey");

    public static boolean redeemVoucher(String code, String email) throws Exception {
        var resp = HttpClientUtil.postJson(URL + "/redeem",
            JSONParser.stringify(Map.of("voucherCode", code, "email", email)),
            Map.of("Authorization", "Bearer " + KEY)
        );
        return "success".equalsIgnoreCase(resp.get("status").toString());
    }
}
