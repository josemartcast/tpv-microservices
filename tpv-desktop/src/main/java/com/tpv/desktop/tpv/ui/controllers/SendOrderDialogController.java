package com.tpv.desktop.tpv.ui.controllers;

import com.tpv.desktop.core.PrinterSettingsStore;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.ui.viewmodel.OrderViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class SendOrderDialogController {
    @FXML private Label barCount;
    @FXML private Label cocinaCount;
    @FXML private Label postresCount;
    @FXML private CheckBox splitByDestination;
    @FXML private javafx.scene.control.Button sendAllBtn;
    @FXML private javafx.scene.control.Button sendBarBtn;
    @FXML private javafx.scene.control.Button sendCocinaBtn;

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
        int bar = pending.getOrDefault(Destination.BAR, 0);
        int cocina = pending.getOrDefault(Destination.COCINA, 0);
        int postres = pending.getOrDefault(Destination.POSTRES, 0);
        barCount.setText(bar + " productos");
        cocinaCount.setText(cocina + " productos");
        postresCount.setText(postres + " productos");

        if (sendAllBtn != null) sendAllBtn.setDisable((bar + cocina + postres) <= 0);
        if (sendBarBtn != null) sendBarBtn.setDisable(bar <= 0);
        if (sendCocinaBtn != null) sendCocinaBtn.setDisable(cocina <= 0);
    }

    @FXML
    public void onSendAll() {
        boolean sent = viewModel.sendAll(splitByDestination.isSelected());
        refreshCounts();
        if (sent) {
            close();
        }
    }

    @FXML
    public void onSendBar() {
        boolean sent = viewModel.sendDestinations(EnumSet.of(Destination.BAR), splitByDestination.isSelected());
        refreshCounts();
        if (sent) {
            close();
        }
    }

    @FXML
    public void onSendCocina() {
        boolean sent = viewModel.sendDestinations(EnumSet.of(Destination.COCINA), splitByDestination.isSelected());
        refreshCounts();
        if (sent) {
            close();
        }
    }

    @FXML
    public void onReprintLast() {
        try {
            Map<String, String> jobs = AppContext.get().appState().lastComandaPrintJobsProperty().get();
            if (jobs != null && !jobs.isEmpty()) {
                int queued = 0;
                List<String> missingDestinationConfig = new ArrayList<>();
                for (Map.Entry<String, String> entry : jobs.entrySet()) {
                    String destination = entry.getKey();
                    if (PrinterSettingsStore.resolveSystemPrintersForDestination(destination).isEmpty()) {
                        missingDestinationConfig.add(destination);
                        continue;
                    }
                    AppContext.get().printQueueService().enqueue(destination, entry.getValue());
                    queued++;
                }
                if (queued <= 0) {
                    viewModel.feedbackProperty().set("No hay impresora configurada para reenviar la comanda.");
                    return;
                }
                if (missingDestinationConfig.isEmpty()) {
                    viewModel.feedbackProperty().set("Reimpresion enviada a impresoras configuradas.");
                } else {
                    viewModel.feedbackProperty().set(
                            "Reimpresion enviada. Sin impresora para: "
                                    + String.join(", ", missingDestinationConfig)
                                    + "."
                    );
                }
                return;
            }

            String text = AppContext.get().appState().lastComandaPrintTextProperty().get();
            if (text == null || text.isBlank()) {
                viewModel.feedbackProperty().set("No hay comanda enviada para reimprimir.");
                return;
            }
            if (PrinterSettingsStore.resolveSystemPrintersForDestination("ALL").isEmpty()) {
                viewModel.feedbackProperty().set("No hay impresora configurada para reenviar la comanda.");
                return;
            }
            AppContext.get().printQueueService().enqueue("ALL", text);
            viewModel.feedbackProperty().set("Reimpresion enviada a impresoras configuradas.");
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
