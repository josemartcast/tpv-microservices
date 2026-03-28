package com.tpv.desktop.tpv.app;

import com.tpv.desktop.api.auth.AuthApi;
import com.tpv.desktop.api.pos.BusinessProfileApi;
import com.tpv.desktop.api.pos.BusinessProfileResponse;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.domain.model.User;
import com.tpv.desktop.tpv.services.*;
import com.tpv.desktop.tpv.services.fake.*;
import com.tpv.desktop.tpv.services.local.DesktopComandaAutoPrintService;
import com.tpv.desktop.tpv.services.local.LocalPrintQueueService;
import com.tpv.desktop.tpv.services.real.RealApiClient;
import com.tpv.desktop.tpv.services.real.RealCatalogService;
import com.tpv.desktop.tpv.services.real.RealLockService;
import com.tpv.desktop.tpv.services.real.RealOrderService;
import com.tpv.desktop.tpv.services.real.RealTableService;
import com.tpv.desktop.tpv.diagnostics.MemoryDiagnostics;

public class AppContext {
    private static AppContext INSTANCE;

    public static AppContext get() {
        if (INSTANCE == null) {
            INSTANCE = new AppContext();
        }
        return INSTANCE;
    }

    private final AppState appState;
    private final ApiClient apiClient;
    private final CatalogService catalogService;
    private final LockService lockService;
    private final OrderService orderService;
    private final TableService tableService;
    private final BackendStatusService backendStatusService;
    private final PrintQueueService printQueueService;
    private final DesktopComandaAutoPrintService comandaAutoPrintService;
    private final MemoryDiagnostics memoryDiagnostics;
    private final boolean autoLoginEnabled;
    private final boolean kioskMode;

    private AppContext() {
        this.appState = new AppState();
        this.appState.terminalIdProperty().set(SettingsStore.getTerminalId());
        this.appState.activeCustomerProperty().set(SettingsStore.getActiveCustomer());
        String restaurantName = SettingsStore.getRestaurantName();
        if (restaurantName == null || restaurantName.isBlank()) {
            restaurantName = readConfig("TPV_RESTAURANT_NAME", "tpv.restaurant.name", "Restaurante EL GUSTO");
        }
        this.appState.restaurantNameProperty().set(restaurantName);
        this.kioskMode = readBoolean("TPV_KIOSK_MODE", "tpv.kiosk.mode", false);
        this.autoLoginEnabled = readBoolean("TPV_AUTO_LOGIN", "tpv.auto.login", kioskMode);
        this.appState.kioskModeProperty().set(kioskMode);

        RuntimeMode mode = readMode();
        if (mode == RuntimeMode.REAL || mode == RuntimeMode.AUTO) {
            boolean ready = ensureRealSession();
            if (ready || mode == RuntimeMode.REAL) {
                this.appState.runtimeModeProperty().set("REAL");
                this.apiClient = new RealApiClient();
                this.catalogService = new RealCatalogService();
                this.lockService = new RealLockService();
                this.orderService = new RealOrderService(catalogService);
                this.tableService = new RealTableService();
                this.backendStatusService = new FakeBackendStatusService(apiClient);
                this.printQueueService = new LocalPrintQueueService();
                this.comandaAutoPrintService = new DesktopComandaAutoPrintService(this.printQueueService);
                this.memoryDiagnostics = MemoryDiagnostics.startIfEnabled(this);
                syncBusinessProfileFromBackend();
                return;
            }
        }

        FakeDataStore store = new FakeDataStore();
        this.appState.runtimeModeProperty().set("FAKE");
        this.apiClient = new FakeApiClient();
        this.catalogService = new FakeCatalogService(store);
        this.lockService = new FakeLockService(store, appState);
        this.orderService = new FakeOrderService(store, catalogService);
        this.tableService = new FakeTableService(store, lockService, appState);
        this.backendStatusService = new FakeBackendStatusService(apiClient);
        this.printQueueService = new LocalPrintQueueService();
        this.comandaAutoPrintService = null;
        this.memoryDiagnostics = MemoryDiagnostics.startIfEnabled(this);
    }

