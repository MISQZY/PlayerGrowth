package org.misqzy.playergrowth.common.lang;

public final class TimeFormatter {

    private TimeFormatter() {}

    public static String format(long totalSeconds, Messages messages) {
        if (totalSeconds <= 0) return "0 " + messages.raw("time.seconds");

        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) append(sb, days, messages.raw("time.days"));
        if (hours > 0) append(sb, hours, messages.raw("time.hours"));
        if (minutes > 0) append(sb, minutes, messages.raw("time.mins"));
        if (seconds > 0 || sb.isEmpty()) append(sb, seconds, messages.raw("time.seconds"));
        return sb.toString();
    }

    private static void append(StringBuilder sb, long value, String label) {
        if (!sb.isEmpty()) sb.append(' ');
        sb.append(value).append(' ').append(label);
    }
}
