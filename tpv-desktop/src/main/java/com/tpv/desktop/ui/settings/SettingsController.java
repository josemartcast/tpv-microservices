package com.tpv.desktop.ui.settings;

import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.core.Nav;
import com.tpv.desktop.core.AuthStore; // ajusta el nombre si tu clase se llama distinto
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class SettingsController {

  @FXML private TextField apiBaseUrlField;
  @FXML private Label statusLabel;

  @FXML
  public void initialize() {
    apiBaseUrlField.setText(SettingsStore.getApiBaseUrl());
  }

  @FXML
  public void onSave() {
    String url = apiBaseUrlField.getText() == null ? "" : apiBaseUrlField.getText().trim();
    if (url.isBlank() || !(url.startsWith("http://") || url.startsWith("https://"))) {
      statusLabel.setText("URL no válida. Ejemplo: http://localhost:8080");
      return;
    }
    SettingsStore.setApiBaseUrl(url);
    statusLabel.setText("Guardado.");
  }

  @FXML
  public void onTest() {
    statusLabel.setText("");
    try {
      // Test sencillo: si responde, baseUrl + token OK
      CashApi.current();
      statusLabel.setText("Conexión OK.");
    } catch (Exception e) {
      statusLabel.setText("Fallo conexión/auth: " + e.getMessage());
    }
  }

  @FXML
  public void onLogout() {
    AuthStore.clear(); // ajusta si tu método se llama distinto
    Nav.goToLogin();
  }
}
