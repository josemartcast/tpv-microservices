package com.tpv.desktop.ui.layout;

import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.Nav;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class MainLayoutController {

    @FXML
    private StackPane content;

    @FXML
    public void goSales() {
        loadCenter("/fxml/sales/SalesView.fxml");
    }

    @FXML
    public void goCash() {
        loadCenter("/fxml/cash/CashView.fxml");
    }

    @FXML
    public void goHistory() {
        loadCenter("/fxml/history/HistoryView.fxml");
    }

    @FXML
    public void goFiscal() {
        loadCenter("/fxml/fiscal/FiscalView.fxml");
    }

    @FXML
    public void goSettings() {
        loadCenter("/fxml/settings/SettingsView.fxml");
    }

    @FXML
    public void goFiscalClosure() {
        loadCenter("/fxml/fiscal/FiscalClosureView.fxml");
    }

    @FXML
    public void logout() {
        AuthStore.clear();
        Nav.goToLogin();
    }

    private void loadCenter(String fxml) {
        try {
            Parent view = FXMLLoader.load(getClass().getResource(fxml));
            content.getChildren().setAll(view);
        } catch (Exception e) {
            content.getChildren().setAll(new Label("No se pudo cargar: " + fxml + "\n" + e.getMessage()));
        }
    }
}
