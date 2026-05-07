package com.mcp.indexing.sprint0;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

final class Json {
    static String stringify(Object value) {
        StringBuilder sb = new StringBuilder(256);
        write(sb, value);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }

        if (value instanceof String s) {
            writeString(sb, s);
            return;
        }

        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
            return;
        }

        if (value instanceof Map<?, ?> m) {
            writeMap(sb, m);
            return;
        }

        if (value instanceof Collection<?> c) {
            writeCollection(sb, c);
            return;
        }

        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            sb.append('[');
            for (int i = 0; i < len; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(sb, Array.get(value, i));
            }
            sb.append(']');
            return;
        }

        if (value.getClass().isRecord()) {
            writeMap(sb, recordToMap(value));
            return;
        }

        writeString(sb, value.toString());
    }

    private static void writeMap(StringBuilder sb, Map<?, ?> m) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (!(e.getKey() instanceof String key)) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeString(sb, key);
            sb.append(':');
            write(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeCollection(StringBuilder sb, Collection<?> c) {
        sb.append('[');
        boolean first = true;
        for (Object v : c) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            write(sb, v);
        }
        sb.append(']');
    }

    private static Map<String, Object> recordToMap(Object record) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Map<String, Object> m = new LinkedHashMap<>(components.length);
        for (RecordComponent c : components) {
            try {
                m.put(c.getName(), c.getAccessor().invoke(record));
            } catch (ReflectiveOperationException e) {
                m.put(c.getName(), null);
            }
        }
        return m;
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
                }
            }
        }
        sb.append('"');
    }
}

