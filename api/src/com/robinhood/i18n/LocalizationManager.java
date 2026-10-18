package com.robinhood.i18n;

import com.robinhood.util.JSONParser;
import com.robinhood.util.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LocalizationManager {
    private static final String[] POSSIBLE_LANG_DIRS = {
        "api/src/com/robinhood/i18n/lang",
        "src/com/robinhood/i18n/lang"
    };
    private static String resolvedLangDir = null;
    private static final String DEFAULT_LOCALE = "en";

    private static final Map<String, Map<String, String>> bundles = new ConcurrentHashMap<>();
    private static final Set<String> availableLocales = ConcurrentHashMap.newKeySet();

    static {
        loadAvailableLocales();
    }

    private static void loadAvailableLocales() {
        try {
            for (String dirPath : POSSIBLE_LANG_DIRS) {
                File folder = new File(dirPath);
                if (folder.exists() && folder.isDirectory()) {
                    resolvedLangDir = dirPath;
                    File[] files = folder.listFiles((dir, name) -> name.endsWith(".json"));
                    if (files != null) {
                        for (File f : files) {
                            String locale = f.getName().replaceFirst("\\.json$", "");
                            availableLocales.add(locale);
                        }
                    }
                    break;
                }
            }

            if (availableLocales.isEmpty()) {
                // Default fallback locales
                availableLocales.addAll(List.of("en", "af", "nr", "nso", "ss", "tn", "xh", "zu"));
            }
            Logger.info("Loaded locales: " + availableLocales);
        } catch (Exception e) {
            Logger.error("Failed to load locales from lang folder", e);
        }
    }

    public static String t(String key, String locale) {
        if (key == null || key.isBlank()) return "";

        String chosenLocale = (locale != null && availableLocales.contains(locale)) ? locale : DEFAULT_LOCALE;
        Map<String, String> bundle = bundles.computeIfAbsent(chosenLocale, LocalizationManager::loadBundle);

        if (bundle.containsKey(key)) return bundle.get(key);

        if (!chosenLocale.equals(DEFAULT_LOCALE)) {
            Map<String, String> fallback = bundles.computeIfAbsent(DEFAULT_LOCALE, LocalizationManager::loadBundle);
            return fallback.getOrDefault(key, key);
        }

        return key;
    }

    private static Map<String, String> loadBundle(String locale) {
        if (resolvedLangDir != null) {
            Path path = Path.of(resolvedLangDir, locale + ".json");
            File f = path.toFile();
            if (f.exists()) {
                try (FileReader fr = new FileReader(f)) {
                    return JSONParser.parseJsonToMap(fr);
                } catch (Exception ignored) {}
            }
        }

        // Try classpath resource
        try (InputStream is = LocalizationManager.class.getResourceAsStream("/com/robinhood/i18n/lang/" + locale + ".json")) {
            if (is != null) {
                return JSONParser.parseJsonToMap(new InputStreamReader(is));
            }
        } catch (Exception ignored) {}

        return Map.of();
    }

    public static Set<String> getAvailableLocales() {
        return Collections.unmodifiableSet(availableLocales);
    }
}
