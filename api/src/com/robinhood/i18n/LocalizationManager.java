package com.robinhood.i18n;

import com.robinhood.util.JSONParser;
import com.robinhood.util.Logger;

import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LocalizationManager {
    private static final String LANG_DIR = "src/com/robinhood/i18n/lang";
    private static final String DEFAULT_LOCALE = "en";

    private static final Map<String, Map<String, String>> bundles = new ConcurrentHashMap<>();
    private static final Set<String> availableLocales = ConcurrentHashMap.newKeySet();

    static {
        loadAvailableLocales();
    }

    private static void loadAvailableLocales() {
        try {
            File folder = new File(LANG_DIR);
            File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));

            if (files != null) {
                for (File f : files) {
                    String locale = f.getName().replaceFirst("\\.json$", "");
                    availableLocales.add(locale);
                }
                Logger.info("Loaded locales: " + availableLocales);
            } else {
                Logger.warn("No language files found in: " + LANG_DIR);
            }
        } catch (Exception e) {
            Logger.error("Failed to load locales from lang folder", e);
        }
    }

    public static String t(String key, String locale) {
        if (key == null || key.isBlank()) return "";

        String chosenLocale = (locale != null && availableLocales.contains(locale)) ? locale : DEFAULT_LOCALE;
        Map<String, String> bundle = bundles.computeIfAbsent(chosenLocale, LocalizationManager::loadBundle);

        // Attempt in preferred locale
        if (bundle.containsKey(key)) return bundle.get(key);

        // Fallback to default locale if not already default
        if (!chosenLocale.equals(DEFAULT_LOCALE)) {
            Map<String, String> fallback = bundles.computeIfAbsent(DEFAULT_LOCALE, LocalizationManager::loadBundle);
            return fallback.getOrDefault(key, key);
        }

        // Final fallback is just the key
        return key;
    }

    private static Map<String, String> loadBundle(String locale) {
        Path path = Path.of(LANG_DIR, locale + ".json");
        try (FileReader fr = new FileReader(path.toFile())) {
            Logger.info("Loading translation bundle: " + locale);
            return JSONParser.parseJsonToMap(fr);
        } catch (Exception e) {
            Logger.warn("Failed to load or parse: " + path + " → " + e.getMessage());
            return Map.of();
        }
    }

    public static Set<String> getAvailableLocales() {
        return Collections.unmodifiableSet(availableLocales);
    }
}
