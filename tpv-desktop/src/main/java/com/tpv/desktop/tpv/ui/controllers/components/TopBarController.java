package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.Nav;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.services.BackendStatusService;
import com.tpv.desktop.tpv.services.PrintQueueService;
import com.tpv.desktop.tpv.ui.viewmodel.TopBarBadgeMapper;
import com.tpv.desktop.tpv.ui.viewmodel.TopBarViewModel;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

public class TopBarController {
    @FXML private Label menuLabel;
    @FXML private Label restaurantLabel;
    @FXML private Label leftRestaurantLabel;
    @FXML private Label centerTitleLabel;
    @FXML private Label usernameLabel;
    @FXML private Label avatarLabel;
    @FXML private Label backendBadge;
    @FXML private Label modeBadge;
    @FXML private Label latencyBadge;
    @FXML private Label printerBadge;
    @FXML private Button errorsButton;
    @FXML private HBox root;

    private final TopBarViewModel vm = new TopBarViewModel();
    private final BackendStatusService backendStatusService = AppContext.get().backendStatusService();
    private final PrintQueueService printQueueService = AppContext.get().printQueueService();

    @FXML
    public void initialize() {
        leftRestaurantLabel.textProperty().bind(vm.restaurantNameProperty());
        centerTitleLabel.textProperty().bind(vm.centerTitleProperty());
        usernameLabel.textProperty().bind(Bindings.createStringBinding(
                () -> AppContext.get().appState().activeUserProperty().get().displayName()
                        + " | " + AppContext.get().appState().terminalIdProperty().get(),
                AppContext.get().appState().activeUserProperty(),
                AppContext.get().appState().terminalIdProperty()
        ));
        vm.bindReactive(backendBadge.textProperty(), latencyBadge.textProperty());

        backendStatusService.statusProperty().addListener((obs, oldV, newV) -> applyStatusStyle(newV));
        applyStatusStyle(backendStatusService.statusProperty().get());

        printQueueService.stateProperty().addListener((obs, oldV, newV) -> applyPrintStatusStyle());
        printQueueService.pendingJobsProperty().addListener((obs, oldV, newV) -> applyPrintStatusStyle());
        printQueueService.lastErrorProperty().addListener((obs, oldV, newV) -> applyPrintStatusStyle());
        applyPrintStatusStyle();

        AppContext.get().appState().runtimeModeProperty().addListener((obs, oldV, newV) -> applyRuntimeModeStyle(newV));
        applyRuntimeModeStyle(AppContext.get().appState().runtimeModeProperty().get());

        Tooltip tip = new Tooltip();
        tip.textProperty().bind(backendStatusService.lastErrorProperty());
        errorsButton.setTooltip(tip);

        Tooltip printTip = new Tooltip();
        printTip.textProperty().bind(printQueueService.lastErrorProperty());
        printerBadge.setTooltip(printTip);
        printerBadge.setOnMouseClicked(e -> onShowPrintErrors());
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

    public void onShowPrintErrors() {
        ContextMenu menu = new ContextMenu();
        if (printQueueService.errorHistory().isEmpty()) {
            menu.getItems().add(new MenuItem("Sin errores de impresion"));
        } else {
            for (String err : printQueueService.errorHistory()) {
                MenuItem item = new MenuItem(err);
                item.setDisable(true);
                menu.getItems().add(item);
            }
        }
        menu.show(printerBadge, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    @FXML
    public void onOpenMenu(MouseEvent event) {
        ContextMenu menu = new ContextMenu();

        MenuItem salon = new MenuItem("Salon");
        salon.setOnAction(e -> Navigator.get().goHome());

        MenuItem settings = new MenuItem("Settings");
        settings.setOnAction(e -> openSettingsModal());

        MenuItem usersAdmin = new MenuItem("Usuarios");
        usersAdmin.setOnAction(e -> openUsersAdminModal());
        usersAdmin.setDisable(!AuthStore.hasRole("ADMIN"));

        MenuItem logout = new MenuItem("Logout");
        logout.setOnAction(e -> {
            AuthStore.clear();
            Nav.goToLogin();
        });

        menu.getItems().addAll(salon, settings, usersAdmin, new SeparatorMenuItem(), logout);
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

        MenuItem usersAdmin = new MenuItem("Usuarios");
        usersAdmin.setOnAction(e -> openUsersAdminModal());
        usersAdmin.setDisable(!AuthStore.hasRole("ADMIN"));

        MenuItem logout = new MenuItem("Logout");
        logout.setOnAction(e -> {
            AuthStore.clear();
            Nav.goToLogin();
        });

        menu.getItems().addAll(settings, usersAdmin, new SeparatorMenuItem(), logout);
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

    private void openUsersAdminModal() {
        if (!AuthStore.hasRole("ADMIN")) {
            Alert alert = new Alert(Alert.AlertType.WARNING,
                    "Esta accion requiere un usuario con rol ADMIN.\nCierra sesion e inicia con un usuario administrador.",
                    ButtonType.OK);
            alert.setTitle("Permisos insuficientes");
            alert.setHeaderText("Usuarios y Roles");
            alert.showAndWait();
            return;
        }
        try {
            Parent root = FXMLLoader.load(getClass().getResource("/fxml/users/UsersAdminView.fxml"));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Usuarios y Roles");
            Scene scene = new Scene(root, 980, 700);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            modal.setScene(scene);
            modal.showAndWait();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "No se pudo abrir Usuarios: " + e.getMessage(), ButtonType.OK);
            alert.setHeaderText("Error");
            alert.showAndWait();
        }
    }

    private void applyStatusStyle(BackendStatus status) {
        backendBadge.getStyleClass().removeAll("badge-online", "badge-degraded", "badge-offline");
        backendBadge.getStyleClass().add(TopBarBadgeMapper.backendBadgeClass(status));
    }

    private void applyPrintStatusStyle() {
        printerBadge.getStyleClass().removeAll("badge-online", "badge-degraded", "badge-offline");
        TopBarBadgeMapper.PrintBadgePresentation presentation = TopBarBadgeMapper.printBadge(
                printQueueService.stateProperty().get(),
                printQueueService.pendingJobsProperty().get(),
                printQueueService.lastErrorProperty().get()
        );
        printerBadge.setText(presentation.text());
        printerBadge.getStyleClass().add(presentation.styleClass());
    }

    private void applyRuntimeModeStyle(String runtimeMode) {
        modeBadge.getStyleClass().removeAll("badge-mode-real", "badge-mode-fake");
        TopBarBadgeMapper.RuntimeModePresentation presentation = TopBarBadgeMapper.runtimeModeBadge(runtimeMode);
        modeBadge.setText(presentation.text());
        modeBadge.getStyleClass().add(presentation.styleClass());
    }
}
