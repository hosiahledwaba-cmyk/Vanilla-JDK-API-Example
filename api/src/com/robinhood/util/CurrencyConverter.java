package com.robinhood.util;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class CurrencyConverter {
    private static final Map<String, Double> rates = new HashMap<>();

    static {
        // Defaults in case config file cannot be found
        rates.put("ZAR", 1.0);
        rates.put("USD", 0.055);
        rates.put("EUR", 0.050);
        rates.put("INR", 4.5);

        // Try loading from possible file locations or classpath
        String[] possiblePaths = {
            "config/rates.json",
            "api/src/com/robinhood/config/rates.json",
            "src/com/robinhood/config/rates.json"
        };

        boolean loaded = false;
        for (String p : possiblePaths) {
            File f = new File(p);
            if (f.exists()) {
                try (FileReader reader = new FileReader(f)) {
                    Map<String, String> parsed = JSONParser.parseJsonToMap(reader);
                    for (Map.Entry<String, String> entry : parsed.entrySet()) {
                        rates.put(entry.getKey(), Double.parseDouble(entry.getValue()));
                    }
                    loaded = true;
                    break;
                } catch (Exception ignored) {}
            }
        }

        if (!loaded) {
            try (InputStream is = CurrencyConverter.class.getResourceAsStream("/com/robinhood/config/rates.json")) {
                if (is != null) {
                    Map<String, String> parsed = JSONParser.parseJsonToMap(new InputStreamReader(is));
                    for (Map.Entry<String, String> entry : parsed.entrySet()) {
                        rates.put(entry.getKey(), Double.parseDouble(entry.getValue()));
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    /** Convert amount from base (tokens) to target currency. */
    public static double toCurrency(int tokens, String currencyCode) {
        Double rate = rates.get(currencyCode != null ? currencyCode.toUpperCase() : "ZAR");
        if (rate == null) rate = 1.0;
        return tokens * rate;
    }

    /** Convert an amount in currency back to tokens (round down). */
    public static int fromCurrency(double amount, String currencyCode) {
        Double rate = rates.get(currencyCode != null ? currencyCode.toUpperCase() : "ZAR");
        if (rate == null || rate == 0) rate = 1.0;
        return (int) Math.floor(amount / rate);
    }
}
