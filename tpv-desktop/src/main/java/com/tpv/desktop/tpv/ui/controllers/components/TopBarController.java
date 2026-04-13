package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.ui.UiDialogs;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.diagnostics.LeakDiagnostics;
import com.tpv.desktop.tpv.domain.model.User;
import com.tpv.desktop.tpv.ui.LifecycleAware;
import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.services.BackendStatusService;
import com.tpv.desktop.tpv.services.PrintQueueService;
import com.tpv.desktop.tpv.ui.viewmodel.TopBarBadgeMapper;
import com.tpv.desktop.tpv.ui.viewmodel.TopBarViewModel;
import javafx.beans.binding.StringBinding;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
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

public class TopBarController implements LifecycleAware {
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
    private ChangeListener<BackendStatus> backendStatusListener;
    private ChangeListener<Object> printStateListener;
    private ChangeListener<String> printErrorListener;
    private ChangeListener<String> runtimeModeListener;
    private StringBinding usernameBinding;
    private Tooltip errorsTooltip;
    private Tooltip printTooltip;
    private boolean disposed;

    @FXML
    public void initialize() {
        LeakDiagnostics.controllerCreated("TopBarController");
        leftRestaurantLabel.textProperty().bind(vm.restaurantNameProperty());
        centerTitleLabel.textProperty().bind(vm.centerTitleProperty());
        usernameBinding = Bindings.createStringBinding(
                () -> {
                    var activeUser = AppContext.get().appState().activeUserProperty().get();
                    if (activeUser == null || activeUser.displayName() == null || activeUser.displayName().isBlank()) {
                        return "Usuario";
                    }
                    return activeUser.displayName();
                },
                AppContext.get().appState().activeUserProperty()
        );
        usernameLabel.textProperty().bind(usernameBinding);
        backendBadge.textProperty().bind(Bindings.createStringBinding(
                () -> backendStatusService.statusProperty().get().name(),
                backendStatusService.statusProperty()
        ));
        latencyBadge.textProperty().bind(Bindings.createStringBinding(
                () -> backendStatusService.latencyMsProperty().get() + "ms",
                backendStatusService.latencyMsProperty()
        ));

        backendStatusListener = (obs, oldV, newV) -> applyStatusStyle(newV);
        backendStatusService.statusProperty().addListener(backendStatusListener);
        applyStatusStyle(backendStatusService.statusProperty().get());

        printStateListener = (obs, oldV, newV) -> applyPrintStatusStyle();
        printErrorListener = (obs, oldV, newV) -> applyPrintStatusStyle();
        printQueueService.stateProperty().addListener(printStateListener);
        printQueueService.pendingJobsProperty().addListener(printStateListener);
        printQueueService.lastErrorProperty().addListener(printErrorListener);
        applyPrintStatusStyle();

        runtimeModeListener = (obs, oldV, newV) -> applyRuntimeModeStyle(newV);
        AppContext.get().appState().runtimeModeProperty().addListener(runtimeModeListener);
        applyRuntimeModeStyle(AppContext.get().appState().runtimeModeProperty().get());

        errorsTooltip = new Tooltip();
        errorsTooltip.textProperty().bind(backendStatusService.lastErrorProperty());
        errorsButton.setTooltip(errorsTooltip);

        printTooltip = new Tooltip();
        printTooltip.textProperty().bind(printQueueService.lastErrorProperty());
        printerBadge.setTooltip(printTooltip);
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
            AppContext.get().appState().activeUserProperty().set(new User(0, "", ""));
            Navigator.get().goLogin();
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
            AppContext.get().appState().activeUserProperty().set(new User(0, "", ""));
            Navigator.get().goLogin();
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
            UiDialogs.error("Settings", "No se pudo abrir Settings: " + e.getMessage());
        }
    }

    private void openUsersAdminModal() {
        if (!AuthStore.hasRole("ADMIN")) {
            UiDialogs.warn(
                    "Usuarios y Roles",
                    "Esta accion requiere un usuario con rol ADMIN.\nCierra sesion e inicia con un usuario administrador."
            );
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
            UiDialogs.error("Usuarios", "No se pudo abrir Usuarios: " + e.getMessage());
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

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;

        if (backendStatusListener != null) {
            backendStatusService.statusProperty().removeListener(backendStatusListener);
            backendStatusListener = null;
        }
        if (printStateListener != null) {
            printQueueService.stateProperty().removeListener(printStateListener);
            printQueueService.pendingJobsProperty().removeListener(printStateListener);
            printStateListener = null;
        }
        if (printErrorListener != null) {
            printQueueService.lastErrorProperty().removeListener(printErrorListener);
            printErrorListener = null;
        }
        if (runtimeModeListener != null) {
            AppContext.get().appState().runtimeModeProperty().removeListener(runtimeModeListener);
            runtimeModeListener = null;
        }

        leftRestaurantLabel.textProperty().unbind();
        centerTitleLabel.textProperty().unbind();
        backendBadge.textProperty().unbind();
        latencyBadge.textProperty().unbind();
        usernameLabel.textProperty().unbind();
        if (usernameBinding != null) {
            usernameBinding.dispose();
            usernameBinding = null;
        }

        if (errorsTooltip != null) {
            errorsTooltip.textProperty().unbind();
        }
        if (printTooltip != null) {
            printTooltip.textProperty().unbind();
        }
        LeakDiagnostics.controllerDestroyed("TopBarController");
    }
}
