package com.gathin.weatherram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

/**
 * The RAM percentage, read once from /proc/meminfo: used over total, in whole percent. Old kernels lack
 * MemAvailable, so fall back to free plus buffers plus cached, capped at the total.
 */
final class Ram {
    private Ram() { }

    /** [available MB, total MB], each -1 when the file does not say. */
    static long[] readMb(Reader source) throws IOException {
        BufferedReader reader = new BufferedReader(source);
        long total = -1, available = -1, free = -1, buffers = -1, cached = -1;
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 3 || !"kB".equals(fields[2])) continue;
            long value;
            try { value = Long.parseLong(fields[1]); }
            catch (NumberFormatException ignored) { continue; }
            if (value < 0) continue;
            switch (fields[0]) {
                case "MemTotal:": total = value; break;
                case "MemAvailable:": available = value; break;
                case "MemFree:": free = value; break;
                case "Buffers:": buffers = value; break;
                case "Cached:": cached = value; break;
            }
        }
        if (available < 0 && free >= 0 && buffers >= 0 && cached >= 0) {
            try { available = Math.addExact(Math.addExact(free, buffers), cached); }
            catch (ArithmeticException ignored) { available = -1; }
        }
        if (available >= 0 && total > 0) available = Math.min(available, total);
        return new long[]{available < 0 ? -1 : available / 1024, total < 0 ? -1 : total / 1024};
    }

    /** Whole percent of the RAM in use, or -1 when the totals are unknown. */
    static int usedPercent(long availableMb, long totalMb) {
        if (availableMb < 0 || totalMb <= 0) return -1;
        return (int) Math.round(100.0 * (totalMb - availableMb) / totalMb);
    }

    /** The total as "1.5 GB" (or "512 MB" under a gigabyte), Locale-independent. */
    static String totalLabel(long totalMb) {
        if (totalMb < 0) return "";
        if (totalMb >= 1024) return String.format(java.util.Locale.ROOT, "%.1f GB", totalMb / 1024.0);
        return totalMb + " MB";
    }
}
