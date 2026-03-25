package com.tpv.desktop.tpv.ui.controllers;

import com.tpv.desktop.api.pos.TicketHistoryApi;
import com.tpv.desktop.api.pos.TicketSummaryResponse;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.ui.UiDialogs;
import com.tpv.desktop.tpv.app.Navigator;
import com.tpv.desktop.tpv.domain.model.Category;
import com.tpv.desktop.tpv.domain.model.OrderLine;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.services.LockException;
import com.tpv.desktop.tpv.services.local.DesktopComandaAutoPrintService;
import com.tpv.desktop.tpv.ui.util.PrintUtil;
import com.tpv.desktop.core.PrinterSettingsStore;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.ui.controllers.components.ProductButtonController;
import com.tpv.desktop.tpv.ui.controllers.components.TicketLineCellController;
import com.tpv.desktop.tpv.ui.controllers.components.TopBarController;
import com.tpv.desktop.tpv.ui.viewmodel.OrderViewModel;
import com.tpv.desktop.ui.components.NumericPadController;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OrderController {
    private static final int RECEIPT_LINE_WIDTH = 42;
    private static final int RECEIPT_QTY_COL_WIDTH = 4;
    private static final int RECEIPT_DESC_COL_WIDTH = 21;
    private static final int RECEIPT_UNIT_COL_WIDTH = 7;
    private static final int RECEIPT_TOTAL_COL_WIDTH = 7;

    @FXML private TopBarController topBarController;
    @FXML private ListView<OrderLine> ticketList;
    @FXML private Label subtotalLabel;
    @FXML private Label feedbackLabel;
    @FXML private Label orderHeader;
    @FXML private ToggleGroup categoryTabs;
    @FXML private HBox categoryTabsBox;
    @FXML private TextField qtyField;
    @FXML private NumericPadController orderPadController;
    @FXML private FlowPane productsPane;
    @FXML private Button sendBtn;
    @FXML private Button payBtn;
    @FXML private Button splitBtn;
    @FXML private Button prebillBtn;
    @FXML private Button moveBtn;
    @FXML private Button discountBtn;
    @FXML private Button openDrawerBtn;
    @FXML private Button noteBtn;
    @FXML private Button deleteBtn;
    @FXML private Button editBtn;

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

        if (qtyField != null) {
            qtyField.setText("");
        }
        if (orderPadController != null && qtyField != null) {
            orderPadController.bindTargets(qtyField);
        }

        vm.lines().addListener((ListChangeListener<OrderLine>) change -> updateActionState());
        ticketList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> updateActionState());
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
                controller.bind(product, () -> onProductClicked(product));
                productsPane.getChildren().add(node);
            } catch (IOException e) {
                Button fallback = new Button(product.name());
                fallback.setOnAction(evt -> onProductClicked(product));
                productsPane.getChildren().add(fallback);
            }
        }
    }

    private void onProductClicked(Product product) {
        int qty = parseQtyOrDefault();
        vm.addProduct(product, qty);
        if (qtyField != null) {
            qtyField.setText("");
        }
    }

    private int parseQtyOrDefault() {
        if (qtyField == null) {
            return 1;
        }
        String raw = qtyField.getText();
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(1, value);
        } catch (NumberFormatException ex) {
            qtyField.setText("1");
            return 1;
        }
    }

    @FXML
    public void onBack() {
        if (hasPendingSendLines()) {
            boolean sendNow = UiDialogs.confirm(
                    "Enviar comanda",
                    "Hay lineas pendientes de enviar.\n\nQuieres enviarlas ahora (BAR + COCINA + POSTRES) antes de salir?"
            );
            if (sendNow) {
                try {
                    boolean sent = vm.sendAll(true);
                    if (!sent) {
                        setFeedback("No se pudo enviar comanda pendiente.");
                        return;
                    }
                } catch (Exception ex) {
                    String msg = "No se pudo enviar comanda pendiente: " + ex.getMessage();
                    setFeedback(msg);
                    showErrorDialog("Enviar comanda", msg);
                    return;
                }
            }
        }
        stopHeartbeat();
        vm.closeOrReleaseOnBack();
        Navigator.get().goHome();
    }

    private boolean hasPendingSendLines() {
        try {
            return vm.pendingByDestination().values().stream().mapToInt(Integer::intValue).sum() > 0;
        } catch (Exception ignored) {
            return false;
        }
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
            setFeedback(msg);
            showErrorDialog("Enviar comanda", msg);
        }
    }
    @FXML
    public void onPay() {
        DesktopComandaAutoPrintService.markLocalPrebillRequest(vm.orderIdProperty().get());
        vm.requestBill();
        String method = promptPaymentMethod("Cobrar", "Selecciona metodo de pago");
        if (method == null) {
            return;
        }
        try {
            int pending = vm.pendingPaymentCents();
            if (pending <= 0) {
                String msg = "No hay importe pendiente.";
                setFeedback(msg);
                showInfoDialog("Cobrar", msg);
                return;
            }

            ChoiceDialog<String> typeDialog = new ChoiceDialog<>("Total", "Total", "Parcial", "Parcial (lineas)");
            typeDialog.setTitle("Cobro");
            typeDialog.setHeaderText("Tipo de cobro");
            typeDialog.setContentText("Modo:");
            String mode = typeDialog.showAndWait().orElse("Total");

            boolean paid;
            int paidAmountCents;
            if ("Parcial (lineas)".equalsIgnoreCase(mode)) {
                int amountCents = promptPartialByLines(
                        pending,
                        "Cobro parcial por lineas",
                        "Selecciona lineas y cantidades"
                );
                if (amountCents <= 0) {
                    return;
                }
                paidAmountCents = amountCents;
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
                paidAmountCents = amountCents;
                paid = vm.payPartial(method, amountCents);
            } else {
                paidAmountCents = pending;
                paid = vm.payFull(method);
            }

            if (paid) {
                DesktopComandaAutoPrintService.markLocalPayment(vm.orderIdProperty().get());
                printPaidTicketSafe(method, paidAmountCents);
                stopHeartbeat();
                Navigator.get().goHome();
            }
        } catch (Exception e) {
            String msg = "No se pudo cobrar: " + e.getMessage();
            setFeedback(msg);
            showErrorDialog("Cobrar", msg);
        }
    }
    @FXML
    public void onSplit() {
        try {
            int pending = vm.pendingPaymentCents();
            if (pending <= 0) {
                String msg = "No hay importe pendiente para dividir.";
                setFeedback(msg);
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
                DesktopComandaAutoPrintService.markLocalPayment(vm.orderIdProperty().get());
                printPaidTicketSafe(method, amountCents);
                stopHeartbeat();
                Navigator.get().goHome();
                return;
            }

            setFeedback("Parte dividida cobrada (" + money(amountCents) + ").");
        } catch (Exception e) {
            String msg = "No se pudo dividir/cobrar: " + e.getMessage();
            setFeedback(msg);
            showErrorDialog("Dividir", msg);
        }
    }

    @FXML
    public void onPrebill() {
        if (vm.lines().isEmpty()) {
            String msg = "No hay lineas en ticket para pre-cuenta.";
            setFeedback(msg);
            showInfoDialog("Precuenta", msg);
            return;
        }

        String text = buildPrebillText();
        TextArea preview = new TextArea(text);
        preview.setEditable(false);
        preview.setWrapText(false);
        preview.setPrefColumnCount(44);
        preview.setPrefRowCount(22);
        preview.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12px; -fx-font-weight: bold;");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Precuenta");
        dialog.setHeaderText("Vista previa de pre-cuenta");
        ButtonType copyButton = new ButtonType("Copiar", ButtonBar.ButtonData.LEFT);
        ButtonType printButton = new ButtonType("Imprimir", ButtonBar.ButtonData.LEFT);
        ButtonType printPdfButton = new ButtonType("Print to PDF", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(copyButton, printButton, printPdfButton, ButtonType.CLOSE);
        dialog.getDialogPane().setContent(preview);

        ButtonType action = dialog.showAndWait().orElse(ButtonType.CLOSE);
        if (action == copyButton) {
            ClipboardContent content = new ClipboardContent();
            content.putString(text);
            Clipboard.getSystemClipboard().setContent(content);
            setFeedback("Pre-cuenta copiada al portapapeles.");
            showInfoDialog("Precuenta", "Pre-cuenta copiada al portapapeles.");
            return;
        }
        if (action == printButton) {
            String target = printDocumentToGeneral(text);
            setFeedback("Pre-cuenta enviada a " + target + ".");
            showInfoDialog("Precuenta", "Pre-cuenta enviada a " + target + ".");
            return;
        }
        if (action == printPdfButton) {
            PrintUtil.printTextToPdfWithBottomMargin(text, feedbackLabel != null && feedbackLabel.getScene() != null ? feedbackLabel.getScene().getWindow() : null);
            setFeedback("Enviado a Print to PDF.");
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
                setFeedback(msg);
                showInfoDialog("Mover mesa", msg);
            } catch (Exception e) {
                String msg = "No se pudo mover mesa: " + e.getMessage();
                setFeedback(msg);
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
            setFeedback(msg);
            showErrorDialog("Descuento", msg);
        }
    }

    @FXML
    public void onOpenDrawer() {
        try {
            String printerName = resolveDrawerPrinter();
            if (printerName == null || printerName.isBlank()) {
                String msg = "No hay impresora configurada para abrir cajon. Configura una impresora en Settings.";
                setFeedback(msg);
                showInfoDialog("Abrir cajon", msg);
                return;
            }
            PrintUtil.openCashDrawer(printerName);
            String msg = "Senal de apertura enviada a: " + printerName;
            setFeedback(msg);
            showInfoDialog("Abrir cajon", msg);
        } catch (Exception e) {
            String msg = "No se pudo abrir cajon: " + e.getMessage();
            setFeedback(msg);
            showErrorDialog("Abrir cajon", msg);
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
        OrderLine selected = ticketList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            String msg = "Selecciona una linea para anular.";
            setFeedback(msg);
            showInfoDialog("Anular linea", msg);
            return;
        }
        try {
            vm.removeLine(selected.getId());
        } catch (Exception e) {
            String msg = "No se pudo anular linea: " + e.getMessage();
            setFeedback(msg);
            showErrorDialog("Anular linea", msg);
        }
    }

    @FXML
    public void onEditLine() {
        OrderLine selected = ticketList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Editar linea");
        dialog.setHeaderText(selected.getProductName());

        ButtonType saveButton = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);
        grid.setPadding(new Insets(10, 10, 10, 10));

        TextField qtyInput = new TextField(String.valueOf(selected.getQty()));
        TextField priceInput = new TextField(String.format(Locale.US, "%.2f", selected.getUnitPriceCents() / 100.0));

        grid.add(new Label("Cantidad"), 0, 0);
        grid.add(qtyInput, 1, 0);
        grid.add(new Label("Precio (EUR)"), 0, 1);
        grid.add(priceInput, 1, 1);

        dialog.getDialogPane().setContent(grid);

        ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (result != saveButton) {
            return;
        }

        try {
            int qty = Integer.parseInt(qtyInput.getText().trim());
            if (qty < 1) {
                throw new IllegalArgumentException("La cantidad debe ser >= 1.");
            }
            int priceCents = parseAmountToCents(priceInput.getText());

            if (qty != selected.getQty()) {
                vm.updateLineQty(selected.getId(), qty);
            }
            if (priceCents != selected.getUnitPriceCents()) {
                vm.updateLinePrice(selected.getId(), priceCents);
            }
        } catch (Exception e) {
            String msg = "No se pudo editar linea: " + e.getMessage();
            setFeedback(msg);
            showErrorDialog("Editar linea", msg);
        }
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
        boolean hasSelection = ticketList.getSelectionModel().getSelectedItem() != null;

        // Keep ENVIAR always available so the modal can be opened for "Reimprimir ultimo"
        // even when there are no pending lines.
        if (sendBtn != null) sendBtn.setDisable(false);
        if (noteBtn != null) noteBtn.setDisable(!hasPending);
        if (deleteBtn != null) deleteBtn.setDisable(!hasSelection);
        if (editBtn != null) editBtn.setDisable(!hasSelection);

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
        List<PaymentChoice> choices = List.of(
                new PaymentChoice("EFECTIVO", "CASH"),
                new PaymentChoice("TARJETA", "CARD"),
                new PaymentChoice("BIZUM", "BIZUM")
        );
        ChoiceDialog<PaymentChoice> methodDialog = new ChoiceDialog<>(choices.get(1), choices);
        methodDialog.setTitle(title);
        methodDialog.setHeaderText(header);
        methodDialog.setContentText("Metodo:");
        return methodDialog.showAndWait().map(PaymentChoice::apiCode).orElse(null);
    }
    private int promptPartialByLines(int pendingCents, String title, String header) {
        if (vm.lines().isEmpty()) {
            setFeedback("No hay lineas para cobro parcial.");
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

    private String resolveDrawerPrinter() {
        String printer = firstUsablePrinter(PrinterSettingsStore.resolveSystemPrintersForDestination("GENERAL"));
        if (printer != null) return printer;
        printer = firstUsablePrinter(PrinterSettingsStore.resolveSystemPrintersForDestination("ALL"));
        if (printer != null) return printer;
        printer = firstUsablePrinter(PrinterSettingsStore.resolveSystemPrintersForDestination("BAR"));
        if (printer != null) return printer;
        printer = firstUsablePrinter(PrinterSettingsStore.resolveSystemPrintersForDestination("COCINA"));
        if (printer != null) return printer;
        return firstUsablePrinter(PrinterSettingsStore.resolveSystemPrintersForDestination("POSTRES"));
    }

    private static String firstUsablePrinter(java.util.List<String> printers) {
        if (printers == null) return null;
        for (String p : printers) {
            if (p == null || p.isBlank()) continue;
            String lower = p.toLowerCase(Locale.ROOT);
            if (lower.contains("pdf")) continue;
            return p.trim();
        }
        return null;
    }

    private void appendBusinessHeader(StringBuilder out, String documentTitle) {
        String restaurantName = AppContext.get().appState().restaurantNameProperty().get();
        String taxId = SettingsStore.getFiscalTaxId();
        String fiscalAddress = SettingsStore.getFiscalAddress();
        String fiscalPostalCode = SettingsStore.getFiscalPostalCode();
        String fiscalCity = SettingsStore.getFiscalCity();
        String fiscalProvince = SettingsStore.getFiscalProvince();
        String fiscalCountry = SettingsStore.getFiscalCountry();
        String fiscalPhone = SettingsStore.getFiscalPhone();
        String fiscalEmail = SettingsStore.getFiscalEmail();

        String headerName = (restaurantName == null || restaurantName.isBlank() ? "RESTAURANTE" : restaurantName);

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
        out.append(documentTitle).append('\n');
    }

    private String buildPrebillText() {
        StringBuilder out = new StringBuilder();
        appendBusinessHeader(out, "PRECUENTA");
        appendWrappedLine(out, "Mesa " + vm.tableIdProperty().get() + " Ticket " + vm.orderIdProperty().get());
        appendWrappedLine(out, "Fecha " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        out.append(receiptSeparator()).append('\n');
        appendReceiptItemsHeader(out);
        out.append(receiptSeparator()).append('\n');
        for (OrderLine line : vm.lines()) {
            int unitPrice = line.getUnitPriceCents();
            int lineTotal = line.getQty() * line.getUnitPriceCents();
            appendReceiptLineWithAmounts(out, line.getQty(), line.getProductName(), unitPrice, lineTotal);
            if (line.getNote() != null && !line.getNote().isBlank()) {
                out.append("   - ").append(line.getNote()).append('\n');
            }
        }
        out.append(receiptSeparator()).append('\n');
        appendReceiptAmountLine(out, "TOTAL", vm.lines().stream()
                .mapToInt(l -> l.getQty() * l.getUnitPriceCents())
                .sum());
        appendReceiptAmountLine(out, "PENDIENTE", vm.pendingPaymentCents());
        out.append(receiptSeparator()).append('\n');
        out.append("Gracias. Esta pre-cuenta no es factura.").append('\n');
        return out.toString();
    }

    private String buildPaidTicketText(String method, int paidAmountCents) {
        StringBuilder out = new StringBuilder();
        appendBusinessHeader(out, "TICKET CLIENTE");
        appendWrappedLine(out, tableLabelForTicket() + " Ticket " + vm.orderIdProperty().get());
        appendWrappedLine(out, "Fecha " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        out.append(receiptSeparator()).append('\n');
        appendReceiptItemsHeader(out);
        out.append(receiptSeparator()).append('\n');
        for (OrderLine line : vm.lines()) {
            int unitPrice = line.getUnitPriceCents();
            int lineTotal = line.getQty() * line.getUnitPriceCents();
            appendReceiptLineWithAmounts(out, line.getQty(), line.getProductName(), unitPrice, lineTotal);
            if (line.getNote() != null && !line.getNote().isBlank()) {
                out.append("   - ").append(line.getNote()).append('\n');
            }
        }

        PaidTicketSummary summary = resolvePaidTicketSummary(method, paidAmountCents);
        out.append(receiptSeparator()).append('\n');
        appendReceiptAmountLine(out, "TOTAL", summary.totalCents());
        out.append("IVA INCLUIDO").append('\n');
        appendReceiptAmountLine(out, "PAGADO", summary.paidCents());
        if (summary.paymentBreakdown().isEmpty()) {
            appendReceiptFieldLine(out, "METODO", normalizePaymentMethod(method));
        } else {
            for (Map.Entry<String, Integer> entry : summary.paymentBreakdown().entrySet()) {
                appendReceiptAmountLine(out, entry.getKey(), entry.getValue());
            }
        }
        out.append(receiptSeparator()).append('\n');
        out.append("Gracias por su visita.").append('\n');
        return out.toString();
    }

    private PaidTicketSummary resolvePaidTicketSummary(String fallbackMethod, int fallbackPaidAmountCents) {
        int fallbackTotal = vm.lines().stream().mapToInt(l -> l.getQty() * l.getUnitPriceCents()).sum();
        LinkedHashMap<String, Integer> fallbackBreakdown = new LinkedHashMap<>();
        if (fallbackPaidAmountCents > 0) {
            fallbackBreakdown.put(normalizePaymentMethod(fallbackMethod), fallbackPaidAmountCents);
        }

        try {
            long ticketId = vm.orderIdProperty().get();
            TicketSummaryResponse summary = TicketHistoryApi.summary(ticketId);
            if (summary == null) {
                return new PaidTicketSummary(fallbackTotal, fallbackPaidAmountCents, fallbackBreakdown);
            }

            int totalCents = summary.totalCents() > 0 ? summary.totalCents() : fallbackTotal;
            LinkedHashMap<String, Integer> breakdown = new LinkedHashMap<>();
            if (summary.payments() != null) {
                for (TicketSummaryResponse.PaymentSummary payment : summary.payments()) {
                    if (payment == null || payment.amountCents() == 0) {
                        continue;
                    }
                    String paymentMethod = normalizePaymentMethod(payment.method());
                    breakdown.merge(paymentMethod, payment.amountCents(), Integer::sum);
                }
            }
            breakdown.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);

            int paidFromBreakdown = breakdown.values().stream().mapToInt(Integer::intValue).sum();
            int paidCents = summary.paidCents() > 0 ? summary.paidCents() : paidFromBreakdown;
            if (paidCents <= 0) {
                paidCents = fallbackPaidAmountCents;
            }
            if (breakdown.isEmpty() && fallbackPaidAmountCents > 0) {
                breakdown.put(normalizePaymentMethod(fallbackMethod), fallbackPaidAmountCents);
            }

            return new PaidTicketSummary(totalCents, paidCents, breakdown);
        } catch (Exception ignored) {
            return new PaidTicketSummary(fallbackTotal, fallbackPaidAmountCents, fallbackBreakdown);
        }
    }

    private static String normalizePaymentMethod(String method) {
        if (method == null || method.isBlank()) {
            return "OTRO";
        }
        String m = method.trim().toUpperCase(Locale.ROOT);
        return switch (m) {
            case "CASH", "EFECTIVO" -> "EFECTIVO";
            case "CARD", "TARJETA" -> "TARJETA";
            case "BIZUM" -> "BIZUM";
            default -> m;
        };
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + ".";
    }

    private static void appendReceiptItemsHeader(StringBuilder out) {
        out.append(formatReceiptColumns("CANT", "DESCRIPCION", "PRECIO", "IMPORTE")).append('\n');
    }

    private static void appendReceiptLineWithAmounts(StringBuilder out, int qty, String productName, int unitPriceCents, int lineTotalCents) {
        String qtyText = Math.max(1, qty) + "x";
        String unitText = String.format(Locale.US, "%.2f", unitPriceCents / 100.0);
        String totalText = String.format(Locale.US, "%.2f", lineTotalCents / 100.0);
        String safeName = productName == null || productName.isBlank() ? "-" : productName.trim();

        java.util.List<String> wrapped = wrapByWords(safeName, RECEIPT_DESC_COL_WIDTH);
        String firstName = wrapped.isEmpty() ? "-" : wrapped.getFirst();

        out.append(formatReceiptColumns(qtyText, firstName, unitText, totalText)).append('\n');

        if (wrapped.size() <= 1) {
            return;
        }

        for (int i = 1; i < wrapped.size(); i++) {
            out.append(formatReceiptColumns("", wrapped.get(i), "", "")).append('\n');
        }
    }

    private static String formatReceiptColumns(String qty, String description, String unitPrice, String amount) {
        return padRight(trimToWidth(qty, RECEIPT_QTY_COL_WIDTH), RECEIPT_QTY_COL_WIDTH)
                + ' '
                + padRight(trimToWidth(description, RECEIPT_DESC_COL_WIDTH), RECEIPT_DESC_COL_WIDTH)
                + ' '
                + padLeft(trimToWidth(unitPrice, RECEIPT_UNIT_COL_WIDTH), RECEIPT_UNIT_COL_WIDTH)
                + ' '
                + padLeft(trimToWidth(amount, RECEIPT_TOTAL_COL_WIDTH), RECEIPT_TOTAL_COL_WIDTH);
    }

    private static void appendReceiptAmountLine(StringBuilder out, String label, int amountCents) {
        appendReceiptFieldLine(out, label, String.format(Locale.US, "%.2f", amountCents / 100.0));
    }

    private static void appendReceiptFieldLine(StringBuilder out, String label, String value) {
        String safeLabel = (label == null ? "" : label.trim()) + ":";
        String safeValue = value == null || value.isBlank() ? "-" : value.trim();
        int maxLeft = Math.max(4, RECEIPT_LINE_WIDTH - safeValue.length() - 1);
        String left = safeLabel.length() <= maxLeft ? safeLabel : safeLabel.substring(0, maxLeft);
        int gap = Math.max(1, RECEIPT_LINE_WIDTH - left.length() - safeValue.length());
        out.append(left).append(" ".repeat(gap)).append(safeValue).append('\n');
    }

    private static void appendWrappedLine(StringBuilder out, String text) {
        for (String part : wrapByWords(text, RECEIPT_LINE_WIDTH)) {
            out.append(part).append('\n');
        }
    }

    private static String receiptSeparator() {
        return "-".repeat(RECEIPT_LINE_WIDTH);
    }

    private static java.util.List<String> wrapByWords(String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }

        String[] words = text.trim().split("\\s+");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (word.length() > maxWidth) {
                if (!current.isEmpty()) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                int index = 0;
                while (index < word.length()) {
                    int end = Math.min(index + maxWidth, word.length());
                    lines.add(word.substring(index, end));
                    index = end;
                }
                continue;
            }

            if (current.isEmpty()) {
                current.append(word);
                continue;
            }

            if (current.length() + 1 + word.length() <= maxWidth) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String trimToWidth(String value, int width) {
        String safe = value == null ? "" : value.trim();
        if (safe.length() <= width) {
            return safe;
        }
        return safe.substring(0, width);
    }

    private static String padRight(String value, int width) {
        String safe = value == null ? "" : value;
        if (safe.length() >= width) {
            return safe;
        }
        return safe + " ".repeat(width - safe.length());
    }

    private static String padLeft(String value, int width) {
        String safe = value == null ? "" : value;
        if (safe.length() >= width) {
            return safe;
        }
        return " ".repeat(width - safe.length()) + safe;
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

    private String tableLabelForTicket() {
        String label = tableLabelOrFallback();
        return label == null || label.isBlank() ? "Mesa " + vm.tableIdProperty().get() : label;
    }

    private void printPaidTicketSafe(String method, int paidAmountCents) {
        try {
            String target = printDocumentToGeneral(buildPaidTicketText(method, paidAmountCents));
            setFeedback("Cobro registrado. Ticket enviado a " + target + ".");
        } catch (Exception e) {
            UiDialogs.warn("Ticket cliente", "Cobro registrado, pero no se pudo imprimir ticket:\n" + e.getMessage());
        }
    }

    private String printDocumentToGeneral(String text) {
        java.util.List<String> candidates = new java.util.ArrayList<>();
        addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("GENERAL"));
        addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("ALL"));
        addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("BAR"));
        addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("COCINA"));
        addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("POSTRES"));

        javafx.stage.Window owner = feedbackLabel != null && feedbackLabel.getScene() != null ? feedbackLabel.getScene().getWindow() : null;
        for (String printer : candidates) {
            try {
                PrintUtil.printTextToPrinterWithBottomMargin(printer, text, owner);
                return printer;
            } catch (Exception ignored) {
                // Probamos siguiente impresora configurada.
            }
        }

        PrintUtil.printTextToPdfWithBottomMargin(text, owner);
        return "Print to PDF";
    }

    private static void addPrinters(java.util.List<String> target, java.util.List<String> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (String printer : source) {
            if (printer == null || printer.isBlank()) {
                continue;
            }
            String normalized = printer.trim();
            if (!target.contains(normalized)) {
                target.add(normalized);
            }
        }
    }

    private void setFeedback(String message) {
        vm.feedbackProperty().set(message == null ? "" : message);
    }

    private void showInfoDialog(String title, String message) {
        UiDialogs.info(title, message);
    }

    private void showErrorDialog(String title, String message) {
        UiDialogs.error(title, message);
    }

    private record LineSelection(OrderLine line, CheckBox include, Spinner<Integer> qty) {
        int selectedCents() {
            Integer value = qty.getValue();
            int qtyValue = value == null ? 0 : value;
            return include.isSelected() ? qtyValue * line.getUnitPriceCents() : 0;
        }
    }

    private record PaidTicketSummary(int totalCents, int paidCents, Map<String, Integer> paymentBreakdown) {}

    private record PaymentChoice(String label, String apiCode) {
        @Override
        public String toString() {
            return label;
        }
    }
}





