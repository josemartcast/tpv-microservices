package com.tpv.desktop.core;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class Nav {

    private static Stage stage;

    private Nav() {
    }

    public static void init(Stage primaryStage) {
        stage = primaryStage;
        stage.setTitle("TPV Desktop");
    }

    public static void goToLogin() {
        setRoot(load("/fxml/login/LoginView.fxml"), 420, 520);
    }

    public static void goToMainLayout() {
        setRoot(load("/fxml/layout/MainLayout.fxml"), 1200, 750);
    }

    public static void goToSales() {
        try {
            FXMLLoader loader = new FXMLLoader(Nav.class.getResource("/fxml/layout/MainLayout.fxml"));
            Parent root = loader.load();

            // obtenemos el controller del layout
            com.tpv.desktop.ui.layout.MainLayoutController ctrl = loader.getController();

            Scene scene = new Scene(root, 1200, 750);
            stage.setScene(scene);
            stage.show();

            // cargamos Sales en el center
            ctrl.goSales();

        } catch (IOException e) {
            throw new RuntimeException("No se pudo navegar a Sales", e);
        }
    }

    private static Parent load(String fxmlPath) {
        try {
            return FXMLLoader.load(Nav.class.getResource(fxmlPath));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar FXML: " + fxmlPath, e);
        }
    }

    private static void setRoot(Parent root, int w, int h) {
        Scene scene = new Scene(root, w, h);
        stage.setScene(scene);
        stage.show();
    }
}
