package com.tpv.desktop.tpv.ui.controllers;

import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.ui.viewmodel.OrderViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.EnumSet;
import java.util.Map;

public class SendOrderDialogController {
    @FXML private Label barCount;
    @FXML private Label cocinaCount;
    @FXML private Label postresCount;
    @FXML private CheckBox splitByDestination;

    private OrderViewModel viewModel;

    public void bind(OrderViewModel vm) {
        this.viewModel = vm;
        refreshCounts();
    }

    private void refreshCounts() {
        Map<Destination, Integer> pending = viewModel.pendingByDestination();
        barCount.setText(pending.getOrDefault(Destination.BAR, 0) + " productos");
        cocinaCount.setText(pending.getOrDefault(Destination.COCINA, 0) + " productos");
        postresCount.setText(pending.getOrDefault(Destination.POSTRES, 0) + " productos");
    }

    @FXML
    public void onSendAll() {
        viewModel.sendAll();
        close();
    }

    @FXML
    public void onSendBar() {
        viewModel.sendDestinations(EnumSet.of(Destination.BAR));
        close();
    }

    @FXML
    public void onSendCocina() {
        viewModel.sendDestinations(EnumSet.of(Destination.COCINA));
        close();
    }

    @FXML
    public void onReprintLast() {
        viewModel.feedbackProperty().set("Reimpresión simulada.");
    }

    @FXML
    public void onClose() {
        close();
    }

    private void close() {
        ((Stage) barCount.getScene().getWindow()).close();
    }
}

