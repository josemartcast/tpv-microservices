package com.tpv.desktop.tpv.ui.controllers.components;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.ui.UiDialogs;
import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.api.pos.SalonAdminApi;
import com.tpv.desktop.api.pos.SalonAreaResponse;
import com.tpv.desktop.api.pos.TableAliasResponse;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.SettingsStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
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
            ObservableList<Category> categories = FXCollections.observableArrayList(AppContext.get().catalogService().categories());
            ObservableList<Product> products = FXCollections.observableArrayList();
            ObservableList<Integer> vatOptions = FXCollections.observableArrayList(400, 1000, 2100);

            ComboBox<Category> filterCategoryBox = new ComboBox<>(categories);
            ComboBox<Category> editCategoryBox = new ComboBox<>(categories);
            configureCategoryCombo(filterCategoryBox);
            configureCategoryCombo(editCategoryBox);
            if (!categories.isEmpty()) {
                filterCategoryBox.getSelectionModel().selectFirst();
                editCategoryBox.getSelectionModel().selectFirst();
            }

            TextField searchField = new TextField();
            searchField.setPromptText("Buscar producto...");

            TextField productNameField = new TextField();
            productNameField.setPromptText("Nombre producto");
            TextField productPriceField = new TextField();
            productPriceField.setPromptText("Precio EUR (ej: 4.50)");
            ComboBox<Integer> vatBox = new ComboBox<>(vatOptions);
            vatBox.getSelectionModel().select(Integer.valueOf(1000));
            vatBox.setConverter(new StringConverter<>() {
                @Override
                public String toString(Integer value) {
                    if (value == null) return "";
                    return (value / 100.0) + "%";
                }

                @Override
                public Integer fromString(String string) {
                    return null;
                }
            });

            Button newProductBtn = new Button("Nuevo");
            Button createProductBtn = new Button("Crear producto");
            Button updateProductBtn = new Button("Guardar cambios");
            Button deleteProductBtn = new Button("Eliminar");
            updateProductBtn.setDisable(true);
            deleteProductBtn.setDisable(true);

            ListView<Product> list = new ListView<>(products);
            list.setPrefHeight(430);
            list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Product item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : formatProductRow(item));
                }
            });

            Runnable refresh = () -> {
                Category selected = filterCategoryBox.getValue();
                if (selected == null) {
                    products.clear();
                    return;
                }
                String filter = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
                List<Product> rows = new ArrayList<>();
                for (Product product : AppContext.get().catalogService().productsByCategory(selected.id())) {
                    if (!filter.isBlank() && !product.name().toLowerCase(Locale.ROOT).contains(filter)) {
                        continue;
                    }
                    rows.add(product);
                }
                products.setAll(rows);
            };

            Runnable clearProductForm = () -> {
                list.getSelectionModel().clearSelection();
                productNameField.clear();
                productPriceField.clear();
                if (editCategoryBox.getValue() == null && !categories.isEmpty()) {
                    editCategoryBox.getSelectionModel().selectFirst();
                }
                vatBox.getSelectionModel().select(Integer.valueOf(1000));
                createProductBtn.setDisable(false);
                updateProductBtn.setDisable(true);
                deleteProductBtn.setDisable(true);
            };

            filterCategoryBox.valueProperty().addListener((obs, oldV, newV) -> refresh.run());
            searchField.textProperty().addListener((obs, oldV, newV) -> refresh.run());
            list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
                if (selected == null) {
                    createProductBtn.setDisable(false);
                    updateProductBtn.setDisable(true);
                    deleteProductBtn.setDisable(true);
                    return;
                }
                productNameField.setText(selected.name());
                productPriceField.setText(String.format(Locale.US, "%.2f", selected.priceCents() / 100.0));
                vatBox.getSelectionModel().select(Integer.valueOf(selected.vatRateBps()));
                categories.stream()
                        .filter(c -> c.id() == selected.categoryId())
                        .findFirst()
                        .ifPresent(editCategoryBox::setValue);
                createProductBtn.setDisable(true);
                updateProductBtn.setDisable(false);
                deleteProductBtn.setDisable(false);
            });

            createProductBtn.setOnAction(evt -> {
                Category category = editCategoryBox.getValue();
                if (category == null) {
                    showError("Selecciona una categoria para el producto.");
                    return;
                }
                String name = productNameField.getText() == null ? "" : productNameField.getText().trim();
                if (name.length() < 2) {
                    showError("Nombre de producto invalido (min 2 caracteres).");
                    return;
                }
                int priceCents;
                try {
                    priceCents = parsePriceToCents(productPriceField.getText());
                } catch (Exception ex) {
                    showError("Precio invalido. Usa formato 4.50");
                    return;
                }
                Integer vat = vatBox.getValue();
                if (vat == null) {
                    showError("Selecciona IVA (4, 10 o 21).");
                    return;
                }
                AppContext.get().catalogService().createProduct(category.id(), name, priceCents, vat);
                categories.setAll(AppContext.get().catalogService().categories());
                filterCategoryBox.setItems(categories);
                editCategoryBox.setItems(categories);
                filterCategoryBox.setValue(category);
                clearProductForm.run();
                refresh.run();
            });

            updateProductBtn.setOnAction(evt -> {
                Product selected = list.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    showError("Selecciona un producto para editar.");
                    return;
                }
                Category category = editCategoryBox.getValue();
                if (category == null) {
                    showError("Selecciona una categoria para el producto.");
                    return;
                }
                String name = productNameField.getText() == null ? "" : productNameField.getText().trim();
                if (name.length() < 2) {
                    showError("Nombre de producto invalido (min 2 caracteres).");
                    return;
                }
                int priceCents;
                try {
                    priceCents = parsePriceToCents(productPriceField.getText());
                } catch (Exception ex) {
                    showError("Precio invalido. Usa formato 4.50");
                    return;
                }
                Integer vat = vatBox.getValue();
                if (vat == null) {
                    showError("Selecciona IVA (4, 10 o 21).");
                    return;
                }
                AppContext.get().catalogService().updateProduct(selected.id(), category.id(), name, priceCents, vat);
                categories.setAll(AppContext.get().catalogService().categories());
                filterCategoryBox.setItems(categories);
                editCategoryBox.setItems(categories);
                filterCategoryBox.setValue(category);
                clearProductForm.run();
                refresh.run();
            });

            deleteProductBtn.setOnAction(evt -> {
                Product selected = list.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    showError("Selecciona un producto para eliminar.");
                    return;
                }
                if (!showConfirm("Eliminar producto", "Se eliminara el producto '" + selected.name() + "'. Continuar?")) {
                    return;
                }
                AppContext.get().catalogService().deleteProduct(selected.id());
                clearProductForm.run();
                refresh.run();
            });

            newProductBtn.setOnAction(evt -> clearProductForm.run());
            refresh.run();

            HBox filters = new HBox(8, new Label("Categoria"), filterCategoryBox, searchField);
            HBox.setHgrow(searchField, Priority.ALWAYS);

            HBox productEditor = new HBox(8,
                    new Label("Producto"),
                    productNameField,
                    productPriceField,
                    new Label("Categoria"),
                    editCategoryBox,
                    new Label("IVA"),
                    vatBox,
                    newProductBtn,
                    createProductBtn,
                    updateProductBtn,
                    deleteProductBtn);
            HBox.setHgrow(productNameField, Priority.ALWAYS);

            VBox root = new VBox(10, filters, productEditor, list);
            root.setPadding(new Insets(12));

            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Productos");
            Scene scene = new Scene(root, 1180, 680);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            modal.setScene(scene);
            modal.showAndWait();
        } catch (Exception e) {
            showError("No se pudo abrir Productos: " + e.getMessage());
        }
    }

    @FXML
    public void onCategorias() {
        try {
            ObservableList<Category> categories = FXCollections.observableArrayList(AppContext.get().catalogService().categories());
            ObservableList<Category> rows = FXCollections.observableArrayList(categories);

            TextField searchField = new TextField();
            searchField.setPromptText("Buscar categoria...");

            TextField newCategoryField = new TextField();
            newCategoryField.setPromptText("Nueva categoria");
            Button createCategoryBtn = new Button("Crear");

            TextField renameCategoryField = new TextField();
            renameCategoryField.setPromptText("Renombrar categoria seleccionada");
            Button renameCategoryBtn = new Button("Guardar cambios");
            Button deleteCategoryBtn = new Button("Eliminar");
            renameCategoryBtn.setDisable(true);
            deleteCategoryBtn.setDisable(true);

            ListView<Category> list = new ListView<>(rows);
            list.setPrefHeight(430);
            list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(Category item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name());
                }
            });

            Runnable refresh = () -> {
                categories.setAll(AppContext.get().catalogService().categories());
                String filter = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
                if (filter.isBlank()) {
                    rows.setAll(categories);
                    return;
                }
                rows.setAll(categories.stream()
                        .filter(c -> c.name().toLowerCase(Locale.ROOT).contains(filter))
                        .toList());
            };

            searchField.textProperty().addListener((obs, oldV, newV) -> refresh.run());
            list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
                boolean enabled = selected != null;
                renameCategoryBtn.setDisable(!enabled);
                deleteCategoryBtn.setDisable(!enabled);
                if (selected != null) {
                    renameCategoryField.setText(selected.name());
                } else {
                    renameCategoryField.clear();
                }
            });

            createCategoryBtn.setOnAction(evt -> {
                String name = newCategoryField.getText() == null ? "" : newCategoryField.getText().trim();
                if (name.length() < 2) {
                    showError("Nombre de categoria invalido (min 2 caracteres).");
                    return;
                }
                AppContext.get().catalogService().createCategory(name);
                newCategoryField.clear();
                refresh.run();
            });

            renameCategoryBtn.setOnAction(evt -> {
                Category selected = list.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    showError("Selecciona una categoria para editar.");
                    return;
                }
                String name = renameCategoryField.getText() == null ? "" : renameCategoryField.getText().trim();
                if (name.length() < 2) {
                    showError("Nombre de categoria invalido (min 2 caracteres).");
                    return;
                }
                AppContext.get().catalogService().updateCategory(selected.id(), name);
                refresh.run();
            });

            deleteCategoryBtn.setOnAction(evt -> {
                Category selected = list.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    showError("Selecciona una categoria para eliminar.");
                    return;
                }
                try {
                    List<Product> affected = AppContext.get().catalogService().productsByCategory(selected.id());
                    if (!affected.isEmpty()) {
                        if (!showConfirm(
                                "Productos afectados",
                                buildAffectedProductsMessage(selected, affected)
                        )) {
                            return;
                        }
                    }

                    if (!showConfirm(
                            "Confirmacion final",
                            "Estas seguro de borrar la categoria '" + selected.name()
                                    + "' con todos los productos asociados?"
                    )) {
                        return;
                    }

                    for (Product product : affected) {
                        AppContext.get().catalogService().deleteProduct(product.id());
                    }
                    AppContext.get().catalogService().deleteCategory(selected.id());
                    renameCategoryField.clear();
                    refresh.run();
                } catch (Exception ex) {
                    showError("No se pudo borrar categoria en cascada: " + ex.getMessage());
                }
            });

            refresh.run();

            HBox searchRow = new HBox(8, new Label("Buscar"), searchField);
            HBox.setHgrow(searchField, Priority.ALWAYS);

            HBox createRow = new HBox(8, new Label("Nueva"), newCategoryField, createCategoryBtn);
            HBox.setHgrow(newCategoryField, Priority.ALWAYS);

            HBox editRow = new HBox(8, new Label("Editar"), renameCategoryField, renameCategoryBtn, deleteCategoryBtn);
            HBox.setHgrow(renameCategoryField, Priority.ALWAYS);

            VBox root = new VBox(10, searchRow, createRow, editRow, list);
            root.setPadding(new Insets(12));

            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Categorias");
            Scene scene = new Scene(root, 760, 620);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            modal.setScene(scene);
            modal.showAndWait();
        } catch (Exception e) {
            showError("No se pudo abrir Categorias: " + e.getMessage());
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

    @FXML
    public void onSalones() {
        if (!AuthStore.hasRole("ADMIN")) {
            showError("Esta accion requiere rol ADMIN.");
            return;
        }
        try {
            ObservableList<SalonAreaResponse> salons = FXCollections.observableArrayList();
            ListView<SalonAreaResponse> list = new ListView<>(salons);
            list.setPrefHeight(420);
            list.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(SalonAreaResponse item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    setText(String.format(
                            Locale.ROOT,
                            "%s  |  Mesas %d-%d (%d)",
                            item.name(),
                            item.firstTableNumber(),
                            item.lastTableNumber(),
                            item.tableCount()
                    ));
                }
            });

            ObservableList<TableAliasResponse> tableAliases = FXCollections.observableArrayList();
            ListView<TableAliasResponse> aliasList = new ListView<>(tableAliases);
            aliasList.setPrefHeight(240);
            aliasList.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
                @Override
                protected void updateItem(TableAliasResponse item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        return;
                    }
                    String alias = item.alias() == null ? "" : item.alias().trim();
                    if (alias.isBlank()) {
                        alias = "(sin alias)";
                    }
                    setText("Mesa " + item.tableNumber() + "  |  " + alias);
                }
            });

            TextField salonNameField = new TextField();
            salonNameField.setPromptText("Nombre del salon (ej: Terraza)");

            TextField tableCountField = new TextField();
            tableCountField.setPromptText("Numero de mesas (ej: 8)");

            TextField firstTableField = new TextField();
            firstTableField.setPromptText("Mesa inicial opcional (ej: 21)");

            Button createBtn = new Button("Crear salon");
            Button renameBtn = new Button("Renombrar");
            Button deleteBtn = new Button("Eliminar");
            Button refreshBtn = new Button("Refrescar");
            renameBtn.setDisable(true);
            deleteBtn.setDisable(true);

            Label aliasTableLabel = new Label("Mesa: -");
            TextField aliasField = new TextField();
            aliasField.setPromptText("Alias interno (ej: VENTANA)");
            Button saveAliasBtn = new Button("Guardar alias");
            Button clearAliasBtn = new Button("Quitar alias");
            Button refreshAliasBtn = new Button("Refrescar alias");
            saveAliasBtn.setDisable(true);
            clearAliasBtn.setDisable(true);
            refreshAliasBtn.setDisable(true);

            Runnable refreshAliases = () -> {
                SalonAreaResponse selectedSalon = list.getSelectionModel().getSelectedItem();
                if (selectedSalon == null) {
                    tableAliases.clear();
                    aliasList.getSelectionModel().clearSelection();
                    aliasField.clear();
                    aliasTableLabel.setText("Mesa: -");
                    refreshAliasBtn.setDisable(true);
                    saveAliasBtn.setDisable(true);
                    clearAliasBtn.setDisable(true);
                    return;
                }
                try {
                    tableAliases.setAll(SalonAdminApi.listTableAliases(selectedSalon.id()));
                    refreshAliasBtn.setDisable(false);
                } catch (Exception e) {
                    showError("No se pudieron cargar alias: " + e.getMessage());
                }
            };

            Runnable refresh = () -> {
                try {
                    salons.setAll(SalonAdminApi.list());
                } catch (Exception e) {
                    showError("No se pudieron cargar salones: " + e.getMessage());
                }
            };

            list.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
                boolean enabled = selected != null;
                renameBtn.setDisable(!enabled);
                deleteBtn.setDisable(!enabled);
                if (selected != null) {
                    salonNameField.setText(selected.name());
                    tableCountField.setText(Integer.toString(selected.tableCount()));
                    firstTableField.setText(Integer.toString(selected.firstTableNumber()));
                }
                refreshAliases.run();
            });

            aliasList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
                boolean enabled = selected != null;
                saveAliasBtn.setDisable(!enabled);
                clearAliasBtn.setDisable(!enabled);
                if (!enabled) {
                    aliasField.clear();
                    aliasTableLabel.setText("Mesa: -");
                    return;
                }
                aliasTableLabel.setText("Mesa: " + selected.tableNumber());
                aliasField.setText(selected.alias() == null ? "" : selected.alias());
            });

            createBtn.setOnAction(evt -> {
                String name = salonNameField.getText() == null ? "" : salonNameField.getText().trim();
                if (name.length() < 2) {
                    showError("Nombre de salon invalido (min 2 caracteres).");
                    return;
                }
                int tableCount;
                try {
                    tableCount = Integer.parseInt(tableCountField.getText().trim());
                } catch (Exception ex) {
                    showError("Numero de mesas invalido.");
                    return;
                }
                Integer firstTable = null;
                if (firstTableField.getText() != null && !firstTableField.getText().trim().isBlank()) {
                    try {
                        firstTable = Integer.parseInt(firstTableField.getText().trim());
                    } catch (Exception ex) {
                        showError("Mesa inicial invalida.");
                        return;
                    }
                }
                try {
                    SalonAdminApi.create(name, tableCount, firstTable);
                    salonNameField.clear();
                    tableCountField.clear();
                    firstTableField.clear();
                    refresh.run();
                    refreshAliases.run();
                } catch (Exception e) {
                    showError("No se pudo crear salon: " + e.getMessage());
                }
            });

            renameBtn.setOnAction(evt -> {
                SalonAreaResponse selected = list.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    showError("Selecciona un salon para renombrar.");
                    return;
                }
                String name = salonNameField.getText() == null ? "" : salonNameField.getText().trim();
                if (name.length() < 2) {
                    showError("Nombre de salon invalido (min 2 caracteres).");
                    return;
                }
                try {
                    SalonAdminApi.rename(selected.id(), name);
                    refresh.run();
                    refreshAliases.run();
                } catch (Exception e) {
                    showError("No se pudo renombrar salon: " + e.getMessage());
                }
            });

            deleteBtn.setOnAction(evt -> {
                SalonAreaResponse selected = list.getSelectionModel().getSelectedItem();
                if (selected == null) {
                    showError("Selecciona un salon para eliminar.");
                    return;
                }
                if (!showConfirm(
                        "Eliminar salon",
                        "Se eliminara el salon '" + selected.name() + "' (mesas "
                                + selected.firstTableNumber() + "-" + selected.lastTableNumber() + ")."
                )) {
                    return;
                }
                if (!showConfirm(
                        "Confirmacion final",
                        "Solo se podra eliminar si no hay tickets abiertos ni bloqueos en esas mesas.\nContinuar?"
                )) {
                    return;
                }
                try {
                    SalonAdminApi.delete(selected.id());
                    refresh.run();
                    refreshAliases.run();
                } catch (Exception e) {
                    showError("No se pudo eliminar salon: " + e.getMessage());
                }
            });

            saveAliasBtn.setOnAction(evt -> {
                SalonAreaResponse selectedSalon = list.getSelectionModel().getSelectedItem();
                TableAliasResponse selectedTable = aliasList.getSelectionModel().getSelectedItem();
                if (selectedSalon == null || selectedTable == null) {
                    showError("Selecciona salon y mesa para guardar alias.");
                    return;
                }
                String alias = aliasField.getText() == null ? "" : aliasField.getText().trim();
                try {
                    SalonAdminApi.updateTableAlias(selectedSalon.id(), selectedTable.tableNumber(), alias);
                    refreshAliases.run();
                } catch (Exception e) {
                    showError("No se pudo guardar alias: " + e.getMessage());
                }
            });

            clearAliasBtn.setOnAction(evt -> {
                SalonAreaResponse selectedSalon = list.getSelectionModel().getSelectedItem();
                TableAliasResponse selectedTable = aliasList.getSelectionModel().getSelectedItem();
                if (selectedSalon == null || selectedTable == null) {
                    showError("Selecciona salon y mesa para quitar alias.");
                    return;
                }
                try {
                    SalonAdminApi.updateTableAlias(selectedSalon.id(), selectedTable.tableNumber(), "");
                    aliasField.clear();
                    refreshAliases.run();
                } catch (Exception e) {
                    showError("No se pudo quitar alias: " + e.getMessage());
                }
            });

            refreshBtn.setOnAction(evt -> {
                refresh.run();
                refreshAliases.run();
            });
            refreshAliasBtn.setOnAction(evt -> refreshAliases.run());

            HBox form = new HBox(8,
                    new Label("Salon"), salonNameField,
                    new Label("Mesas"), tableCountField,
                    new Label("Mesa inicial"), firstTableField,
                    createBtn, renameBtn, deleteBtn, refreshBtn
            );
            HBox.setHgrow(salonNameField, Priority.ALWAYS);

            HBox aliasForm = new HBox(8, aliasTableLabel, aliasField, saveAliasBtn, clearAliasBtn, refreshAliasBtn);
            HBox.setHgrow(aliasField, Priority.ALWAYS);

            VBox root = new VBox(
                    10,
                    form,
                    list,
                    new Label("Alias internos por mesa (no se imprimen en ticket/comprobante)"),
                    aliasForm,
                    aliasList
            );
            root.setPadding(new Insets(12));

            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Salones");
            Scene scene = new Scene(root, 980, 620);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            modal.setScene(scene);
            refresh.run();
            refreshAliases.run();
            modal.showAndWait();
        } catch (Exception e) {
            showError("No se pudo abrir Salones: " + e.getMessage());
        }
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
        UiDialogs.error("Error", message);
    }

    private boolean showConfirm(String title, String message) {
        return UiDialogs.confirm(title, message);
    }

    private static void configureCategoryCombo(ComboBox<Category> combo) {
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Category category) {
                return category == null ? "" : category.name();
            }

            @Override
            public Category fromString(String string) {
                return null;
            }
        });
        combo.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
    }

    private static String formatProductRow(Product product) {
        return String.format(
                Locale.US,
                "%-30s %8.2f EUR   IVA %4.1f%%   [%s]",
                product.name(),
                product.priceCents() / 100.0,
                product.vatRateBps() / 100.0,
                product.destination().name()
        );
    }

    private static int parsePriceToCents(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("empty");
        }
        String normalized = raw.trim().replace(",", ".");
        BigDecimal eur = new BigDecimal(normalized);
        if (eur.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("negative");
        }
        return eur.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private static String buildAffectedProductsMessage(Category category, List<Product> affected) {
        StringBuilder out = new StringBuilder();
        out.append("La categoria '").append(category.name()).append("' tiene ")
                .append(affected.size()).append(" producto(s) y se borraran en cascada:\n\n");
        int maxLines = Math.min(20, affected.size());
        for (int i = 0; i < maxLines; i++) {
            Product product = affected.get(i);
            out.append("- ").append(product.name()).append('\n');
        }
        if (affected.size() > maxLines) {
            out.append("... y ").append(affected.size() - maxLines).append(" mas.");
        }
        out.append("\n\nQuieres continuar?");
        return out.toString();
    }
}
