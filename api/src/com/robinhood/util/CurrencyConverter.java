package com.robinhood.util;

import java.io.FileReader;
import java.util.Map;
import com.google.gson.Gson; // include minimal JSON lib if allowed

public class CurrencyConverter {
    private static Map<String, Double> rates;

    static {
        try (FileReader reader = new FileReader("config/rates.json")) {
            rates = new Gson().fromJson(reader, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load exchange rates", e);
        }
    }

    /** Convert amount from base (tokens) to target currency. */
    public static double toCurrency(int tokens, String currencyCode) {
        Double rate = rates.get(currencyCode);
        if (rate == null) throw new IllegalArgumentException("Unsupported currency: " + currencyCode);
        return tokens * rate;
    }

    /** Convert an amount in currency back to tokens (round down). */
    public static int fromCurrency(double amount, String currencyCode) {
        Double rate = rates.get(currencyCode);
        if (rate == null) throw new IllegalArgumentException("Unsupported currency: " + currencyCode);
        return (int) Math.floor(amount / rate);
    }
}
