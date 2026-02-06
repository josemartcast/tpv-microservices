package com.tpv.desktop.core;

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
}
