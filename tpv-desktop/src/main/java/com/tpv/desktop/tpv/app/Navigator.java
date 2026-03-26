package com.tpv.desktop.tpv.app;

import com.tpv.desktop.tpv.ui.controllers.OrderController;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class Navigator {
    private static Navigator INSTANCE;
    private final Stage stage;

    public static void init(Stage stage) {
        INSTANCE = new Navigator(stage);
    }

    public static Navigator get() {
        return INSTANCE;
    }

    private Navigator(Stage stage) {
        this.stage = stage;
        stage.setTitle("TPV Desktop");
        stage.setMinWidth(1200);
        stage.setMinHeight(720);
    }

    public void goHome() {
        setRoot(load("/fxml/views/HomeView.fxml"), 1600, 900);
    }

    public void goOrder(long orderId, int tableId) {
        goOrder(orderId, tableId, null);
    }

    public void goOrder(long orderId, int tableId, String tableLabel) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/views/OrderView.fxml"));
            Parent root = loader.load();
            OrderController controller = loader.getController();
            controller.bind(orderId, tableId, tableLabel);
            setRoot(root, Math.max(stage.getWidth(), 1366), Math.max(stage.getHeight(), 768));
        } catch (IOException e) {
            String detail = rootMessage(e);
            throw new RuntimeException("No se pudo abrir comanda: " + detail, e);
        }
    }

    private static String rootMessage(Throwable ex) {
        if (ex == null) {
            return "error desconocido";
        }
        Throwable current = ex;
        Throwable next = current.getCause();
        while (next != null && next != current) {
            current = next;
            next = current.getCause();
        }
        String msg = current.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = ex.getMessage();
        }
        return (msg == null || msg.isBlank()) ? current.getClass().getSimpleName() : msg;
    }

    public void openStubModal(String title, String message) {
        Stage modal = new Stage();
        modal.initOwner(stage);
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle(title);
        javafx.scene.control.Label label = new javafx.scene.control.Label(message);
        label.setStyle("-fx-padding: 24; -fx-font-size: 16px;");
        Scene scene = new Scene(new javafx.scene.layout.StackPane(label), 420, 180);
        modal.setScene(scene);
        modal.showAndWait();
    }

    private Parent load(String path) {
        try {
            return FXMLLoader.load(getClass().getResource(path));
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar: " + path, e);
        }
    }

    private void setRoot(Parent root, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        if (AppContext.get().appState().touchModeProperty().get()) {
            if (!scene.getRoot().getStyleClass().contains("touch-mode")) {
                scene.getRoot().getStyleClass().add("touch-mode");
            }
        } else {
            scene.getRoot().getStyleClass().remove("touch-mode");
        }
        boolean kioskMode = AppContext.get().appState().kioskModeProperty().get();
        if (kioskMode) {
            if (!scene.getRoot().getStyleClass().contains("kiosk-mode")) {
                scene.getRoot().getStyleClass().add("kiosk-mode");
            }
            installKioskKeyFilter(scene);
            stage.setResizable(false);
            stage.setFullScreenExitHint("");
        } else {
            stage.setResizable(true);
            stage.setFullScreen(false);
        }
        stage.setScene(scene);
        stage.setIconified(false);
        stage.show();
        stage.toFront();
        if (kioskMode) {
            stage.setMaximized(true);
            stage.setFullScreen(true);
        } else {
            enforceFullscreenWindowBounds();
            Platform.runLater(() -> {
                stage.setIconified(false);
                enforceFullscreenWindowBounds();
                stage.toFront();
            });
        }
    }

    private void enforceFullscreenWindowBounds() {
        var bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setMaximized(true);
    }

    private void installKioskKeyFilter(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE || event.getCode() == KeyCode.F11) {
                event.consume();
                return;
            }
            if (event.isAltDown() && (event.getCode() == KeyCode.F4 || event.getCode() == KeyCode.TAB)) {
                event.consume();
            }
        });
    }
}

