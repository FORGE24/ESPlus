package com.esplus.panel;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal cron parser for typical Minecraft server scheduling.
 * Supports 6 fields: second minute hour day-of-month month day-of-week (optional seconds).
 * Also accepts common shorthands like @hourly, @daily.
 */
public final class CronUtil {
    private CronUtil() {}

    public static long nextRunMillis(String cron, long afterMs) {
        Cron cronObj = parse(cron);
        if (cronObj == null) return -1;
        LocalDateTime base = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(afterMs), ZoneId.systemDefault()).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime next = base.plusSeconds(1);
        for (int i = 0; i < 366 * 24 * 60 * 60; i++) { // search up to ~1 year
            if (cronObj.matches(next)) {
                return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            }
            next = next.plusSeconds(1);
        }
        return -1;
    }

    private static Cron parse(String expr) {
        if (expr == null || expr.isBlank()) return null;
        String e = expr.trim();
        e = switch (e.toLowerCase()) {
            case "@yearly", "@annually" -> "0 0 0 1 1 *";
            case "@monthly" -> "0 0 0 1 * *";
            case "@weekly" -> "0 0 0 * * 0";
            case "@daily", "@midnight" -> "0 0 0 * * *";
            case "@hourly" -> "0 0 * * * *";
            default -> e;
        };
        String[] parts = e.split("\\s+");
        if (parts.length == 5) {
            // classic cron without seconds
            String[] six = new String[6];
            six[0] = "0";
            System.arraycopy(parts, 0, six, 1, 5);
            parts = six;
        }
        if (parts.length != 6) return null;
        try {
            return new Cron(
                    parseField(parts[0], 0, 59),
                    parseField(parts[1], 0, 59),
                    parseField(parts[2], 0, 23),
                    parseField(parts[3], 1, 31),
                    parseField(parts[4], 1, 12),
                    parseField(parts[5], 0, 7)
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<Integer> parseField(String s, int min, int max) {
        List<Integer> vals = new ArrayList<>();
        if (s.equals("*") || s.equals("?")) {
            for (int i = min; i <= max; i++) vals.add(i);
            return vals;
        }
        // support day-of-week 7 -> 0
        int effectiveMax = max;
        if (min == 0 && max == 7) effectiveMax = 7;
        for (String part : s.split(",")) {
            if (part.contains("/")) {
                String[] baseStep = part.split("/");
                int step = Integer.parseInt(baseStep[1]);
                int start = min;
                int end = effectiveMax;
                if (!baseStep[0].equals("*")) {
                    if (baseStep[0].contains("-")) {
                        String[] se = baseStep[0].split("-");
                        start = Integer.parseInt(se[0]);
                        end = Integer.parseInt(se[1]);
                    } else {
                        start = Integer.parseInt(baseStep[0]);
                    }
                }
                for (int i = start; i <= end; i += step) vals.add(normalize(i, min, max));
            } else if (part.contains("-")) {
                String[] se = part.split("-");
                int start = Integer.parseInt(se[0]);
                int end = Integer.parseInt(se[1]);
                for (int i = start; i <= end; i++) vals.add(normalize(i, min, max));
            } else {
                vals.add(normalize(Integer.parseInt(part), min, max));
            }
        }
        return vals.stream().distinct().sorted().toList();
    }

    private static int normalize(int v, int min, int max) {
        if (min == 0 && max == 7 && v == 7) return 0; // Sunday both 0 and 7
        return v;
    }

    private record Cron(List<Integer> seconds, List<Integer> minutes, List<Integer> hours,
                        List<Integer> daysOfMonth, List<Integer> months, List<Integer> daysOfWeek) {
        boolean matches(LocalDateTime dt) {
            return seconds.contains(dt.getSecond())
                    && minutes.contains(dt.getMinute())
                    && hours.contains(dt.getHour())
                    && months.contains(dt.getMonthValue())
                    && (daysOfMonth.contains(dt.getDayOfMonth()) || daysOfWeek.contains(dt.getDayOfWeek().getValue()));
        }
    }
}
