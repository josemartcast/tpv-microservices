package com.tpv.desktop.tpv.ui.controllers;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.OrderLine;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.services.LockException;
import com.tpv.desktop.tpv.ui.util.PrintUtil;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.ui.controllers.components.ProductButtonController;
import com.tpv.desktop.tpv.ui.controllers.components.TicketLineCellController;
import com.tpv.desktop.tpv.ui.controllers.components.TopBarController;
import com.tpv.desktop.tpv.ui.viewmodel.OrderViewModel;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class OrderController {
    @FXML private TopBarController topBarController;
    @FXML private ListView<OrderLine> ticketList;
    @FXML private Label subtotalLabel;
    @FXML private Label feedbackLabel;
    @FXML private Label orderHeader;
    @FXML private ToggleGroup categoryTabs;
    @FXML private HBox categoryTabsBox;
    @FXML private FlowPane productsPane;
    @FXML private Button sendBtn;
    @FXML private Button payBtn;
    @FXML private Button splitBtn;
    @FXML private Button prebillBtn;
    @FXML private Button moveBtn;
    @FXML private Button discountBtn;
    @FXML private Button noteBtn;
    @FXML private Button deleteBtn;

    private final OrderViewModel vm = new OrderViewModel();
    private Timeline heartbeat;

    public void bind(long orderId, int tableId, String tableLabel) {
        vm.bindOrder(orderId, tableId, tableLabel);
        rebuildCategoryTabs(selectedCategoryId());
        setupBindings();
        if (categoryTabs.getSelectedToggle() == null && !categoryTabs.getToggles().isEmpty()) {
            categoryTabs.getToggles().getFirst().setSelected(true);
        }
    }

    @FXML
    public void initialize() {
        ticketList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(OrderLine item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/TicketLineCell.fxml"));
                    Node node = loader.load();
                    TicketLineCellController c = loader.getController();
                    c.bind(item);
                    setGraphic(node);
                } catch (IOException e) {
                    setText(item.getProductName());
                }
            }
        });

        categoryTabs.selectedToggleProperty().addListener((obs, o, n) -> {
            if (n == null) {
                return;
            }
            Object data = n.getUserData();
            if (data instanceof Category selected) {
                loadProducts(selected);
            }
        });

        heartbeat = new Timeline(new KeyFrame(Duration.seconds(20), e -> onHeartbeatTick()));
        heartbeat.setCycleCount(Timeline.INDEFINITE);
        heartbeat.play();

        vm.lines().addListener((ListChangeListener<OrderLine>) change -> updateActionState());
        updateActionState();
    }

    private void setupBindings() {
        ticketList.setItems(vm.lines());
        subtotalLabel.textProperty().bind(vm.subtotalTextProperty());
        feedbackLabel.textProperty().bind(vm.feedbackProperty());
        vm.tableLabelProperty().addListener((obs, oldV, newV) -> refreshOrderHeader());
        vm.peopleProperty().addListener((obs, oldV, newV) -> refreshOrderHeader());
        vm.elapsedProperty().addListener((obs, oldV, newV) -> refreshOrderHeader());
        refreshOrderHeader();
        topBarController.setCenterTitle(AppContext.get().appState().restaurantNameProperty().get());
        AppContext.get().appState().restaurantNameProperty().addListener(
                (obs, oldV, newV) -> topBarController.setCenterTitle(newV)
        );
        updateActionState();
    }

    private void loadProducts(Category category) {
        if (category == null) return;
        vm.loadProducts(category);
        productsPane.getChildren().clear();
        for (Product product : vm.products()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/ProductButton.fxml"));
                Node node = loader.load();
                ProductButtonController controller = loader.getController();
                controller.bind(product, () -> vm.addProduct(product));
                productsPane.getChildren().add(node);
            } catch (IOException e) {
                Button fallback = new Button(product.name());
                fallback.setOnAction(evt -> vm.addProduct(product));
                productsPane.getChildren().add(fallback);
            }
        }
    }

    @FXML
    public void onBack() {
        stopHeartbeat();
        vm.closeOrReleaseOnBack();
        Navigator.get().goHome();
    }

    @FXML
    public void onSend() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/views/SendOrderDialog.fxml"));
            DialogPane pane = loader.load();
            SendOrderDialogController controller = loader.getController();
            controller.bind(vm);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Enviar Comanda");
            javafx.scene.Scene scene = new javafx.scene.Scene(pane);
            scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());
            stage.setScene(scene);
            stage.showAndWait();
        } catch (IOException e) {
            String msg = "No se pudo abrir modal de envio: " + e.getMessage();
            feedbackLabel.setText(msg);
            showErrorDialog("Enviar comanda", msg);
        }
    }
    @FXML
    public void onPay() {
        vm.requestBill();
        String method = promptPaymentMethod("Cobrar", "Selecciona metodo de pago");
        if (method == null) {
            return;
        }
        try {
            int pending = vm.pendingPaymentCents();
            if (pending <= 0) {
                String msg = "No hay importe pendiente.";
                feedbackLabel.setText(msg);
                showInfoDialog("Cobrar", msg);
                return;
            }

            ChoiceDialog<String> typeDialog = new ChoiceDialog<>("Total", "Total", "Parcial", "Parcial (lineas)");
            typeDialog.setTitle("Cobro");
            typeDialog.setHeaderText("Tipo de cobro");
            typeDialog.setContentText("Modo:");
            String mode = typeDialog.showAndWait().orElse("Total");

            boolean paid;
            if ("Parcial (lineas)".equalsIgnoreCase(mode)) {
                int amountCents = promptPartialByLines(
                        pending,
                        "Cobro parcial por lineas",
                        "Selecciona lineas y cantidades"
                );
                if (amountCents <= 0) {
                    return;
                }
                paid = vm.payPartial(method, amountCents);
            } else if ("Parcial".equalsIgnoreCase(mode)) {
                String defaultAmount = String.format(Locale.US, "%.2f", pending / 100.0);
                TextInputDialog amountDialog = new TextInputDialog(defaultAmount);
                amountDialog.setTitle("Cobro parcial");
                amountDialog.setHeaderText("Pendiente actual: " + defaultAmount + " EUR");
                amountDialog.setContentText("Importe a cobrar (EUR):");
                String amountText = amountDialog.showAndWait().orElse("");
                if (amountText == null || amountText.isBlank()) {
                    return;
                }
                int amountCents = parseAmountToCents(amountText);
                paid = vm.payPartial(method, amountCents);
            } else {
                paid = vm.payFull(method);
            }

            if (paid) {
                stopHeartbeat();
                Navigator.get().goHome();
            }
        } catch (Exception e) {
            String msg = "No se pudo cobrar: " + e.getMessage();
            feedbackLabel.setText(msg);
            showErrorDialog("Cobrar", msg);
        }
    }
    @FXML
    public void onSplit() {
        try {
            int pending = vm.pendingPaymentCents();
            if (pending <= 0) {
                String msg = "No hay importe pendiente para dividir.";
                feedbackLabel.setText(msg);
                showInfoDialog("Dividir", msg);
                return;
            }

            int amountCents = promptPartialByLines(
                    pending,
                    "Dividir cuenta",
                    "Selecciona lineas/cantidades para la parte separada"
            );
            if (amountCents <= 0) {
                return;
            }

            String method = promptPaymentMethod("Cobrar parte dividida", "Metodo para esta parte");
            if (method == null) {
                return;
            }

            boolean paid = vm.payPartial(method, amountCents);
            if (paid) {
                stopHeartbeat();
                Navigator.get().goHome();
                return;
            }

            feedbackLabel.setText("Parte dividida cobrada (" + money(amountCents) + ").");
        } catch (Exception e) {
            String msg = "No se pudo dividir/cobrar: " + e.getMessage();
            feedbackLabel.setText(msg);
            showErrorDialog("Dividir", msg);
        }
    }

    @FXML
    public void onPrebill() {
        if (vm.lines().isEmpty()) {
            String msg = "No hay lineas en ticket para pre-cuenta.";
            feedbackLabel.setText(msg);
            showInfoDialog("Precuenta", msg);
            return;
        }

        String text = buildPrebillText();
        TextArea preview = new TextArea(text);
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setPrefColumnCount(44);
        preview.setPrefRowCount(22);
        preview.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Precuenta");
        dialog.setHeaderText("Vista previa de pre-cuenta");
        ButtonType copyButton = new ButtonType("Copiar", ButtonBar.ButtonData.LEFT);
        ButtonType printPdfButton = new ButtonType("Print to PDF", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(copyButton, printPdfButton, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(preview);

        ButtonType action = dialog.showAndWait().orElse(ButtonType.CLOSE);
        if (action == copyButton) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            feedbackLabel.setText("Pre-cuenta copiada al portapapeles.");
            showInfoDialog("Precuenta", "Pre-cuenta copiada al portapapeles.");
            return;
        }
        if (action == printPdfButton) {
            printPrebillToPdf(text);
            feedbackLabel.setText("Enviado a Print to PDF.");
            showInfoDialog("Precuenta", "Enviado a Print to PDF.");
        }
    }

    @FXML
    public void onMoveTable() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Mover ticket a otra mesa");
        dialog.setContentText("Mesa destino:");
        dialog.showAndWait().ifPresent(value -> {
            try {
                int newTable = Integer.parseInt(value.trim());
                vm.moveToTable(newTable);
                refreshOrderHeader();
            } catch (NumberFormatException e) {
                String msg = "Mesa destino invalida.";
                feedbackLabel.setText(msg);
                showInfoDialog("Mover mesa", msg);
            } catch (Exception e) {
                String msg = "No se pudo mover mesa: " + e.getMessage();
                feedbackLabel.setText(msg);
                showErrorDialog("Mover mesa", msg);
            }
        });
    }

    @FXML
    public void onDiscount() {
        try {
            ChoiceDialog<String> typeDialog = new ChoiceDialog<>("Porcentaje", "Porcentaje", "Importe", "Quitar");
            typeDialog.setTitle("Descuento");
            typeDialog.setHeaderText("Aplicar descuento al ticket");
            typeDialog.setContentText("Modo:");
            String mode = typeDialog.showAndWait().orElse("");
            if (mode.isBlank()) {
                return;
            }

            if ("Quitar".equalsIgnoreCase(mode)) {
                vm.clearDiscount();
                return;
            }

            if ("Porcentaje".equalsIgnoreCase(mode)) {
                TextInputDialog d = new TextInputDialog("10");
                d.setTitle("Descuento %");
                d.setHeaderText("Introduce porcentaje (0-100)");
                d.setContentText("Porcentaje:");
                String raw = d.showAndWait().orElse("");
                if (raw.isBlank()) return;
                int percent = Integer.parseInt(raw.trim());
                vm.applyDiscountPercent(percent);
                return;
            }

            TextInputDialog d = new TextInputDialog("1.00");
            d.setTitle("Descuento importe");
            d.setHeaderText("Introduce importe de descuento");
            d.setContentText("EUR:");
            String raw = d.showAndWait().orElse("");
            if (raw.isBlank()) return;
            int amountCents = parseAmountToCents(raw);
            vm.applyDiscountAmount(amountCents);
        } catch (Exception e) {
            String msg = "No se pudo aplicar descuento: " + e.getMessage();
            feedbackLabel.setText(msg);
            showErrorDialog("Descuento", msg);
        }
    }

    @FXML
    public void onNote() {
        TextInputDialog d = new TextInputDialog("");
        d.setHeaderText("Nota para ultima linea pendiente");
        d.showAndWait().ifPresent(vm::addNoteToLastPending);
    }

    @FXML
    public void onDeleteLine() {
        vm.removeLastPending();
    }

    @FXML
    public void onCancelOrder() {
        stopHeartbeat();
        vm.cancelOrder();
        Navigator.get().goHome();
    }

    private void stopHeartbeat() {
        if (heartbeat != null) {
            heartbeat.stop();
        }
    }

    private void onHeartbeatTick() {
        try {
            vm.heartbeatLock();
            if (vm.refreshCategories()) {
                rebuildCategoryTabs(selectedCategoryId());
            }
        } catch (LockException ex) {
            if (ex.isRecoverableWithReacquire()) {
                return;
            }
            if (ex.isOwnershipConflict()) {
                stopHeartbeat();
                vm.feedbackProperty().set("Mesa bloqueada por otro terminal. Volviendo al salon.");
                Navigator.get().goHome();
                return;
            }
            if (ex.isAuthIssue()) {
                stopHeartbeat();
                vm.feedbackProperty().set("Sesion expirada durante lock. Vuelve a entrar.");
                Navigator.get().goHome();
                return;
            }
            vm.feedbackProperty().set("Error renovando lock: " + ex.getMessage());
        } catch (RuntimeException ex) {
            vm.feedbackProperty().set("Error renovando lock: " + ex.getMessage());
        }
    }

    private void rebuildCategoryTabs(Long preferredCategoryId) {
        categoryTabs.getToggles().clear();
        categoryTabsBox.getChildren().clear();

        if (vm.categories().isEmpty()) {
            Label empty = new Label("Sin categorias");
            empty.getStyleClass().add("home-filter-label");
            categoryTabsBox.getChildren().add(empty);
            productsPane.getChildren().clear();
            return;
        }

        ToggleButton toSelect = null;
        for (Category category : vm.categories()) {
            ToggleButton button = new ToggleButton(category.name());
            button.setToggleGroup(categoryTabs);
            button.setUserData(category);
            button.getStyleClass().add("category-tab");
            categoryTabsBox.getChildren().add(button);
            if (preferredCategoryId != null && preferredCategoryId == category.id()) {
                toSelect = button;
            }
        }

        if (toSelect == null) {
            toSelect = (ToggleButton) categoryTabs.getToggles().getFirst();
        }
        toSelect.setSelected(true);
    }

    private Long selectedCategoryId() {
        Toggle selected = categoryTabs.getSelectedToggle();
        if (selected == null) {
            return null;
        }
        Object data = selected.getUserData();
        if (data instanceof Category category) {
            return category.id();
        }
        return null;
    }

    private void updateActionState() {
        boolean hasLines = !vm.lines().isEmpty();
        boolean hasPending = vm.lines().stream().anyMatch(line -> line.getPendingQty() > 0);

        // Keep ENVIAR always available so the modal can be opened for "Reimprimir ultimo"
        // even when there are no pending lines.
        if (sendBtn != null) sendBtn.setDisable(false);
        if (noteBtn != null) noteBtn.setDisable(!hasPending);
        if (deleteBtn != null) deleteBtn.setDisable(!hasPending);

        if (payBtn != null) payBtn.setDisable(!hasLines);
        if (splitBtn != null) splitBtn.setDisable(!hasLines);
        if (prebillBtn != null) prebillBtn.setDisable(!hasLines);
        if (moveBtn != null) moveBtn.setDisable(!hasLines);
        if (discountBtn != null) discountBtn.setDisable(!hasLines);
    }
    private static int parseAmountToCents(String rawAmount) {
        String normalized = rawAmount.trim().replace(",", ".");
        BigDecimal eur = new BigDecimal(normalized);
        if (eur.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El importe debe ser mayor que cero.");
        }
        return eur.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValueExact();
    }

    private String promptPaymentMethod(String title, String header) {
        ChoiceDialog<String> methodDialog = new ChoiceDialog<>("CARD", "CASH", "CARD", "BIZUM");
        methodDialog.setTitle(title);
        methodDialog.setHeaderText(header);
        methodDialog.setContentText("Metodo:");
        return methodDialog.showAndWait().orElse(null);
    }
    private int promptPartialByLines(int pendingCents, String title, String header) {
        if (vm.lines().isEmpty()) {
            feedbackLabel.setText("No hay lineas para cobro parcial.");
            return 0;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);

        ButtonType applyButton = new ButtonType("Aplicar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyButton, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPadding(new Insets(8, 4, 4, 4));

        Label pendingLabel = new Label("Pendiente: " + money(pendingCents));
        pendingLabel.getStyleClass().add("ticket-subtotal-label");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);
        grid.add(new Label("Pagar"), 0, 0);
        grid.add(new Label("Producto"), 1, 0);
        grid.add(new Label("Cant."), 2, 0);
        grid.add(new Label("Subtotal"), 3, 0);

        var selections = new java.util.ArrayList<LineSelection>();
        int row = 1;
        for (OrderLine line : vm.lines()) {
            if (line.getQty() <= 0) continue;

            CheckBox include = new CheckBox();
            Label name = new Label(line.getProductName());
            Spinner<Integer> qty = new Spinner<>(0, line.getQty(), 0);
            qty.setEditable(false);
            qty.setPrefWidth(84);
            Label subtotal = new Label("0.00 EUR");

            LineSelection selection = new LineSelection(line, include, qty);
            selections.add(selection);

            include.selectedProperty().addListener((obs, oldVal, selected) -> {
                if (selected && qty.getValue() == 0) {
                    qty.getValueFactory().setValue(line.getQty());
                }
                if (!selected) {
                    qty.getValueFactory().setValue(0);
                }
                subtotal.setText(money(selection.selectedCents()));
            });
            qty.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal != null && newVal > 0 && !include.isSelected()) {
                    include.setSelected(true);
                }
                if (newVal != null && newVal == 0 && include.isSelected()) {
                    include.setSelected(false);
                }
                subtotal.setText(money(selection.selectedCents()));
            });

            grid.add(include, 0, row);
            grid.add(name, 1, row);
            grid.add(qty, 2, row);
            grid.add(subtotal, 3, row);
            row++;
        }

        Label selectedLabel = new Label("Seleccionado: 0.00 EUR");
        selectedLabel.getStyleClass().add("ticket-subtotal-value");

        Runnable refreshSelected = () -> {
            int selected = selections.stream().mapToInt(LineSelection::selectedCents).sum();
            selectedLabel.setText("Seleccionado: " + money(selected));
        };

        selections.forEach(s -> {
            s.include().selectedProperty().addListener((obs, o, n) -> refreshSelected.run());
            s.qty().valueProperty().addListener((obs, o, n) -> refreshSelected.run());
        });
        refreshSelected.run();

        ScrollPane scroll = new ScrollPane(grid);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(340);

        HBox totals = new HBox(selectedLabel);
        totals.setAlignment(Pos.CENTER_RIGHT);
        HBox.setHgrow(selectedLabel, Priority.ALWAYS);

        content.getChildren().addAll(pendingLabel, scroll, totals);
        dialog.getDialogPane().setContent(content);

        var result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != applyButton) {
            return 0;
        }

        int selectedCents = selections.stream().mapToInt(LineSelection::selectedCents).sum();
        if (selectedCents <= 0) {
            throw new IllegalArgumentException("Selecciona al menos una linea.");
        }
        if (selectedCents > pendingCents) {
            throw new IllegalArgumentException("Seleccion supera el pendiente (" + money(pendingCents) + ").");
        }
        return selectedCents;
    }

    private static String money(int cents) {
        return String.format(Locale.US, "%.2f EUR", cents / 100.0);
    }

    private String buildPrebillText() {
        StringBuilder out = new StringBuilder();
        String restaurantName = AppContext.get().appState().restaurantNameProperty().get();
        String legalName = SettingsStore.getFiscalLegalName();
        String taxId = SettingsStore.getFiscalTaxId();
        String fiscalAddress = SettingsStore.getFiscalAddress();
        String fiscalPostalCode = SettingsStore.getFiscalPostalCode();
        String fiscalCity = SettingsStore.getFiscalCity();
        String fiscalProvince = SettingsStore.getFiscalProvince();
        String fiscalCountry = SettingsStore.getFiscalCountry();
        String fiscalPhone = SettingsStore.getFiscalPhone();
        String fiscalEmail = SettingsStore.getFiscalEmail();

        String headerName = (restaurantName == null || restaurantName.isBlank() ? "RESTAURANTE" : restaurantName);
        if (legalName != null && !legalName.isBlank()) {
            headerName = legalName;
        }

        out.append(headerName.toUpperCase(Locale.ROOT)).append('\n');
        if (taxId != null && !taxId.isBlank()) {
            out.append("NIF/CIF ").append(taxId).append('\n');
        }
        if ((fiscalAddress != null && !fiscalAddress.isBlank())
                || (fiscalPostalCode != null && !fiscalPostalCode.isBlank())
                || (fiscalCity != null && !fiscalCity.isBlank())) {
            if (fiscalAddress != null && !fiscalAddress.isBlank()) {
                out.append(clip(fiscalAddress, 42)).append('\n');
            }
            String cityLine = String.format(
                    Locale.ROOT,
                    "%s %s %s",
                    fiscalPostalCode == null ? "" : fiscalPostalCode.trim(),
                    fiscalCity == null ? "" : fiscalCity.trim(),
                    fiscalProvince == null ? "" : fiscalProvince.trim()
            ).trim();
            if (!cityLine.isBlank()) {
                out.append(clip(cityLine, 42)).append('\n');
            }
            if (fiscalCountry != null && !fiscalCountry.isBlank()) {
                out.append(fiscalCountry.trim().toUpperCase(Locale.ROOT)).append('\n');
            }
        }
        if (fiscalPhone != null && !fiscalPhone.isBlank()) {
            out.append("TEL ").append(clip(fiscalPhone, 36)).append('\n');
        }
        if (fiscalEmail != null && !fiscalEmail.isBlank()) {
            out.append(clip(fiscalEmail, 42)).append('\n');
        }
        out.append("PRECUENTA").append('\n');
        out.append("Mesa ").append(vm.tableIdProperty().get())
                .append("  Ticket ").append(vm.orderIdProperty().get()).append('\n');
        out.append("Cliente ").append(AppContext.get().appState().activeCustomerProperty().get()).append('\n');
        out.append("Fecha ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        out.append("--------------------------------------------").append('\n');
        for (OrderLine line : vm.lines()) {
            int lineTotal = line.getQty() * line.getUnitPriceCents();
            out.append(String.format(Locale.US, "%2dx %-24s %8.2f", line.getQty(), clip(line.getProductName(), 24), lineTotal / 100.0))
                    .append('\n');
            if (line.getNote() != null && !line.getNote().isBlank()) {
                out.append("   - ").append(line.getNote()).append('\n');
            }
        }
        out.append("--------------------------------------------").append('\n');
        out.append(String.format(Locale.US, "TOTAL:%33.2f", vm.lines().stream()
                .mapToInt(l -> l.getQty() * l.getUnitPriceCents())
                .sum() / 100.0)).append('\n');
        out.append(String.format(Locale.US, "PENDIENTE:%29.2f", vm.pendingPaymentCents() / 100.0)).append('\n');
        out.append("--------------------------------------------").append('\n');
        out.append("Gracias. Esta pre-cuenta no es factura.").append('\n');
        return out.toString();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + ".";
    }

    private void refreshOrderHeader() {
        String tableLabel = tableLabelOrFallback();
        orderHeader.setText(tableLabel + " - " + vm.peopleProperty().get() + " Personas / " + vm.elapsedProperty().get());
    }

    private String tableLabelOrFallback() {
        String value = vm.tableLabelProperty().get();
        if (value == null || value.isBlank()) {
            return "Mesa " + vm.tableIdProperty().get();
        }
        return value;
    }

    private void printPrebillToPdf(String text) {
        PrintUtil.printTextToPdf(text, feedbackLabel != null && feedbackLabel.getScene() != null ? feedbackLabel.getScene().getWindow() : null);
    }

    private void showInfoDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.showAndWait();
    }

    private void showErrorDialog(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.showAndWait();
    }

    private record LineSelection(OrderLine line, CheckBox include, Spinner<Integer> qty) {
        int selectedCents() {
            Integer value = qty.getValue();
            int qtyValue = value == null ? 0 : value;
            return include.isSelected() ? qtyValue * line.getUnitPriceCents() : 0;
        }
    }
}




