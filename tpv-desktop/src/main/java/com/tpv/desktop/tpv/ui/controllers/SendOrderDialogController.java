package com.tpv.desktop.tpv.ui.controllers;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.ui.util.PrintUtil;
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
        splitByDestination.setSelected(AppContext.get().appState().printSeparateByDestinationProperty().get());
        splitByDestination.selectedProperty().addListener((obs, oldV, newV) ->
                AppContext.get().appState().printSeparateByDestinationProperty().set(newV));
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
        viewModel.sendAll(splitByDestination.isSelected());
        close();
    }

    @FXML
    public void onSendBar() {
        viewModel.sendDestinations(EnumSet.of(Destination.BAR), splitByDestination.isSelected());
        close();
    }

    @FXML
    public void onSendCocina() {
        viewModel.sendDestinations(EnumSet.of(Destination.COCINA), splitByDestination.isSelected());
        close();
    }

    @FXML
    public void onReprintLast() {
        try {
            String text = AppContext.get().appState().lastComandaPrintTextProperty().get();
            if (text == null || text.isBlank()) {
                viewModel.feedbackProperty().set("No hay comanda enviada para reimprimir.");
                return;
            }
            PrintUtil.printTextToPdf(text, barCount.getScene().getWindow());
            viewModel.feedbackProperty().set("Reimpresion enviada a Print to PDF.");
        } catch (Exception e) {
            viewModel.feedbackProperty().set("No se pudo reimprimir: " + e.getMessage());
        }
    }

    @FXML
    public void onClose() {
        close();
    }

    private void close() {
        ((Stage) barCount.getScene().getWindow()).close();
    }
}
