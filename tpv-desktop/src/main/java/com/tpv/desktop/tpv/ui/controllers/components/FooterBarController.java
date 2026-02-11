package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.core.SettingsStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class FooterBarController {

    @FXML
    public void onCaja() {
        openModal("Caja", "/fxml/cash/CashView.fxml", 860, 700);
    }

    @FXML
    public void onHistorial() {
        openModal("Historial", "/fxml/history/HistoryView.fxml", 1160, 760);
    }

    @FXML
    public void onProductos() {
        try {
            List<Category> categories = AppContext.get().catalogService().categories();
            ObservableList<String> productRows = FXCollections.observableArrayList();

            ComboBox<Category> categoryBox = new ComboBox<>(FXCollections.observableArrayList(categories));
            if (!categories.isEmpty()) {
                categoryBox.getSelectionModel().selectFirst();
            }
            TextField searchField = new TextField();
            searchField.setPromptText("Buscar producto...");
            ListView<String> list = new ListView<>(productRows);
            list.setPrefHeight(500);

            Runnable refresh = () -> {
                Category selected = categoryBox.getValue();
                if (selected == null) {
                    productRows.clear();
                    return;
                }
                String filter = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
                List<String> rows = new ArrayList<>();
                for (Product p : AppContext.get().catalogService().productsByCategory(selected.id())) {
                    if (!filter.isBlank() && !p.name().toLowerCase(Locale.ROOT).contains(filter)) {
                        continue;
                    }
                    rows.add(String.format(Locale.US, "%-28s  %7.2f EUR  [%s]", p.name(), p.priceCents() / 100.0, p.destination().name()));
                }
                productRows.setAll(rows);
            };

            categoryBox.valueProperty().addListener((obs, oldV, newV) -> refresh.run());
            searchField.textProperty().addListener((obs, oldV, newV) -> refresh.run());
            refresh.run();

            HBox top = new HBox(8, new Label("Categoria"), categoryBox, searchField);
            HBox.setHgrow(searchField, Priority.ALWAYS);
            VBox root = new VBox(10, top, list);
            root.setPadding(new Insets(12));

            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Productos");
            Scene scene = new Scene(root, 900, 620);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            modal.setScene(scene);
            modal.showAndWait();
        } catch (Exception e) {
            showError("No se pudo abrir Productos: " + e.getMessage());
        }
    }

    @FXML
    public void onClientes() {
        ObservableList<String> clients = FXCollections.observableArrayList(
                "Mostrador",
                "Mesa 1 - Consumidor final",
                "Empresa Demo S.L.",
                "Cliente VIP"
        );
        TextField search = new TextField();
        search.setPromptText("Buscar cliente...");
        ListView<String> list = new ListView<>(clients);
        list.setPrefHeight(420);

        search.textProperty().addListener((obs, oldV, newV) -> {
            String filter = newV == null ? "" : newV.trim().toLowerCase(Locale.ROOT);
            if (filter.isBlank()) {
                list.setItems(clients);
                return;
            }
            ObservableList<String> filtered = FXCollections.observableArrayList();
            for (String c : clients) {
                if (c.toLowerCase(Locale.ROOT).contains(filter)) {
                    filtered.add(c);
                }
            }
            list.setItems(filtered);
        });

        Button selectBtn = new Button("Seleccionar");
        selectBtn.setOnAction(e -> {
            String selected = list.getSelectionModel().getSelectedItem();
            if (selected != null) {
                AppContext.get().appState().activeCustomerProperty().set(selected);
                SettingsStore.setActiveCustomer(selected);
                Stage stage = (Stage) selectBtn.getScene().getWindow();
                stage.close();
            }
        });

        VBox root = new VBox(10, search, list, selectBtn);
        root.setPadding(new Insets(12));

        Stage modal = new Stage();
        modal.initModality(Modality.APPLICATION_MODAL);
        modal.setTitle("Clientes");
        Scene scene = new Scene(root, 560, 540);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
        modal.setScene(scene);
        modal.showAndWait();
    }

    private void openModal(String title, String fxml, int width, int height) {
        try {
            Parent root = FXMLLoader.load(getClass().getResource(fxml));
            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle(title);
            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            modal.setScene(scene);
            modal.showAndWait();
        } catch (Exception e) {
            showError("No se pudo abrir " + title + ": " + e.getMessage());
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText("Error");
        alert.showAndWait();
    }
}
