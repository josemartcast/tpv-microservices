package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.tpv.domain.model.TableStatus;
import com.tpv.desktop.tpv.ui.viewmodel.TableCardViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class TableCardController {
    @FXML private StackPane root;
    @FXML private VBox card;
    @FXML private Label tableName;
    @FXML private Label stateLabel;
    @FXML private Label totalLabel;
    @FXML private Label elapsedLabel;
    @FXML private Label lockLabel;
    @FXML private Label pendingIcon;
    @FXML private Label billIcon;
    @FXML private Label lockOverlay;
    @FXML private Button openButton;

    private Runnable onClick;

    public void bind(TableCardViewModel vm, Runnable onClick) {
        this.onClick = onClick;
        tableName.textProperty().bind(vm.titleProperty());
        stateLabel.textProperty().bind(vm.statusTextProperty());
        totalLabel.textProperty().bind(vm.totalTextProperty());
        elapsedLabel.textProperty().bind(vm.elapsedTextProperty());
        lockLabel.textProperty().bind(vm.lockTextProperty());
        openButton.textProperty().bind(vm.actionTextProperty());
        openButton.disableProperty().bind(vm.blockedProperty());

        pendingIcon.visibleProperty().bind(vm.pendingWarnProperty());
        pendingIcon.managedProperty().bind(vm.pendingWarnProperty());
        billIcon.visibleProperty().bind(vm.billWarnProperty());
        billIcon.managedProperty().bind(vm.billWarnProperty());

        lockOverlay.visibleProperty().bind(vm.blockedProperty());
        lockOverlay.managedProperty().bind(vm.blockedProperty());

        vm.statusProperty().addListener((obs, oldV, newV) -> applyStatus(newV));
        applyStatus(vm.statusProperty().get());
    }

    @FXML
    public void onOpen() {
        if (onClick != null) {
            onClick.run();
        }
    }

    private void applyStatus(TableStatus status) {
        card.getStyleClass().removeAll(
                "table-free", "table-occupied", "table-pending", "table-prebill", "table-bill", "table-locked-me", "table-locked-other");
        switch (status) {
            case FREE -> card.getStyleClass().add("table-free");
            case OCCUPIED -> card.getStyleClass().add("table-occupied");
            case PENDING_SEND -> card.getStyleClass().add("table-pending");
            case PRECUENTA_PEDIDA -> card.getStyleClass().add("table-prebill");
            case BILL_REQUESTED -> card.getStyleClass().add("table-bill");
            case LOCKED_BY_ME -> card.getStyleClass().add("table-locked-me");
            case LOCKED_BY_OTHER -> card.getStyleClass().add("table-locked-other");
        }
    }
}

