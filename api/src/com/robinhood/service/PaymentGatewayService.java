package com.robinhood.service;

import com.robinhood.model.PaymentTransaction;
import com.robinhood.util.JSONParser;

import java.util.Map;

public class PaymentGatewayService {

    public static Map<String, Object> initiateDeposit(PaymentTransaction tx) throws Exception {
        String gateway = tx.getGateway() != null ? tx.getGateway().toLowerCase() : "stripe";

        switch (gateway) {
            case "stripe":
                return StripeService.createPaymentIntent(tx.getAmount(), tx.getCurrency(), tx.getEmail());
            case "ozow":
                return Map.of("paymentUrl", OzowService.createPaymentLink(tx.getReference(), tx.getAmount(), tx.getReturnUrl()));
            case "payfast":
                return Map.of("paymentUrl", PayFastService.createPaymentUrl(tx.getReference(), tx.getAmount(), tx.getReturnUrl(), tx.getNotifyUrl()));
            case "applepay":
                return ApplePayService.processPayment(tx.getAppleToken(), tx.getAmount());
            case "bluvoucher":
                boolean success = BluVoucherService.redeemVoucher(tx.getVoucherCode(), tx.getEmail());
                return Map.of("success", success);
            default:
                throw new IllegalArgumentException("Unsupported payment gateway: " + gateway);
        }
    }

    public static Map<String, Object> handleWebhook(String gateway, String rawJson, Map<String, String> headers) throws Exception {
        String gw = gateway != null ? gateway.toLowerCase() : "stripe";
        switch (gw) {
            case "stripe":
                return StripeService.handleWebhook(rawJson, headers);
            case "ozow":
                return OzowService.handleWebhook(rawJson, headers);
            case "payfast":
                return PayFastService.handleWebhook(rawJson, headers);
            case "applepay":
                return ApplePayService.handleWebhook(rawJson, headers);
            case "bluvoucher":
                return BluVoucherService.handleWebhook(rawJson, headers);
            default:
                throw new IllegalArgumentException("Unsupported payment gateway for webhook: " + gateway);
        }
    }
}
