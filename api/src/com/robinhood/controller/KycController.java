package com.robinhood.controller;

import com.robinhood.db.KycRequestManager;
import com.robinhood.model.KycRequest;
import com.robinhood.security.AuthManager;
import com.robinhood.service.KycService;
import com.robinhood.util.JSONParser;
import com.robinhood.util.ResponseBuilder;

import java.util.Map;

public class KycController {

    public static String apply(String token, String body) {
        try {
            String email = AuthManager.getEmailFromToken(token);
            var d = JSONParser.parse(body);
            KycRequest r = new KycRequest(0, email, d.get("fullName"), d.get("type"), d.get("url"), null, "pending", null, null);
            int id = KycRequestManager.create(r);
            var prov = KycService.submit(r);
            String provId = prov.getOrDefault("request_id", "req_" + id).toString();
            KycRequestManager.updateStatus(id, "pending", provId, null);
            return ResponseBuilder.json(201, "{\"requestId\":" + id + ",\"providerId\":\"" + provId + "\"}");
        } catch (Exception e) {
            return ResponseBuilder.json(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    public static String status(String token, String body) {
        try {
            var d = JSONParser.parse(body);
            int id = Integer.parseInt(d.getOrDefault("requestId", "0"));
            var k = KycRequestManager.getById(id);
            if (k == null) {
                return ResponseBuilder.json(404, "{\"error\":\"KYC request not found\"}");
            }
            var st = KycService.status(k.getProviderRequestId());
            String status = st.getOrDefault("status", "pending").toString();
            String note = st.containsKey("note") ? st.get("note").toString() : null;
            KycRequestManager.updateStatus(id, status, k.getProviderRequestId(), note);
            return ResponseBuilder.json(200, JSONParser.stringify(st));
        } catch (Exception e) {
            return ResponseBuilder.json(500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}