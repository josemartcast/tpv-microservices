package com.tpv.desktop.ui.sales;

import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.CatalogApi;
import com.tpv.desktop.api.pos.CategoryResponse;
import com.tpv.desktop.api.pos.ComandaApi;
import com.tpv.desktop.api.pos.PaymentApi;
import com.tpv.desktop.api.pos.ProductResponse;
import com.tpv.desktop.api.pos.SendPreviewResponse;
import com.tpv.desktop.api.pos.TicketApi;
import com.tpv.desktop.api.pos.TicketLineResponse;
import com.tpv.desktop.api.pos.TicketResponse;
import com.tpv.desktop.core.MoneyUtil;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

public class SalesController {
    private static final String FEEDBACK_BASE = "sales-feedback";
    private static final String FEEDBACK_INFO = "sales-feedback-info";
    private static final String FEEDBACK_SUCCESS = "sales-feedback-success";
    private static final String FEEDBACK_WARN = "sales-feedback-warn";
    private static final String FEEDBACK_ERROR = "sales-feedback-error";

    private final AtomicBoolean paying = new AtomicBoolean(false);
    private final ObservableList<ProductResponse> allProducts = FXCollections.observableArrayList();
    private final EventHandler<KeyEvent> keyHandler = this::handleKeyPressed;
    private boolean keyboardShortcutsAttached = false;

    @FXML
    private BorderPane root;
    @FXML
    private ListView<CategoryResponse> categoriesList;
    @FXML
    private TextField productSearchField;
    @FXML
    private FlowPane productsPane;

    @FXML
    private TableView<TicketLineResponse> linesTable;
    @FXML
    private TableColumn<TicketLineResponse, String> colName;
    @FXML
    private TableColumn<TicketLineResponse, Integer> colQty;
    @FXML
    private TableColumn<TicketLineResponse, String> colUnit;
    @FXML
    private TableColumn<TicketLineResponse, String> colTotal;

    @FXML
    private Label ticketInfoLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label errorLabel;

    @FXML
    private Button newTicketBtn;
    @FXML
    private Button sendBtn;
    @FXML
    private Button payCashBtn;
    @FXML
    private Button payCardBtn;
    @FXML
    private Button payBizumBtn;
    @FXML
    private Button payCustomBtn;
    @FXML
    private Button cancelBtn;

    private TicketResponse currentTicket;
    private final ObservableList<TicketLineResponse> lines = FXCollections.observableArrayList();
    private Timeline lockHeartbeat;
    private Integer lockedTableNumber;
    private String lastPaymentAttemptSignature;
    private String lastPaymentAttemptKey;
    private String lastSendAttemptSignature;
    private String lastSendAttemptKey;