    public AppState appState() { return appState; }
    public CatalogService catalogService() { return catalogService; }
    public LockService lockService() { return lockService; }
    public OrderService orderService() { return orderService; }
    public TableService tableService() { return tableService; }
    public BackendStatusService backendStatusService() { return backendStatusService; }
    public PrintQueueService printQueueService() { return printQueueService; }
    public DesktopComandaAutoPrintService comandaAutoPrintService() { return comandaAutoPrintService; }

    public void shutdown() {
        closeQuietly(memoryDiagnostics);
        closeQuietly(comandaAutoPrintService);
        closeQuietly(printQueueService);
        closeQuietly(backendStatusService);
    }

    private boolean ensureRealSession() {
        if (AuthStore.isLoggedIn()) {
            return true;
        }
        if (!autoLoginEnabled) {
            return false;
        }
        String user = resolveAuthUser();
        String pass = resolveAuthPass();
        try {
            var login = AuthApi.login(user, pass);
            if (login == null || login.accessToken() == null || login.accessToken().isBlank()) {
                return false;
            }
            AuthStore.setToken(login.accessToken());
            appState.activeUserProperty().set(new User(1, user, initials(user)));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String initials(String user) {
        if (user == null || user.isBlank()) return "TPV";
        String u = user.trim().toUpperCase();
        return u.length() <= 2 ? u : u.substring(0, 2);
    }

    private static String readConfig(String env, String prop, String fallback) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) {
            value = System.getProperty(prop);
        }
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    private static RuntimeMode readMode() {
        String value = readConfig("TPV_MODE", "tpv.mode", "fake").toLowerCase();
        return switch (value) {
            case "real" -> RuntimeMode.REAL;
            case "auto" -> RuntimeMode.AUTO;
            default -> RuntimeMode.FAKE;
        };
    }

    private static boolean readBoolean(String env, String prop, boolean fallback) {
        String raw = readConfig(env, prop, Boolean.toString(fallback));
        String v = raw.trim().toLowerCase();
        return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v);
    }

    private String resolveAuthUser() {
        if (kioskMode) {
            String kioskUser = readConfig("TPV_KIOSK_AUTH_USER", "tpv.kiosk.auth.user", "");
            if (!kioskUser.isBlank()) return kioskUser;
        }
        return readConfig("TPV_AUTH_USER", "tpv.auth.user", "admin");
    }

    private String resolveAuthPass() {
        if (kioskMode) {
            String kioskPass = readConfig("TPV_KIOSK_AUTH_PASS", "tpv.kiosk.auth.pass", "");
            if (!kioskPass.isBlank()) return kioskPass;
        }
        return readConfig("TPV_AUTH_PASS", "tpv.auth.pass", "admin123");
    }

    private void syncBusinessProfileFromBackend() {
        try {
            BusinessProfileResponse profile = BusinessProfileApi.get();
            if (profile == null) {
                return;
            }
            String businessName = normalizeOrDefault(profile.businessName(), "Restaurante EL GUSTO");
            SettingsStore.setRestaurantName(businessName);
            SettingsStore.setFiscalLegalName(normalizeOrDefault(profile.legalName(), businessName));
            SettingsStore.setFiscalTaxId(normalize(profile.taxId()).toUpperCase());
            SettingsStore.setFiscalAddress(normalize(profile.address()));
            SettingsStore.setFiscalPostalCode(normalize(profile.postalCode()));
            SettingsStore.setFiscalCity(normalize(profile.city()));
            SettingsStore.setFiscalProvince(normalize(profile.province()));
            SettingsStore.setFiscalCountry(normalizeOrDefault(profile.country(), "ES").toUpperCase());
            SettingsStore.setFiscalPhone(normalize(profile.phone()));
            SettingsStore.setFiscalEmail(normalize(profile.email()).toLowerCase());
            this.appState.restaurantNameProperty().set(businessName);
        } catch (Exception ignored) {
            // Keep local settings as fallback when backend profile is unavailable.
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeOrDefault(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private enum RuntimeMode {
        FAKE,
        REAL,
        AUTO
    }

    private static void closeQuietly(Object candidate) {
        if (!(candidate instanceof AutoCloseable closeable)) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // Best effort shutdown.
        }
    }
}


