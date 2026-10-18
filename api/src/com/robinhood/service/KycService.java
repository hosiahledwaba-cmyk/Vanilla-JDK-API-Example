package com.robinhood.service;

import com.robinhood.core.ConfigLoader;
import com.robinhood.model.KycRequest;
import com.robinhood.util.HttpClientUtil;
import com.robinhood.util.JSONParser;

import java.util.Map;

public class KycService {
    private static final String KYC_API_URL = ConfigLoader.get("kyc.api.url"); 
    private static final String API_KEY     = ConfigLoader.get("kyc.api.key");

    public static Map<String, Object> submitToProvider(KycRequest req) throws Exception {
        String payload = JSONParser.stringify(Map.of(
            "name", req.getFullName() != null ? req.getFullName() : "",
            "type", req.getDocumentType() != null ? req.getDocumentType() : "",
            "url", req.getDocumentUrl() != null ? req.getDocumentUrl() : ""
        ));
        return HttpClientUtil.postJson(KYC_API_URL + "/submit", payload, Map.of("Authorization", "Bearer " + (API_KEY != null ? API_KEY : "")));
    }

    public static Map<String, Object> submit(KycRequest req) throws Exception {
        return submitToProvider(req);
    }

    public static Map<String, Object> checkStatus(String providerReqId) throws Exception {
        return HttpClientUtil.getJson(KYC_API_URL + "/status/" + providerReqId, Map.of("Authorization", "Bearer " + (API_KEY != null ? API_KEY : "")));
    }

    public static Map<String, Object> status(String providerReqId) throws Exception {
        return checkStatus(providerReqId);
    }
}