    @FXML
    public void initialize() {
        setupTable();
        linesTable.setItems(lines);
        attachKeyboardShortcutsLifecycle();
        loadCategories();
        categoriesList.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            Long catId = (newV == null) ? null : newV.id();
            loadProducts(catId);
        });
        productSearchField.textProperty().addListener((obs, oldV, newV) -> renderProducts());
        loadProducts(null);
        renderTicket(null);

        Long resumeId = com.tpv.desktop.core.AppState.getResumeTicketId();
        if (resumeId != null) {
            try {
                currentTicket = TicketApi.getById(resumeId);
                ensureTableLock(currentTicket);
                renderTicket(currentTicket);
            } catch (Exception e) {
                setFeedbackError("Could not load ticket: " + e.getMessage());
            } finally {
                com.tpv.desktop.core.AppState.clearResumeTicketId();
            }
        }
        checkCashSessionOrBlock();
    }

    private void setupTable() {
        colName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().productName()));
        colQty.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().qty()));
        colUnit.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().unitPriceCents())));
        colTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().lineTotalCents())));

        linesTable.setRowFactory(tv -> {
            TableRow<TicketLineResponse> row = new TableRow<>();
            ContextMenu menu = new ContextMenu();
            MenuItem del = new MenuItem("Delete line");
            del.setOnAction(e -> {
                TicketLineResponse line = row.getItem();
                    if (line != null) {
                    deleteLine(line.id());
                }
            });
            menu.getItems().add(del);
            row.contextMenuProperty().bind(
                    javafx.beans.binding.Bindings.when(row.emptyProperty()).then((ContextMenu) null).otherwise(menu)
            );
            row.itemProperty().addListener((obs, oldItem, newItem) -> {
                if (newItem == null) {
                    row.setStyle("");
                } else if (newItem.sent()) {
                    row.setStyle("-fx-background-color: #eef8ee;");
                } else {
                    row.setStyle("-fx-background-color: #fff8e1;");
                }
            });
            return row;
        });

        linesTable.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TicketLineResponse sel = linesTable.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    promptQtyAndUpdate(sel);
                }
            }
        });
    }

    private void loadCategories() {
        try {
            setFeedbackInfo("");
            CategoryResponse[] arr = CatalogApi.categories();
            ObservableList<CategoryResponse> items = FXCollections.observableArrayList(arr);
            categoriesList.setItems(items);
            categoriesList.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(CategoryResponse item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name());
                }
            });
        } catch (Exception e) {
            setFeedbackError("Could not load categories: " + e.getMessage());
        }
    }

    private void loadProducts(Long categoryId) {
        try {
            setFeedbackInfo("");
            ProductResponse[] arr = CatalogApi.products(categoryId);
            allProducts.setAll(Arrays.asList(arr));
            renderProducts();
        } catch (Exception e) {
            setFeedbackError("Could not load products: " + e.getMessage());
        }
    }

    @FXML
    public void onNewTicket() {
        setFeedbackInfo("");
        try {
            currentTicket = TicketApi.create();
            ensureTableLock(currentTicket);
            renderTicket(currentTicket);
        } catch (ApiException e) {
            setFeedbackError("Could not create ticket: " + e.getMessage());
        } catch (Exception e) {
            setFeedbackError("Unexpected error: " + e.getMessage());
        }
    }

    private void addProductToTicket(ProductResponse p) {
        setFeedbackInfo("");
        try {
            if (currentTicket == null || !"OPEN".equalsIgnoreCase(currentTicket.status())) {
                currentTicket = TicketApi.create();
                ensureTableLock(currentTicket);
            }

            TicketLineResponse existing = null;
            if (currentTicket.lines() != null) {
                for (TicketLineResponse l : currentTicket.lines()) {
                    if (l.productId() == p.id() && !l.sent()) {
                        existing = l;
                        break;
                    }
                }
            }

            if (existing != null) {
                currentTicket = TicketApi.updateQty(currentTicket.id(), existing.id(), existing.qty() + 1);
            } else {
                currentTicket = TicketApi.addLine(currentTicket.id(), p.id(), 1);
            }

            renderTicket(currentTicket);
        } catch (Exception e) {
            setFeedbackError("Could not add product: " + e.getMessage());
        }
    }

    private void promptQtyAndUpdate(TicketLineResponse line) {
        if (currentTicket == null) {
            return;
        }
        TextInputDialog d = new TextInputDialog(String.valueOf(line.qty()));
        d.setTitle("Change quantity");
        d.setHeaderText(line.productName());
        d.setContentText("New quantity:");
        d.showAndWait().ifPresent(txt -> {
            try {
                int qty = Integer.parseInt(txt.trim());
                if (qty <= 0) {
                    setFeedbackWarn("Quantity must be > 0");
                    return;
                }
                currentTicket = TicketApi.updateQty(currentTicket.id(), line.id(), qty);
                renderTicket(currentTicket);
            } catch (Exception e) {
                setFeedbackError("Could not update quantity: " + e.getMessage());
            }
        });
    }

    private void deleteLine(long lineId) {
        if (currentTicket == null) {
            return;
        }
        try {
            currentTicket = TicketApi.deleteLine(currentTicket.id(), lineId);
            renderTicket(currentTicket);
        } catch (Exception e) {
            setFeedbackError("Could not delete line: " + e.getMessage());
        }
    }

    @FXML
    public void onRefresh() {
        if (currentTicket == null) {
            renderTicket(null);
            return;
        }
        try {
            currentTicket = TicketApi.getById(currentTicket.id());
            renderTicket(currentTicket);
            setFeedbackInfo("");
        } catch (Exception e) {
            setFeedbackError("Could not refresh ticket: " + e.getMessage());
        }
    }

    @FXML
    public void onCancel() {
        if (currentTicket == null) {
            return;
        }
        try {
            Integer table = currentTicket.tableNumber();
            currentTicket = TicketApi.cancel(currentTicket.id());
            currentTicket = null;
            renderTicket(null);
            releaseTableLock(table);
            setFeedbackSuccess("Ticket cancelled.");
        } catch (Exception e) {
            setFeedbackError("Could not cancel: " + e.getMessage());
        }
    }

    @FXML
    public void onSend() {
        if (currentTicket == null) {
            setFeedbackWarn("No ticket.");
            return;
        }
        try {
            SendPreviewResponse preview = ComandaApi.preview(currentTicket.id());
            if (preview.pendingLines() == null || preview.pendingLines().isEmpty()) {
                setFeedbackWarn("No pending lines.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/sales/SendComandaModal.fxml"));
            Scene scene = new Scene(loader.load());
            SendComandaController modal = loader.getController();
            modal.init(preview.pendingLines());

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Send order");
            stage.setScene(scene);
            stage.showAndWait();

            SendComandaResult result = modal.getResult();
            if (result == SendComandaResult.NONE) {
                return;
            }
            String destination = switch (result) {
                case ALL -> "ALL";
                case BAR_ONLY -> "BAR";
                case COCINA_ONLY -> "COCINA";
                case NONE -> "ALL";
            };
            String pendingSignature = preview.pendingLines().stream()
                    .map(l -> Long.toString(l.id()))
                    .reduce((a, b) -> a + "," + b)
                    .orElse("-");
            String signature = currentTicket.id() + "|" + destination + "|" + pendingSignature;
            String key;
            if (signature.equals(lastSendAttemptSignature) && lastSendAttemptKey != null) {
                key = lastSendAttemptKey;
            } else {
                key = UUID.randomUUID().toString();
                lastSendAttemptSignature = signature;
                lastSendAttemptKey = key;
            }
            var sendRes = ComandaApi.send(currentTicket.id(), destination, key);
            currentTicket = TicketApi.getById(currentTicket.id());
            lastSendAttemptSignature = null;
            lastSendAttemptKey = null;
            renderTicket(currentTicket);
            setFeedbackSuccess("Sent " + sendRes.sentCount() + " lines to " + sendRes.destination());
        } catch (IOException e) {
            setFeedbackError("Could not open send modal: " + e.getMessage());
        } catch (Exception e) {
            setFeedbackError("Could not send order: " + e.getMessage());
        }
    }

    @FXML
    public void onPayCash() {
        payFull("CASH");
    }

    @FXML
    public void onPayCard() {
        payFull("CARD");
    }

    @FXML
    public void onPayBizum() {
        payFull("BIZUM");
    }

    @FXML
    public void onPayCustom() {
        if (currentTicket == null) {
            setFeedbackWarn("No ticket.");
            return;
        }
        if (!"OPEN".equalsIgnoreCase(currentTicket.status())) {
            setFeedbackWarn("Ticket is not open.");
            return;
        }

        List<PaymentChoice> choices = List.of(
                new PaymentChoice("EFECTIVO", "CASH"),
                new PaymentChoice("TARJETA", "CARD"),
                new PaymentChoice("BIZUM", "BIZUM")
        );
        ChoiceDialog<PaymentChoice> methodDialog = new ChoiceDialog<>(choices.get(1), choices);
        methodDialog.setTitle("Custom payment");
        methodDialog.setHeaderText("Select payment method");
        methodDialog.setContentText("Method:");
        var methodOpt = methodDialog.showAndWait().map(PaymentChoice::apiCode);
        if (methodOpt.isEmpty()) {
            return;
        }
        String method = methodOpt.get();

        TextInputDialog amountDialog = new TextInputDialog(MoneyUtil.centsToEuros(currentTicket.totalCents()));
        amountDialog.setTitle("Custom payment");
        amountDialog.setHeaderText("Enter amount in EUR");
        amountDialog.setContentText("Amount:");
        var amountOpt = amountDialog.showAndWait();
        if (amountOpt.isEmpty()) {
            return;
        }

        try {
            int amountCents = MoneyUtil.eurosToCents(amountOpt.get());
            if (amountCents <= 0) {
                setFeedbackWarn("Amount must be > 0.");
                return;
            }
            payCustom(method, amountCents);
        } catch (Exception e) {
            setFeedbackError("Invalid amount: " + e.getMessage());
        }
    }

    private void payCustom(String method, int amountCents) {
        if (!paying.compareAndSet(false, true)) {
            return;
        }
        try {
            String signature = currentTicket.id() + "|" + method + "|" + amountCents;
            String key;
            if (signature.equals(lastPaymentAttemptSignature) && lastPaymentAttemptKey != null) {
                key = lastPaymentAttemptKey;
            } else {
                key = UUID.randomUUID().toString();
                lastPaymentAttemptSignature = signature;
                lastPaymentAttemptKey = key;
            }

            setPayButtonsEnabled(false);
            PaymentApi.addPayment(currentTicket.id(), method, amountCents, key);
            TicketResponse updatedTicket = TicketApi.getById(currentTicket.id());
            lastPaymentAttemptSignature = null;
            lastPaymentAttemptKey = null;

            if ("PAID".equalsIgnoreCase(updatedTicket.status())) {
                Integer table = updatedTicket.tableNumber();
                currentTicket = null;
                renderTicket(null);
                releaseTableLock(table);
                setFeedbackSuccess("Custom payment registered. Ticket paid.");
            } else {
                currentTicket = updatedTicket;
                renderTicket(updatedTicket);
                setFeedbackSuccess("Custom payment registered.");
            }
        } catch (Exception e) {
            setFeedbackError("Could not process custom payment: " + e.getMessage());
            setPayButtonsEnabled(true);
        } finally {
            paying.set(false);
        }
    }

    private void payFull(String method) {
        if (currentTicket == null) {
            setFeedbackWarn("No ticket.");
            return;
        }
        if (!paying.compareAndSet(false, true)) {
            return;
        }
        try {
            int amount = currentTicket.totalCents();
            if (amount <= 0) {
                setFeedbackWarn("Ticket is empty.");
                return;
            }
            String signature = currentTicket.id() + "|" + method + "|" + amount;
            String key;
            if (signature.equals(lastPaymentAttemptSignature) && lastPaymentAttemptKey != null) {
                key = lastPaymentAttemptKey;
            } else {
                key = UUID.randomUUID().toString();
                lastPaymentAttemptSignature = signature;
                lastPaymentAttemptKey = key;
            }
            setPayButtonsEnabled(false);
            PaymentApi.addPayment(currentTicket.id(), method, amount, key);
            TicketResponse updatedTicket = TicketApi.getById(currentTicket.id());
            lastPaymentAttemptSignature = null;
            lastPaymentAttemptKey = null;
            if ("PAID".equalsIgnoreCase(updatedTicket.status())) {
                Integer table = updatedTicket.tableNumber();
                currentTicket = null;
                renderTicket(null);
                releaseTableLock(table);
                setFeedbackSuccess("Payment registered. Ticket paid.");
            } else {
                currentTicket = updatedTicket;
                renderTicket(updatedTicket);
                setFeedbackSuccess("Payment registered.");
            }
        } catch (Exception e) {
            setFeedbackError("Could not process payment: " + e.getMessage());
            setPayButtonsEnabled(true);
        } finally {
            paying.set(false);
        }
    }

    private void renderTicket(TicketResponse t) {
        if (t == null) {
            ticketInfoLabel.setText("No ticket (create one or tap a product)");
            lines.clear();
            linesTable.refresh();
            totalLabel.setText("0.00");
            setPayButtonsEnabled(false);
            sendBtn.setDisable(true);
            cancelBtn.setDisable(true);
            return;
        }

        int pending = t.lines() == null ? 0 : (int) t.lines().stream().filter(l -> !l.sent()).count();
        String tableInfo = t.tableNumber() == null ? "" : " | Mesa " + t.tableNumber();
        ticketInfoLabel.setText("Ticket #" + t.id() + tableInfo + " - " + t.status() + " - Pending: " + pending);
        lines.setAll(t.lines() == null ? FXCollections.observableArrayList() : t.lines());
        totalLabel.setText(MoneyUtil.centsToEuros(t.totalCents()));

        boolean isOpen = "OPEN".equalsIgnoreCase(t.status());
        boolean canPay = isOpen && t.totalCents() > 0;
        setPayButtonsEnabled(canPay);
        sendBtn.setDisable(!isOpen || pending == 0);
        cancelBtn.setDisable(!isOpen);
        linesTable.refresh();
    }

    private void setPayButtonsEnabled(boolean enabled) {
        payCashBtn.setDisable(!enabled);
        payCardBtn.setDisable(!enabled);
        payBizumBtn.setDisable(!enabled);
        if (payCustomBtn != null) {
            payCustomBtn.setDisable(!enabled);
        }
    }

    private void checkCashSessionOrBlock() {
        try {
            CashApi.current();
            setSalesEnabled(true);
        } catch (Exception e) {
            setSalesEnabled(false);
            setFeedbackWarn("You must open cash before selling.");
        }
    }

    private void setSalesEnabled(boolean enabled) {
        productsPane.setDisable(!enabled);
        if (newTicketBtn != null) {
            newTicketBtn.setDisable(!enabled);
        }
        sendBtn.setDisable(!enabled || currentTicket == null);
        setPayButtonsEnabled(enabled);
        cancelBtn.setDisable(!enabled);
    }

    private void ensureTableLock(TicketResponse ticket) throws Exception {
        if (ticket == null || ticket.tableNumber() == null) {
            stopHeartbeat();
            lockedTableNumber = null;
            return;
        }
        SalonApi.lockTable(ticket.tableNumber());
        lockedTableNumber = ticket.tableNumber();
        startHeartbeat();
    }

    private void startHeartbeat() {
        stopHeartbeat();
        if (lockedTableNumber == null) {
            return;
        }
        lockHeartbeat = new Timeline(new KeyFrame(Duration.seconds(20), e -> {
            try {
                if (lockedTableNumber != null) {
                    SalonApi.heartbeatTable(lockedTableNumber);
                }
            } catch (Exception ex) {
                setFeedbackWarn("Lock heartbeat failed: " + ex.getMessage());
            }
        }));
        lockHeartbeat.setCycleCount(Timeline.INDEFINITE);
        lockHeartbeat.play();
    }

    private void stopHeartbeat() {
        if (lockHeartbeat != null) {
            lockHeartbeat.stop();
            lockHeartbeat = null;
        }
    }

    private void releaseTableLock(Integer tableNumber) {
        stopHeartbeat();
        if (tableNumber == null) {
            return;
        }
        try {
            SalonApi.unlockTable(tableNumber);
        } catch (Exception e) {
            setFeedbackWarn("Could not release lock: " + e.getMessage());
        } finally {
            if (lockedTableNumber != null && lockedTableNumber.equals(tableNumber)) {
                lockedTableNumber = null;
            }
        }
    }

    private void renderProducts() {
        String q = productSearchField == null || productSearchField.getText() == null
                ? ""
                : productSearchField.getText().trim().toLowerCase();

        productsPane.getChildren().clear();
        allProducts.stream()
                .filter(p -> q.isBlank() || p.name().toLowerCase().contains(q))
                .forEach(p -> {
                    Button b = new Button(p.name() + "\n" + MoneyUtil.centsToEuros(p.priceCents()) + " EUR");
                    b.getStyleClass().add("touch-btn");
                    b.setPrefWidth(210);
                    b.setPrefHeight(90);
                    b.setWrapText(true);
                    b.setOnAction(ev -> addProductToTicket(p));
                    productsPane.getChildren().add(b);
                });
    }

    private void attachKeyboardShortcutsLifecycle() {
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != null && keyboardShortcutsAttached) {
                oldScene.removeEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                keyboardShortcutsAttached = false;
            }
            if (newScene != null) {
                newScene.addEventFilter(KeyEvent.KEY_PRESSED, keyHandler);
                keyboardShortcutsAttached = true;
            }
        });
    }

    private void handleKeyPressed(KeyEvent e) {
        if (e.getCode() == KeyCode.F2) {
            onNewTicket();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F5) {
            onRefresh();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F6) {
            onPayCustom();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F9) {
            onSend();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F10) {
            onPayCash();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F11) {
            onPayCard();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.F12) {
            onPayBizum();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.ESCAPE) {
            onCancel();
            e.consume();
            return;
        }
        if (e.getCode() == KeyCode.DELETE) {
            TicketLineResponse selected = linesTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                deleteLine(selected.id());
                e.consume();
            }
        }
    }

    private void setFeedbackInfo(String text) {
        applyFeedback(text, FEEDBACK_INFO);
    }

    private void setFeedbackSuccess(String text) {
        applyFeedback(text, FEEDBACK_SUCCESS);
    }

    private void setFeedbackWarn(String text) {
        applyFeedback(text, FEEDBACK_WARN);
    }

    private void setFeedbackError(String text) {
        applyFeedback(text, FEEDBACK_ERROR);
    }

    private void applyFeedback(String text, String toneClass) {
        errorLabel.setText(text == null ? "" : text);
        errorLabel.getStyleClass().removeAll(FEEDBACK_INFO, FEEDBACK_SUCCESS, FEEDBACK_WARN, FEEDBACK_ERROR);
        if (!errorLabel.getStyleClass().contains(FEEDBACK_BASE)) {
            errorLabel.getStyleClass().add(FEEDBACK_BASE);
        }
        errorLabel.getStyleClass().add(toneClass);
    }

    private record PaymentChoice(String label, String apiCode) {
        @Override
        public String toString() {
            return label;
        }
    }
}
