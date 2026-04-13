package com.tpv.desktop;

import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.core.AuthStore;
import javafx.application.Application;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class App extends Application {
  @Override
  public void start(Stage stage) {
    AppContext context = AppContext.get();
    context.appState().touchModeProperty().set(resolveTouchMode());
    context.appState().kioskModeProperty().set(resolveKioskMode());
    Navigator.init(stage);
    boolean needsLogin = "REAL".equalsIgnoreCase(context.appState().runtimeModeProperty().get())
            && !AuthStore.isLoggedIn();
    if (needsLogin) {
      Navigator.get().goLogin();
      return;
    }
    Navigator.get().goHome();
  }

  @Override
  public void stop() {
    AppContext.get().shutdown();
  }

  public static void main(String[] args) {
    launch(args);
  }

  private boolean resolveTouchMode() {
    String raw = System.getenv("TPV_TOUCH_MODE");
    if (raw == null || raw.isBlank()) {
      raw = System.getProperty("tpv.touch.mode");
    }
    if (raw != null && !raw.isBlank()) {
      String v = raw.trim().toLowerCase();
      if ("1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v)) return true;
      if ("0".equals(v) || "false".equals(v) || "no".equals(v) || "off".equals(v)) return false;
    }
    // Auto: habilita touch mode en resoluciones más justas (p.ej. 1366x768).
    return Screen.getPrimary().getVisualBounds().getWidth() <= 1400;
  }

  private boolean resolveKioskMode() {
    String raw = System.getenv("TPV_KIOSK_MODE");
    if (raw == null || raw.isBlank()) {
      raw = System.getProperty("tpv.kiosk.mode");
    }
    if (raw == null || raw.isBlank()) {
      return false;
    }
    String v = raw.trim().toLowerCase();
    return "1".equals(v) || "true".equals(v) || "yes".equals(v) || "on".equals(v);
  }
}
