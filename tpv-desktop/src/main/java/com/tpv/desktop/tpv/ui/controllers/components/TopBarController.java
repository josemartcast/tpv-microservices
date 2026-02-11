package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.Nav;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.services.BackendStatusService;
import com.tpv.desktop.tpv.ui.viewmodel.TopBarViewModel;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Parent;
import java.io.IOException;
import javafx.scene.input.MouseEvent;

public class TopBarController {
    @FXML private Label menuLabel;
    @FXML private Label restaurantLabel;
    @FXML private Label leftRestaurantLabel;
    @FXML private Label centerTitleLabel;
    @FXML private Label usernameLabel;
    @FXML private Label avatarLabel;
    @FXML private Label backendBadge;
    @FXML private Label latencyBadge;
    @FXML private Button errorsButton;
    @FXML private HBox root;

    private final TopBarViewModel vm = new TopBarViewModel();
    private final BackendStatusService backendStatusService = AppContext.get().backendStatusService();

    @FXML
    public void initialize() {
        leftRestaurantLabel.textProperty().bind(vm.restaurantNameProperty());
        centerTitleLabel.textProperty().bind(vm.centerTitleProperty());
        usernameLabel.setText(AppContext.get().appState().activeUserProperty().get().displayName());
        vm.bindReactive(backendBadge.textProperty(), latencyBadge.textProperty());

        backendStatusService.statusProperty().addListener((obs, oldV, newV) -> applyStatusStyle(newV));
        applyStatusStyle(backendStatusService.statusProperty().get());

        Tooltip tip = new Tooltip();
        tip.textProperty().bind(backendStatusService.lastErrorProperty());
        errorsButton.setTooltip(tip);
    }

    public void setCenterTitle(String title) {
        vm.setCenterTitle(title);
    }

    @FXML
    public void onShowErrors() {
        ContextMenu menu = new ContextMenu();
        if (backendStatusService.errorHistory().isEmpty()) {
            menu.getItems().add(new MenuItem("Sin errores recientes"));
        } else {
            for (String err : backendStatusService.errorHistory()) {
                MenuItem item = new MenuItem(err);
                item.setDisable(true);
                menu.getItems().add(item);
            }
        }
        menu.show(errorsButton, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    @FXML
    public void onOpenMenu(MouseEvent event) {
        ContextMenu menu = new ContextMenu();

        MenuItem salon = new MenuItem("Salon");
        salon.setOnAction(e -> Navigator.get().goHome());

        MenuItem settings = new MenuItem("Settings");
        settings.setOnAction(e -> openSettingsModal());

        MenuItem logout = new MenuItem("Logout");
        logout.setOnAction(e -> {
            AuthStore.clear();
            Nav.goToLogin();
        });

        menu.getItems().addAll(salon, settings, new SeparatorMenuItem(), logout);
        menu.show(menuLabel, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    @FXML
    public void onGoHome() {
        Navigator.get().goHome();
    }

    @FXML
    public void onOpenUserMenu(MouseEvent event) {
        ContextMenu menu = new ContextMenu();

        MenuItem settings = new MenuItem("Settings");
        settings.setOnAction(e -> openSettingsModal());

        MenuItem logout = new MenuItem("Logout");
        logout.setOnAction(e -> {
            AuthStore.clear();
            Nav.goToLogin();
        });

        menu.getItems().addAll(settings, new SeparatorMenuItem(), logout);
        menu.show(usernameLabel, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void openSettingsModal() {
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/settings/SettingsView.fxml"));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Settings");
            Scene scene = new Scene(root, 980, 620);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            modal.setScene(scene);
            modal.showAndWait();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo abrir Settings: " + e.getMessage(), ButtonType.OK);
            alert.setHeaderText("Error");
            alert.showAndWait();
        }
    }

    private void applyStatusStyle(BackendStatus status) {
        backendBadge.getStyleClass().removeAll("badge-online", "badge-degraded", "badge-offline");
        switch (status) {
            case ONLINE -> backendBadge.getStyleClass().add("badge-online");
            case DEGRADED -> backendBadge.getStyleClass().add("badge-degraded");
            case OFFLINE -> backendBadge.getStyleClass().add("badge-offline");
        }
    }
}

