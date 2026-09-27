package portfolio;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lightweight, zero-dependency JSON parser and serializer.
 * Handles objects, arrays, strings, numbers, booleans, and null.
 */
public final class SimpleJson {

    private SimpleJson() {}

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static Object parse(String json) {
        if (json == null) return null;
        Parser parser = new Parser(json.trim());
        return parser.parseValue();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object val = parse(json);
        if (val instanceof Map) {
            return (Map<String, Object>) val;
        }
        return new LinkedHashMap<>();
    }

    private static class Parser {
        private final String src;
        private int idx = 0;
        private final int len;

        Parser(String src) {
            this.src = src;
            this.len = src.length();
        }

        private void skipWhitespace() {
            while (idx < len) {
                char c = src.charAt(idx);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    idx++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (idx >= len) return null;
            char c = src.charAt(idx);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') return parseNull();
            if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
            throw new IllegalArgumentException("Unexpected character at " + idx + ": " + c);
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            idx++; // consume '{'
            skipWhitespace();
            if (idx < len && src.charAt(idx) == '}') {
                idx++;
                return map;
            }
            while (idx < len) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (idx >= len || src.charAt(idx) != ':') {
                    throw new IllegalArgumentException("Expected ':' after key at " + idx);
                }
                idx++; // consume ':'
                Object val = parseValue();
                map.put(key, val);
                skipWhitespace();
                if (idx < len && src.charAt(idx) == ',') {
                    idx++;
                } else if (idx < len && src.charAt(idx) == '}') {
                    idx++;
                    break;
                } else {
                    break;
                }
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            idx++; // consume '['
            skipWhitespace();
            if (idx < len && src.charAt(idx) == ']') {
                idx++;
                return list;
            }
            while (idx < len) {
                Object val = parseValue();
                list.add(val);
                skipWhitespace();
                if (idx < len && src.charAt(idx) == ',') {
                    idx++;
                } else if (idx < len && src.charAt(idx) == ']') {
                    idx++;
                    break;
                } else {
                    break;
                }
            }
            return list;
        }

        String parseString() {
            skipWhitespace();
            if (idx >= len || src.charAt(idx) != '"') {
                throw new IllegalArgumentException("Expected string at " + idx);
            }
            idx++; // consume '"'
            StringBuilder sb = new StringBuilder();
            while (idx < len) {
                char c = src.charAt(idx++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (idx >= len) break;
                    char esc = src.charAt(idx++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (idx + 4 <= len) {
                                String hex = src.substring(idx, idx + 4);
                                sb.append((char) Integer.parseInt(hex, 16));
                                idx += 4;
                            }
                            break;
                        default:
                            sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (src.startsWith("true", idx)) {
                idx += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", idx)) {
                idx += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Expected boolean at " + idx);
        }

        Object parseNull() {
            if (src.startsWith("null", idx)) {
                idx += 4;
                return null;
            }
            throw new IllegalArgumentException("Expected null at " + idx);
        }

        Number parseNumber() {
            int start = idx;
            if (src.charAt(idx) == '-') idx++;
            while (idx < len && Character.isDigit(src.charAt(idx))) idx++;
            boolean isDouble = false;
            if (idx < len && src.charAt(idx) == '.') {
                isDouble = true;
                idx++;
                while (idx < len && Character.isDigit(src.charAt(idx))) idx++;
            }
            if (idx < len && (src.charAt(idx) == 'e' || src.charAt(idx) == 'E')) {
                isDouble = true;
                idx++;
                if (idx < len && (src.charAt(idx) == '+' || src.charAt(idx) == '-')) idx++;
                while (idx < len && Character.isDigit(src.charAt(idx))) idx++;
            }
            String numStr = src.substring(start, idx);
            if (isDouble) {
                return Double.parseDouble(numStr);
            } else {
                return Long.parseLong(numStr);
            }
        }
    }
}
