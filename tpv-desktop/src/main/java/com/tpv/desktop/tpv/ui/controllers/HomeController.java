package com.tpv.desktop.tpv.ui.controllers;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.domain.model.BackendStatus;
import com.tpv.desktop.tpv.services.LockException;
import com.tpv.desktop.tpv.ui.controllers.components.TableCardController;
import com.tpv.desktop.tpv.ui.controllers.components.TopBarController;
import com.tpv.desktop.tpv.ui.viewmodel.HomeViewModel;
import com.tpv.desktop.tpv.ui.viewmodel.TableCardViewModel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.util.Duration;

import java.io.IOException;

public class HomeController {
    @FXML private TopBarController topBarController;
    @FXML private FlowPane tablesPane;
    @FXML private ScrollPane homeScroll;
    @FXML private Label feedbackLabel;
    @FXML private Label offlineBanner;

    private final HomeViewModel vm = new HomeViewModel();
    private Timeline refreshTimeline;

    @FXML
    public void initialize() {
        topBarController.setCenterTitle(AppContext.get().appState().restaurantNameProperty().get());
        tablesPane.prefWrapLengthProperty().bind(homeScroll.widthProperty().subtract(28));
        AppContext.get().backendStatusService().statusProperty().addListener((obs, o, n) -> {
            offlineBanner.setVisible(n == com.tpv.desktop.tpv.domain.model.BackendStatus.OFFLINE);
            offlineBanner.setManaged(n == com.tpv.desktop.tpv.domain.model.BackendStatus.OFFLINE);
        });

        refreshTables();

        refreshTimeline = new Timeline(
                new KeyFrame(Duration.seconds(2), e -> AppContext.get().backendStatusService().probe()),
                new KeyFrame(Duration.seconds(5), e -> {
                    refreshTables();
                })
        );
        refreshTimeline.setCycleCount(Timeline.INDEFINITE);
        refreshTimeline.play();
    }

    private void renderTables() {
        tablesPane.getChildren().clear();
        for (TableCardViewModel table : vm.tables()) {
            Node node = loadCard(table);
            if (node != null) {
                tablesPane.getChildren().add(node);
            }
        }
    }

    private Node loadCard(TableCardViewModel table) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/TableCard.fxml"));
            Node node = loader.load();
            TableCardController controller = loader.getController();
            controller.bind(table, () -> onTableClick(table));
            return node;
        } catch (IOException e) {
            feedbackLabel.setText("No se pudo renderizar mesa: " + e.getMessage());
            return null;
        }
    }

    private void onTableClick(TableCardViewModel table) {
        if (AppContext.get().backendStatusService().statusProperty().get() == BackendStatus.OFFLINE) {
            feedbackLabel.setText("Backend offline: no se puede abrir mesa.");
            return;
        }
        try {
            long orderId = vm.openOrEnter(table);
            Navigator.get().goOrder(orderId, table.getTableId());
        } catch (LockException e) {
            if (e.isOwnershipConflict()) {
                feedbackLabel.setText("Mesa bloqueada por otro terminal.");
                return;
            }
            if (e.isAuthIssue()) {
                feedbackLabel.setText("Sesion expirada. Haz login de nuevo.");
                return;
            }
            feedbackLabel.setText("Error de lock: " + e.getMessage());
        } catch (Exception e) {
            feedbackLabel.setText("Bloqueada por Terminal: " + e.getMessage());
        }
    }

    private void refreshTables() {
        try {
            vm.refresh();
            renderTables();
            if (feedbackLabel.getText() != null && feedbackLabel.getText().startsWith("No se pudo")) {
                feedbackLabel.setText("");
            }
        } catch (Exception e) {
            feedbackLabel.setText("No se pudo sincronizar mesas: " + e.getMessage());
        }
    }
}

