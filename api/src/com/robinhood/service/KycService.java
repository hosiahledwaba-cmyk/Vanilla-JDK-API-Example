// KycService.java
package com.robinhood.service;

import com.robinhood.model.KycRequest;
import com.robinhood.util.HttpClientUtil;
import java.util.Map;

public class KycService {
    private static final String KYC_API_URL = ConfigLoader.get("kyc.api.url"); 
    private static final String API_KEY     = ConfigLoader.get("kyc.api.key");

    public static Map<String,Object> submitToProvider(KycRequest req) throws Exception {
        String payload = JSONParser.stringify(Map.of(
            "name", req.getFullName(),
            "type", req.getDocumentType(),
            "url", req.getDocumentUrl()
        ));
        return HttpClientUtil.postJson(KYC_API_URL + "/submit", payload, Map.of("Authorization", "Bearer "+API_KEY));
    }

    public static Map<String,Object> checkStatus(String providerReqId) throws Exception {
        return HttpClientUtil.getJson(KYC_API_URL + "/status/"+providerReqId, Map.of("Authorization","Bearer "+API_KEY));
    }
}
