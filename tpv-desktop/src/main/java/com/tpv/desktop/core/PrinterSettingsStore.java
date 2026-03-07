package com.tpv.desktop.core;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.prefs.Preferences;

public final class PrinterSettingsStore {
    private static final Preferences PREFS = Preferences.userNodeForPackage(SettingsStore.class);
    private static final String KEY_PRINTER_PROFILES = "printerProfiles.v1";
    private static final Base64.Encoder B64_ENC = Base64.getEncoder();
    private static final Base64.Decoder B64_DEC = Base64.getDecoder();

    private static final String DEST_BAR = "BAR";
    private static final String DEST_COCINA = "COCINA";
    private static final String DEST_POSTRES = "POSTRES";
    private static final String DEST_ALL = "ALL";

    private PrinterSettingsStore() {
    }

    public static List<String> supportedDestinations() {
        return List.of(DEST_BAR, DEST_COCINA, DEST_POSTRES, DEST_ALL);
    }

    public static List<PrinterProfile> getProfiles() {
        String raw = PREFS.get(KEY_PRINTER_PROFILES, "");
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        List<PrinterProfile> out = new ArrayList<>();
        String[] lines = raw.split("\n");
        for (String line : lines) {
            PrinterProfile p = parseLine(line);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    public static void saveProfiles(List<PrinterProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            PREFS.put(KEY_PRINTER_PROFILES, "");
            return;
        }
        StringBuilder out = new StringBuilder();
        for (PrinterProfile p : profiles) {
            if (p == null) {
                continue;
            }
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(encodeLine(p));
        }
        PREFS.put(KEY_PRINTER_PROFILES, out.toString());
    }

    public static List<String> resolveSystemPrintersForDestination(String destination) {
        String normalized = normalizeDestination(destination);
        List<PrinterProfile> profiles = getProfiles();
        List<String> out = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();

        for (PrinterProfile p : profiles) {
            if (!p.enabled()) {
                continue;
            }
            if (!normalized.equals(p.destination())) {
                continue;
            }
            String printer = normalize(p.systemPrinter());
            if (printer.isBlank()) {
                continue;
            }
            String key = printer.toLowerCase(Locale.ROOT);
            if (dedupe.add(key)) {
                out.add(printer);
            }
        }

        if (!out.isEmpty() || DEST_ALL.equals(normalized)) {
            return out;
        }

        for (PrinterProfile p : profiles) {
            if (!p.enabled() || !DEST_ALL.equals(p.destination())) {
                continue;
            }
            String printer = normalize(p.systemPrinter());
            if (printer.isBlank()) {
                continue;
            }
            String key = printer.toLowerCase(Locale.ROOT);
            if (dedupe.add(key)) {
                out.add(printer);
            }
        }
        return out;
    }

    public static PrinterProfile newProfile(String logicalName, String destination, String systemPrinter, boolean enabled) {
        return new PrinterProfile(
                UUID.randomUUID().toString(),
                normalize(logicalName),
                normalizeDestination(destination),
                normalize(systemPrinter),
                enabled
        );
    }

    private static String encodeLine(PrinterProfile p) {
        return normalize(p.id())
                + "|"
                + normalizeDestination(p.destination())
                + "|"
                + (p.enabled() ? "1" : "0")
                + "|"
                + encodeB64(normalize(p.logicalName()))
                + "|"
                + encodeB64(normalize(p.systemPrinter()));
    }

    private static PrinterProfile parseLine(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 5) {
            return null;
        }
        String id = normalize(parts[0]);
        String destination = normalizeDestination(parts[1]);
        boolean enabled = "1".equals(parts[2]);
        String logicalName = decodeB64(parts[3]);
        String systemPrinter = decodeB64(parts[4]);
        if (id.isBlank()) {
            return null;
        }
        return new PrinterProfile(id, logicalName, destination, systemPrinter, enabled);
    }

    private static String encodeB64(String value) {
        return B64_ENC.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeB64(String value) {
        try {
            byte[] decoded = B64_DEC.decode(value);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeDestination(String value) {
        String v = normalize(value).toUpperCase(Locale.ROOT);
        if (supportedDestinations().contains(v)) {
            return v;
        }
        return DEST_ALL;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public record PrinterProfile(
            String id,
            String logicalName,
            String destination,
            String systemPrinter,
            boolean enabled
    ) {
    }
}
