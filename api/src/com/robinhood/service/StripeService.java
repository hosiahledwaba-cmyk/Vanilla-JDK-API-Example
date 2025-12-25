package com.robinhood.service;

import com.robinhood.util.ConfigLoader;
import com.robinhood.util.TimeUtils;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

import java.util.List;
import java.util.Map;

public class StripeService {
    static {
        Stripe.apiKey = ConfigLoader.get("stripe.apiKey");
    }

    public static PaymentIntent createPaymentIntent(int amount, String currency, String customerEmail)
            throws StripeException {
        return PaymentIntent.create(Map.of(
                "amount", amount,
                "currency", currency,
                "receipt_email", customerEmail,
                "payment_method_types", List.of("card")
        ));
    }

    public static PaymentIntent retrieve(String piId) throws StripeException {
        return PaymentIntent.retrieve(piId);
    }
}
