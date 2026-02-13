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
    String overridden = readTerminalOverride();
    if (overridden != null) {
      return overridden;
    }
    String def = defaultTerminalId();
    return prefs.get("terminalId", def);
  }

  public static void setTerminalId(String terminalId) {
    prefs.put("terminalId", terminalId);
  }

  public static void clearTerminalId() {
    prefs.remove("terminalId");
  }

  public static String getActiveCustomer() {
    return prefs.get("activeCustomer", "Mostrador");
  }

  public static void setActiveCustomer(String customer) {
    if (customer == null || customer.isBlank()) {
      prefs.put("activeCustomer", "Mostrador");
      return;
    }
    prefs.put("activeCustomer", customer.trim());
  }

  public static String getBusinessName() {
    return getRestaurantName();
  }

  public static void setBusinessName(String businessName) {
    setRestaurantName(businessName);
  }

  public static String getRestaurantName() {
    return prefs.get("restaurantName", "Restaurante EL GUSTO");
  }

  public static void setRestaurantName(String value) {
    if (value == null || value.isBlank()) {
      prefs.put("restaurantName", "Restaurante EL GUSTO");
      return;
    }
    prefs.put("restaurantName", value.trim());
  }

  public static String getFiscalLegalName() {
    return prefs.get("fiscalLegalName", getRestaurantName());
  }

  public static void setFiscalLegalName(String value) {
    if (value == null || value.isBlank()) {
      prefs.put("fiscalLegalName", getRestaurantName());
      return;
    }
    prefs.put("fiscalLegalName", value.trim());
  }

  public static String getFiscalTaxId() {
    return prefs.get("fiscalTaxId", "");
  }

  public static void setFiscalTaxId(String value) {
    prefs.put("fiscalTaxId", value == null ? "" : value.trim().toUpperCase());
  }

  public static String getFiscalAddress() {
    return prefs.get("fiscalAddress", "");
  }

  public static void setFiscalAddress(String value) {
    prefs.put("fiscalAddress", value == null ? "" : value.trim());
  }

  public static String getFiscalPostalCode() {
    return prefs.get("fiscalPostalCode", "");
  }

  public static void setFiscalPostalCode(String value) {
    prefs.put("fiscalPostalCode", value == null ? "" : value.trim());
  }

  public static String getFiscalCity() {
    return prefs.get("fiscalCity", "");
  }

  public static void setFiscalCity(String value) {
    prefs.put("fiscalCity", value == null ? "" : value.trim());
  }

  public static String getFiscalProvince() {
    return prefs.get("fiscalProvince", "");
  }

  public static void setFiscalProvince(String value) {
    prefs.put("fiscalProvince", value == null ? "" : value.trim());
  }

  public static String getFiscalCountry() {
    return prefs.get("fiscalCountry", "ES");
  }

  public static void setFiscalCountry(String value) {
    if (value == null || value.isBlank()) {
      prefs.put("fiscalCountry", "ES");
      return;
    }
    prefs.put("fiscalCountry", value.trim().toUpperCase());
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

  private static String readTerminalOverride() {
    String env = System.getenv("TPV_TERMINAL_ID");
    if (env != null && !env.isBlank()) {
      return env.trim();
    }
    String prop = System.getProperty("tpv.terminal.id");
    if (prop != null && !prop.isBlank()) {
      return prop.trim();
    }
    return null;
  }
}
