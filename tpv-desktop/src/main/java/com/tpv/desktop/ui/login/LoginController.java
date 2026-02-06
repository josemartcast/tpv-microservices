package com.tpv.desktop.ui.login;

import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.Nav;
import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.auth.AuthApi;
import com.tpv.desktop.api.auth.LoginResponse;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;

    @FXML
    public void onLogin() {
        errorLabel.setText("");

        String u = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String p = passwordField.getText() == null ? "" : passwordField.getText();

        if (u.isBlank() || p.isBlank()) {
            errorLabel.setText("Usuario y contraseña son obligatorios.");
            return;
        }

        try {
            LoginResponse res = AuthApi.login(u, p);

            if (res == null || res.accessToken() == null || res.accessToken().isBlank()) {
                errorLabel.setText("Login OK pero sin token.");
                return;
            }

            AuthStore.setToken(res.accessToken());
            Nav.goToMainLayout();
        } catch (ApiException e) {
            errorLabel.setText("Credenciales incorrectas o backend caído.");
        } catch (Exception e) {
            errorLabel.setText("Error inesperado: " + e.getMessage());
        }
    }
}
