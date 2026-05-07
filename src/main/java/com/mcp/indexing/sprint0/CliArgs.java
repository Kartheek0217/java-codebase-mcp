package com.mcp.indexing.sprint0;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class CliArgs {
    private final Map<String, List<String>> values;

    private CliArgs(Map<String, List<String>> values) {
        this.values = values;
    }

    static CliArgs parse(String[] args) {
        Map<String, List<String>> values = new HashMap<>();
        String pendingKey = null;

        for (String arg : args) {
            if (pendingKey != null) {
                put(values, pendingKey, arg);
                pendingKey = null;
                continue;
            }

            if (arg.startsWith("--") && arg.contains("=")) {
                int idx = arg.indexOf('=');
                String key = arg.substring(2, idx);
                String value = arg.substring(idx + 1);
                put(values, key, value);
                continue;
            }

            if (arg.startsWith("--")) {
                pendingKey = arg.substring(2);
                continue;
            }

            put(values, "_", arg);
        }

        if (pendingKey != null) {
            put(values, pendingKey, "true");
        }

        return new CliArgs(values);
    }

    List<String> getAll(String key) {
        return values.getOrDefault(key, List.of());
    }

    String getFirstOrNull(String key) {
        List<String> v = values.get(key);
        if (v == null || v.isEmpty()) {
            return null;
        }
        return v.getFirst();
    }

    int getInt(String key, int defaultValue) {
        String raw = getFirstOrNull(key);
        if (raw == null) {
            return defaultValue;
        }
        return Integer.parseInt(raw);
    }

    private static void put(Map<String, List<String>> values, String key, String value) {
        values.computeIfAbsent(key, _k -> new ArrayList<>()).add(value);
    }
}

