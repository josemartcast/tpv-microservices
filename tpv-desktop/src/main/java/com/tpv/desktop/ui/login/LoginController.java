package com.tpv.desktop.ui.login;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.auth.AuthApi;
import com.tpv.desktop.api.auth.LoginResponse;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.domain.model.User;
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

        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isBlank() || password.isBlank()) {
            errorLabel.setText("Usuario y contraseña son obligatorios.");
            return;
        }

        try {
            LoginResponse res = AuthApi.login(username, password);
            if (res == null || res.accessToken() == null || res.accessToken().isBlank()) {
                errorLabel.setText("Login correcto pero sin token.");
                return;
            }

            AuthStore.setToken(res.accessToken());
            AppContext.get().appState().activeUserProperty().set(new User(0, username, initials(username)));
            Navigator.get().goHome();
        } catch (ApiException e) {
            errorLabel.setText("Credenciales incorrectas o backend caído.");
        } catch (Exception e) {
            errorLabel.setText("Error inesperado: " + e.getMessage());
        }
    }

    private static String initials(String user) {
        if (user == null || user.isBlank()) {
            return "TPV";
        }
        String normalized = user.trim().toUpperCase();
        return normalized.length() <= 2 ? normalized : normalized.substring(0, 2);
    }
}
