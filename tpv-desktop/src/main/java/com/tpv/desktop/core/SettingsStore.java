package com.tpv.desktop.core;

import java.net.InetAddress;
import java.util.prefs.Preferences;

public final class SettingsStore {
  private static final Preferences prefs = Preferences.userNodeForPackage(SettingsStore.class);

  private SettingsStore() {}

  public static String getApiBaseUrl() {
    return prefs.get("apiBaseUrl", "http://localhost:8080");
  }

  public static void setApiBaseUrl(String url) {
    prefs.put("apiBaseUrl", url);
  }

  public static String getTerminalId() {
    String def = defaultTerminalId();
    return prefs.get("terminalId", def);
  }

  public static void setTerminalId(String terminalId) {
    prefs.put("terminalId", terminalId);
  }

  public static void clearTerminalId() {
    prefs.remove("terminalId");
  }

  private static String defaultTerminalId() {
    try {
      String host = InetAddress.getLocalHost().getHostName();
      if (host != null && !host.isBlank()) return host;
    } catch (Exception ignored) {}
    String env = System.getenv("COMPUTERNAME");
    if (env != null && !env.isBlank()) return env;
    return "TERMINAL-1";
  }
}
