package org.misqzy.playerGrowth.lang;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlaceholderUtil {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{([^}]+)\\}");

    public static String format(String message, Map<String, Object> attributes) {
        if (message == null || attributes == null) {
            return message;
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = attributes.get(key);

            if (value != null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(value.toString()));
            } else {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
            }
        }
        matcher.appendTail(result);

        return result.toString();
    }

    public static String format(String message, String key, Object value) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(key, value);
        return format(message, attributes);
    }
}
