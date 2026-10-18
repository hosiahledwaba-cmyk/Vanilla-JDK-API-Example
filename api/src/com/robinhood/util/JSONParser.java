package com.robinhood.util;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.*;

public class JSONParser {

    /**
     * Parses simple JSON object string into Map<String, String>
     */
    public static Map<String, String> parse(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.isBlank()) return map;

        String trimmed = json.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }

        if (trimmed.isEmpty()) return map;

        List<String> pairs = splitTopLevel(trimmed, ',');
        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx > 0) {
                String key = cleanToken(pair.substring(0, colonIdx));
                String val = cleanToken(pair.substring(colonIdx + 1));
                map.put(key, val);
            }
        }
        return map;
    }

    /**
     * Reads JSON from a Reader and parses into Map<String, String>
     */
    public static Map<String, String> parseJsonToMap(Reader reader) {
        if (reader == null) return Collections.emptyMap();
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader br = (reader instanceof BufferedReader) ? (BufferedReader) reader : new BufferedReader(reader);
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return parse(sb.toString());
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /**
     * Stringify any Object (Map, List, primitives, String, arrays) to JSON
     */
    public static String stringify(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String || obj instanceof Character) {
            return "\"" + escapeString(obj.toString()) + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeString(String.valueOf(entry.getKey()))).append("\":");
                sb.append(stringify(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Collection<?> col) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                first = false;
                sb.append(stringify(item));
            }
            sb.append("]");
            return sb.toString();
        }
        if (obj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(obj);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(stringify(java.lang.reflect.Array.get(obj, i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeString(obj.toString()) + "\"";
    }

    private static String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String cleanToken(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static List<String> splitTopLevel(String str, char delimiter) {
        List<String> list = new ArrayList<>();
        boolean inQuotes = false;
        int depth = 0;
        int start = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (c == '\"' && (i == 0 || str.charAt(i - 1) != '\\')) {
                inQuotes = !inQuotes;
            } else if (!inQuotes) {
                if (c == '{' || c == '[') {
                    depth++;
                } else if (c == '}' || c == ']') {
                    depth--;
                } else if (c == delimiter && depth == 0) {
                    list.add(str.substring(start, i).trim());
                    start = i + 1;
                }
            }
        }
        if (start < str.length()) {
            list.add(str.substring(start).trim());
        }
        return list;
    }
}
