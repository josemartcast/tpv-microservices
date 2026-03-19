package com.tpv.desktop.ui.history;

import com.tpv.desktop.api.pos.CustomerApi;
import com.tpv.desktop.api.pos.CustomerResponse;
import com.tpv.desktop.api.pos.InvoiceApi;
import com.tpv.desktop.api.pos.InvoiceResponse;
import com.tpv.desktop.api.pos.InvoiceSummaryResponse;
import com.tpv.desktop.api.pos.PaymentApi;
import com.tpv.desktop.api.pos.TicketApi;
import com.tpv.desktop.api.pos.TicketHistoryApi;
import com.tpv.desktop.api.pos.TicketResponse;
import com.tpv.desktop.api.pos.TicketSummaryResponse;
import com.tpv.desktop.core.AuthStore;
import com.tpv.desktop.core.MoneyUtil;
import com.tpv.desktop.core.PrinterSettingsStore;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.ui.util.PrintUtil;
import com.tpv.desktop.ui.UiDialogs;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.DatePicker;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

public class HistoryController {

    @FXML private TableView<TicketResponse> ticketsTable;
    @FXML private TableColumn<TicketResponse, String> colId;
    @FXML private TableColumn<TicketResponse, String> colTable;
    @FXML private TableColumn<TicketResponse, String> colStatus;
    @FXML private TableColumn<TicketResponse, String> colTotal;
    @FXML private TableColumn<TicketResponse, String> colCreated;
    @FXML private Label listErrorLabel;
    @FXML private TextField ticketIdSearchField;

    @FXML private Label detailTitle;
    @FXML private Label detailTotal;
    @FXML private Label detailPaid;
    @FXML private Label detailRemaining;
    @FXML private Label detailErrorLabel;

    @FXML private TableView<TicketSummaryResponse.TicketLineSummary> linesTable;
    @FXML private TableColumn<TicketSummaryResponse.TicketLineSummary, String> colLineName;
    @FXML private TableColumn<TicketSummaryResponse.TicketLineSummary, Integer> colLineQty;
    @FXML private TableColumn<TicketSummaryResponse.TicketLineSummary, String> colLineUnit;
    @FXML private TableColumn<TicketSummaryResponse.TicketLineSummary, String> colLineTotal;

    @FXML private TableView<TicketSummaryResponse.PaymentSummary> paymentsTable;
    @FXML private TableColumn<TicketSummaryResponse.PaymentSummary, String> colPayMethod;
    @FXML private TableColumn<TicketSummaryResponse.PaymentSummary, String> colPayAmount;
    @FXML private TableColumn<TicketSummaryResponse.PaymentSummary, String> colPayDate;
    @FXML private Button openInSalesBtn;
    @FXML private Button refundBtn;
    @FXML private Button reopenPaidBtn;
    @FXML private Button invoiceBtn;

    private final ObservableList<TicketResponse> tickets = FXCollections.observableArrayList();
    private final ObservableList<TicketSummaryResponse.TicketLineSummary> lines = FXCollections.observableArrayList();
    private final ObservableList<TicketSummaryResponse.PaymentSummary> payments = FXCollections.observableArrayList();
    private TicketResponse selectedTicket;
    private TicketSummaryResponse.PaymentSummary selectedPayment;

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final int INVOICE_LINE_WIDTH = 34;
    private static final int INVOICE_QTY_COL_WIDTH = 4;
    private static final int INVOICE_DESC_COL_WIDTH = 13;
    private static final int INVOICE_UNIT_COL_WIDTH = 6;
    private static final int INVOICE_TOTAL_COL_WIDTH = 8;

    private boolean canReopenPaid() {
        return AuthStore.hasRole("ADMIN") || AuthStore.hasRole("ENCARGADO");
    }

    private boolean canInvoice() {
        return AuthStore.hasRole("ADMIN") || AuthStore.hasRole("ENCARGADO") || AuthStore.hasRole("CAJERO");
    }

    @FXML
    public void initialize() {
        openInSalesBtn.setDisable(true);
        refundBtn.setDisable(true);
        reopenPaidBtn.setDisable(!canReopenPaid());
        invoiceBtn.setDisable(!canInvoice());
        setupTables();
        ticketsTable.setItems(tickets);
        linesTable.setItems(lines);
        paymentsTable.setItems(payments);

        ticketsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedTicket = newV;
            selectedPayment = null;
            openInSalesBtn.setDisable(true);
            refundBtn.setDisable(true);
            reopenPaidBtn.setDisable(!canReopenPaid());
            invoiceBtn.setDisable(!canInvoice());
            if (newV != null) {
                loadDetail(newV.id());
            }
        });

        paymentsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedPayment = newV;
            refundBtn.setDisable(selectedTicket == null || newV == null || newV.amountCents() <= 0);
        });

        onRefresh();
    }

    private void setupTables() {
        colId.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().id())));
        colTable.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().tableNumber() == null ? "-" : String.valueOf(c.getValue().tableNumber())
        ));
        colStatus.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().status()));
        colTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().totalCents()) + " EUR"));
        colCreated.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().createdAt() == null ? "-" : DT.format(c.getValue().createdAt())
        ));

        colLineName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().productName()));
        colLineQty.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().qty()));
        colLineUnit.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().unitPriceCents())));
        colLineTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().lineTotalCents())));

        colPayMethod.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().method()));
        colPayAmount.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().amountCents()) + " EUR"));
        colPayDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().createdAt() == null ? "-" : DT.format(c.getValue().createdAt())
        ));
    }

    @FXML
    public void onRefresh() {
        listErrorLabel.setText("");
        detailErrorLabel.setText("");
        clearDetail();

        try {
            TicketResponse[] arr = TicketHistoryApi.listCurrentCashHistory();
            tickets.setAll(arr == null ? FXCollections.observableArrayList() : Arrays.asList(arr));
        } catch (Exception e) {
            listErrorLabel.setText("No se pudo cargar la lista: " + e.getMessage());
        }
    }

    @FXML
    public void onLoadTicketById() {
        detailErrorLabel.setText("");
        if (ticketIdSearchField == null || ticketIdSearchField.getText() == null || ticketIdSearchField.getText().isBlank()) {
            detailErrorLabel.setText("Introduce un ID de ticket.");
            return;
        }
        try {
            long ticketId = Long.parseLong(ticketIdSearchField.getText().trim());
            TicketResponse t = TicketApi.getById(ticketId);
            selectedTicket = t;
            loadDetail(ticketId);
        } catch (NumberFormatException e) {
            detailErrorLabel.setText("ID de ticket no valido.");
        } catch (Exception e) {
            detailErrorLabel.setText("No se pudo cargar ticket: " + e.getMessage());
        }
    }

    @FXML
    public void onInvoicesHistory() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Facturas emitidas");
        dialog.setHeaderText("Historial de facturas");
        ButtonType refreshBtn = new ButtonType("Buscar", ButtonBar.ButtonData.LEFT);
        ButtonType reprintBtn = new ButtonType("Reimprimir seleccionada", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(refreshBtn, reprintBtn, ButtonType.CLOSE);

        TextField numberField = new TextField();
        numberField.setPromptText("Numero factura");
        TextField customerField = new TextField();
        customerField.setPromptText("Cliente / NIF");
        DatePicker fromDate = new DatePicker(LocalDate.now().minusDays(30));
        DatePicker toDate = new DatePicker(LocalDate.now());

        TableView<InvoiceSummaryResponse> invoiceTable = new TableView<>();
        invoiceTable.setPrefHeight(460);

        TableColumn<InvoiceSummaryResponse, String> colInvNumber = new TableColumn<>("Factura");
        colInvNumber.setPrefWidth(160);
        colInvNumber.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(blankTo(c.getValue().invoiceNumber(), "-")));

        TableColumn<InvoiceSummaryResponse, String> colInvDate = new TableColumn<>("Fecha");
        colInvDate.setPrefWidth(160);
        colInvDate.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().issuedAt() == null ? "-" : DT.format(c.getValue().issuedAt())
        ));

        TableColumn<InvoiceSummaryResponse, String> colInvTicket = new TableColumn<>("Ticket");
        colInvTicket.setPrefWidth(90);
        colInvTicket.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().ticketId())));

        TableColumn<InvoiceSummaryResponse, String> colInvTable = new TableColumn<>("Mesa");
        colInvTable.setPrefWidth(90);
        colInvTable.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().tableNumber() == null ? "-" : String.valueOf(c.getValue().tableNumber())
        ));

        TableColumn<InvoiceSummaryResponse, String> colInvCustomer = new TableColumn<>("Cliente");
        colInvCustomer.setPrefWidth(280);
        colInvCustomer.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(blankTo(c.getValue().customerDisplayName(), "-")));

        TableColumn<InvoiceSummaryResponse, String> colInvTotal = new TableColumn<>("Total");
        colInvTotal.setPrefWidth(120);
        colInvTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                MoneyUtil.centsToEuros(c.getValue().totalGrossCents()) + " EUR"
        ));

        invoiceTable.getColumns().clear();
        invoiceTable.getColumns().add(colInvNumber);
        invoiceTable.getColumns().add(colInvDate);
        invoiceTable.getColumns().add(colInvTicket);
        invoiceTable.getColumns().add(colInvTable);
        invoiceTable.getColumns().add(colInvCustomer);
        invoiceTable.getColumns().add(colInvTotal);

        Label invoiceErrorLabel = new Label();
        invoiceErrorLabel.setStyle("-fx-text-fill: #b00020;");

        Runnable load = () -> {
            invoiceErrorLabel.setText("");
            try {
                InvoiceSummaryResponse[] data = InvoiceApi.list(
                        numberField.getText(),
                        customerField.getText(),
                        fromDate.getValue(),
                        toDate.getValue(),
                        500
                );
                invoiceTable.getItems().setAll(data == null ? FXCollections.observableArrayList() : Arrays.asList(data));
                if (!invoiceTable.getItems().isEmpty()) {
                    invoiceTable.getSelectionModel().selectFirst();
                }
            } catch (Exception e) {
                invoiceErrorLabel.setText("No se pudo cargar facturas: " + e.getMessage());
            }
        };

        HBox filters = new HBox(
                8,
                new Label("Numero"), numberField,
                new Label("Cliente"), customerField,
                new Label("Desde"), fromDate,
                new Label("Hasta"), toDate
        );
        HBox.setHgrow(numberField, Priority.ALWAYS);
        HBox.setHgrow(customerField, Priority.ALWAYS);

        VBox content = new VBox(10, filters, invoiceTable, invoiceErrorLabel);
        dialog.getDialogPane().setContent(content);

        load.run();

        while (true) {
            ButtonType action = dialog.showAndWait().orElse(ButtonType.CLOSE);
            if (action == null || action == ButtonType.CLOSE || action.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE) {
                break;
            }
            if (action == refreshBtn) {
                load.run();
                continue;
            }
            if (action == reprintBtn) {
                InvoiceSummaryResponse selectedInvoice = invoiceTable.getSelectionModel().getSelectedItem();
                if (selectedInvoice == null) {
                    invoiceErrorLabel.setStyle("-fx-text-fill: #b00020;");
                    invoiceErrorLabel.setText("Selecciona una factura para reimprimir.");
                    continue;
                }
                try {
                    InvoiceResponse invoice = TicketApi.getInvoice(selectedInvoice.ticketId());
                    String target = printInvoiceWithPrinterSelection(
                            buildInvoiceText(invoice),
                            dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow()
                    );
                    if (target == null) {
                        continue;
                    }
                    invoiceErrorLabel.setStyle("-fx-text-fill: #1e7e34;");
                    invoiceErrorLabel.setText("Factura " + invoice.invoiceNumber() + " enviada a " + target + ".");
                    break;
                } catch (Exception e) {
                    invoiceErrorLabel.setStyle("-fx-text-fill: #b00020;");
                    invoiceErrorLabel.setText("No se pudo reimprimir factura: " + e.getMessage());
                }
                continue;
            }
            break;
        }
    }

    private void loadDetail(long ticketId) {
        detailErrorLabel.setText("");
        try {
            TicketSummaryResponse s = TicketHistoryApi.summary(ticketId);

            detailTitle.setText("Ticket #" + s.id() + " - " + s.status());
            detailTotal.setText(MoneyUtil.centsToEuros(s.totalCents()) + " EUR");
            detailPaid.setText(MoneyUtil.centsToEuros(s.paidCents()) + " EUR");
            detailRemaining.setText(MoneyUtil.centsToEuros(s.remainingCents()) + " EUR");

            lines.setAll(s.lines() == null ? FXCollections.observableArrayList() : s.lines());
            payments.setAll(s.payments() == null ? FXCollections.observableArrayList() : s.payments());
            selectedPayment = null;
            refundBtn.setDisable(true);
            boolean isOpen = "OPEN".equalsIgnoreCase(s.status());
            boolean isPaid = "PAID".equalsIgnoreCase(s.status());
            openInSalesBtn.setDisable(!isOpen);
            reopenPaidBtn.setDisable(!isPaid || !canReopenPaid());
            invoiceBtn.setDisable(!isPaid || !canInvoice());
        } catch (Exception e) {
            detailErrorLabel.setText("No se pudo cargar el detalle: " + e.getMessage());
            clearDetail();
        }
    }

    private void clearDetail() {
        detailTitle.setText("Selecciona un ticket");
        detailTotal.setText("-");
        detailPaid.setText("-");
        detailRemaining.setText("-");
        selectedTicket = null;
        lines.clear();
        payments.clear();
        selectedPayment = null;
        openInSalesBtn.setDisable(true);
        reopenPaidBtn.setDisable(!canReopenPaid());
        invoiceBtn.setDisable(!canInvoice());
        refundBtn.setDisable(true);
    }

    @FXML
    public void onInvoice() {
        if (selectedTicket == null) {
            detailErrorLabel.setText("Selecciona un ticket.");
            return;
        }
        if (!"PAID".equalsIgnoreCase(selectedTicket.status())) {
            detailErrorLabel.setText("Solo se puede facturar un ticket en estado PAID.");
            return;
        }
        if (!canInvoice()) {
            detailErrorLabel.setText("No tienes permisos para facturar.");
            return;
        }
        try {
            TicketSummaryResponse summary = TicketHistoryApi.summary(selectedTicket.id());
            CustomerResponse[] allCustomers = CustomerApi.list();
            if (allCustomers == null || allCustomers.length == 0) {
                detailErrorLabel.setText("No hay clientes fiscales. Crea uno en el modulo Clientes.");
                return;
            }

            ComboBox<CustomerResponse> customerBox = new ComboBox<>(FXCollections.observableArrayList(Arrays.asList(allCustomers)));
            customerBox.setConverter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(CustomerResponse c) {
                    if (c == null) {
                        return "";
                    }
                    String tax = c.taxId() == null || c.taxId().isBlank() ? "-" : c.taxId();
                    return c.displayName() + " (" + tax + ")";
                }

                @Override
                public CustomerResponse fromString(String string) {
                    return null;
                }
            });
            customerBox.getSelectionModel().selectFirst();

            TextArea preview = new TextArea();
            preview.setEditable(false);
            preview.setWrapText(false);
            preview.setPrefColumnCount(60);
            preview.setPrefRowCount(28);
            preview.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 12px;");

            Runnable render = () -> preview.setText(buildInvoiceText(summary, customerBox.getValue()));
            customerBox.valueProperty().addListener((obs, oldV, newV) -> render.run());
            render.run();

            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Factura");
            dialog.setHeaderText("Factura de ticket #" + summary.id());
            ButtonType copyBtn = new ButtonType("Copiar", ButtonBar.ButtonData.LEFT);
            ButtonType printBtn = new ButtonType("Imprimir", ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().addAll(copyBtn, printBtn, ButtonType.CLOSE);

            HBox customerRow = new HBox(8, new Label("Cliente fiscal"), customerBox, new Region());
            HBox.setHgrow(customerBox, Priority.ALWAYS);
            VBox content = new VBox(10, customerRow, preview);
            dialog.getDialogPane().setContent(content);

            ButtonType action = dialog.showAndWait().orElse(ButtonType.CLOSE);
            if (action == copyBtn) {
                CustomerResponse selectedCustomer = customerBox.getValue();
                if (selectedCustomer == null) {
                    detailErrorLabel.setText("Selecciona un cliente fiscal.");
                    return;
                }
                InvoiceResponse invoice = TicketApi.issueInvoice(selectedTicket.id(), selectedCustomer.id());
                String finalText = buildInvoiceText(invoice);
                ClipboardContent clip = new ClipboardContent();
                clip.putString(finalText);
                Clipboard.getSystemClipboard().setContent(clip);
                detailErrorLabel.setText("Factura " + invoice.invoiceNumber() + " guardada y copiada.");
                return;
            }
            if (action == printBtn) {
                CustomerResponse selectedCustomer = customerBox.getValue();
                if (selectedCustomer == null) {
                    detailErrorLabel.setText("Selecciona un cliente fiscal.");
                    return;
                }
                InvoiceResponse invoice = TicketApi.issueInvoice(selectedTicket.id(), selectedCustomer.id());
                String finalText = buildInvoiceText(invoice);
                String target = printInvoiceWithPrinterSelection(
                        finalText,
                        detailErrorLabel != null && detailErrorLabel.getScene() != null ? detailErrorLabel.getScene().getWindow() : null
                );
                if (target == null) {
                    return;
                }
                detailErrorLabel.setText("Factura " + invoice.invoiceNumber() + " guardada y enviada a " + target + ".");
            }
        } catch (Exception e) {
            detailErrorLabel.setText("No se pudo generar factura: " + e.getMessage());
        }
    }

    private String printInvoiceWithPrinterSelection(String text, javafx.stage.Window owner) {
        List<String> choices = buildInvoicePrinterChoices();
        String defaultChoice = resolveDefaultInvoiceChoice(choices);
        ChoiceDialog<String> printerDialog = new ChoiceDialog<>(defaultChoice, choices);
        printerDialog.setTitle("Imprimir factura");
        printerDialog.setHeaderText("Selecciona impresora para factura");
        printerDialog.setContentText("Impresora:");
        String selected = printerDialog.showAndWait().orElse(null);
        if (selected == null || selected.isBlank()) {
            return null;
        }
        if ("Print to PDF".equals(selected)) {
            PrintUtil.printTextToPdfWithBottomMargin(text, owner);
            return "Print to PDF";
        }
        PrintUtil.printTextToPrinterWithBottomMargin(selected, text, owner);
        return selected;
    }

    private static String resolveDefaultInvoiceChoice(List<String> choices) {
        for (String printer : PrinterSettingsStore.resolveSystemPrintersForDestination("GENERAL")) {
            if (choices.contains(printer)) {
                return printer;
            }
        }
        return choices.isEmpty() ? "Print to PDF" : choices.getFirst();
    }

    private static List<String> buildInvoicePrinterChoices() {
        List<String> out = new ArrayList<>();
        for (String printer : PrinterSettingsStore.resolveSystemPrintersForDestination("GENERAL")) {
            if (printer == null || printer.isBlank()) {
                continue;
            }
            if (!out.contains(printer)) {
                out.add(printer);
            }
        }
        for (String printer : PrintUtil.availablePrinterNames()) {
            if (printer == null || printer.isBlank()) {
                continue;
            }
            if (!out.contains(printer)) {
                out.add(printer);
            }
        }
        out.add("Print to PDF");
        return out;
    }

    @FXML
    public void onOpenInSales() {
        if (selectedTicket == null) {
            return;
        }
        if (!"OPEN".equalsIgnoreCase(selectedTicket.status())) {
            detailErrorLabel.setText("Solo puedes abrir en Sales tickets en estado OPEN.");
            return;
        }
        com.tpv.desktop.core.AppState.setResumeTicketId(selectedTicket.id());
        com.tpv.desktop.core.Nav.goToSales();
    }

    @FXML
    public void onReopenPaid() {
        if (selectedTicket == null) {
            detailErrorLabel.setText("Selecciona un ticket.");
            return;
        }
        if (!"PAID".equalsIgnoreCase(selectedTicket.status())) {
            detailErrorLabel.setText("Solo se puede reabrir un ticket PAID.");
            return;
        }
        if (!canReopenPaid()) {
            detailErrorLabel.setText("Solo ADMIN/ENCARGADO puede modificar tickets pagados.");
            return;
        }

        TextInputDialog reasonDialog = new TextInputDialog("Correccion de ticket pagado");
        reasonDialog.setTitle("Reabrir ticket pagado");
        reasonDialog.setHeaderText("Ticket #" + selectedTicket.id());
        reasonDialog.setContentText("Motivo (min 6 caracteres):");
        String reason = reasonDialog.showAndWait().orElse("");
        if (reason == null || reason.isBlank()) {
            return;
        }
        if (reason.trim().length() < 6) {
            detailErrorLabel.setText("El motivo debe tener al menos 6 caracteres.");
            return;
        }

        boolean ok = UiDialogs.confirm(
                "Confirmar reapertura",
                "Se reabrira el ticket #" + selectedTicket.id()
                        + " y se revertiran sus pagos netos.\nDeseas continuar?"
        );
        if (!ok) {
            return;
        }

        try {
            TicketResponse reopened = TicketApi.reopenPaid(selectedTicket.id(), reason.trim());
            selectedTicket = reopened;
            detailErrorLabel.setText("Ticket reabierto correctamente.");
            loadDetail(reopened.id());
            onRefresh();
        } catch (Exception e) {
            detailErrorLabel.setText("No se pudo reabrir ticket: " + e.getMessage());
        }
    }

    @FXML
    public void onRefundSelectedPayment() {
        if (selectedTicket == null || selectedPayment == null) {
            return;
        }
        if (selectedPayment.amountCents() <= 0) {
            detailErrorLabel.setText("Solo se puede devolver un pago positivo.");
            return;
        }

        TextInputDialog amountDialog = new TextInputDialog(MoneyUtil.centsToEuros(selectedPayment.amountCents()));
        amountDialog.setTitle("Registrar devolucion");
        amountDialog.setHeaderText("Pago: " + selectedPayment.method() + " - " + MoneyUtil.centsToEuros(selectedPayment.amountCents()) + " EUR");
        amountDialog.setContentText("Importe devolucion (EUR):");
        String amountText = amountDialog.showAndWait().orElse("");
        if (amountText == null || amountText.isBlank()) {
            return;
        }

        try {
            int amountCents = MoneyUtil.eurosToCents(amountText);
            if (amountCents <= 0) {
                detailErrorLabel.setText("El importe de devolucion debe ser > 0.");
                return;
            }
            if (amountCents > selectedPayment.amountCents()) {
                detailErrorLabel.setText("No puedes devolver mas que el pago seleccionado.");
                return;
            }

            PaymentApi.addRefund(
                    selectedTicket.id(),
                    selectedPayment.method(),
                    amountCents,
                    UUID.randomUUID().toString()
            );
            detailErrorLabel.setText("Devolucion registrada.");
            loadDetail(selectedTicket.id());
            onRefresh();
        } catch (Exception e) {
            detailErrorLabel.setText("No se pudo registrar devolucion: " + e.getMessage());
        }
    }

    private String buildInvoiceText(TicketSummaryResponse summary, CustomerResponse customer) {
        StringBuilder out = new StringBuilder();

        String businessName = blankTo(SettingsStore.getFiscalLegalName(),
                blankTo(AppContext.get().appState().restaurantNameProperty().get(), "NEGOCIO"));
        String taxId = blankTo(SettingsStore.getFiscalTaxId(), "-");
        String address = SettingsStore.getFiscalAddress();
        String cp = SettingsStore.getFiscalPostalCode();
        String city = SettingsStore.getFiscalCity();
        String province = SettingsStore.getFiscalProvince();
        String country = SettingsStore.getFiscalCountry();

        String invoiceNumber = "F-" + summary.id() + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));

        out.append(clip(businessName.toUpperCase(Locale.ROOT), INVOICE_LINE_WIDTH)).append('\n');
        out.append("NIF/CIF ").append(taxId).append('\n');
        if (!blankTo(address, "").isBlank()) {
            appendWrappedInvoiceText(out, address);
        }
        String cityLine = String.format(
                Locale.ROOT,
                "%s %s %s",
                blankTo(cp, ""),
                blankTo(city, ""),
                blankTo(province, "")
        ).trim();
        if (!cityLine.isBlank()) {
            appendWrappedInvoiceText(out, cityLine);
        }
        if (!blankTo(country, "").isBlank()) {
            appendWrappedInvoiceText(out, country.toUpperCase(Locale.ROOT));
        }

        out.append(invoiceSeparator()).append('\n');
        out.append("FACTURA ").append(invoiceNumber).append('\n');
        out.append("Ticket ").append(summary.id()).append("  Mesa ")
                .append(selectedTicket == null || selectedTicket.tableNumber() == null ? "-" : selectedTicket.tableNumber())
                .append('\n');
        out.append("Fecha ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        out.append(invoiceSeparator()).append('\n');
        out.append("CLIENTE").append('\n');
        out.append(clip(blankTo(customer == null ? "" : customer.legalName(), customer == null ? "" : customer.displayName()), INVOICE_LINE_WIDTH)).append('\n');
        if (customer != null && !blankTo(customer.taxId(), "").isBlank()) {
            out.append("NIF/CIF ").append(customer.taxId()).append('\n');
        }
        if (customer != null && !blankTo(customer.fiscalAddress(), "").isBlank()) {
            appendWrappedInvoiceText(out, customer.fiscalAddress());
        }
        if (customer != null) {
            String customerCity = String.format(
                    Locale.ROOT,
                    "%s %s %s",
                    blankTo(customer.postalCode(), ""),
                    blankTo(customer.city(), ""),
                    blankTo(customer.province(), "")
            ).trim();
            if (!customerCity.isBlank()) {
                appendWrappedInvoiceText(out, customerCity);
            }
            if (!blankTo(customer.country(), "").isBlank()) {
                appendWrappedInvoiceText(out, customer.country().toUpperCase(Locale.ROOT));
            }
        }

        out.append(invoiceSeparator()).append('\n');
        appendInvoiceItemsHeader(out);
        out.append(invoiceSeparator()).append('\n');
        Map<Long, Integer> vatByProductId = loadVatByProductId();
        Map<Integer, VatTotals> vatBreakdown = new TreeMap<>();
        int totalBaseCents = 0;
        if (summary.lines() != null) {
            for (TicketSummaryResponse.TicketLineSummary line : summary.lines()) {
                int vatRateBps = vatByProductId.getOrDefault(line.productId(), 1000);
                int unitBaseCents = netFromGrossCents(line.unitPriceCents(), vatRateBps);
                int baseCents = netFromGrossCents(line.lineTotalCents(), vatRateBps);
                int vatCents = Math.max(0, line.lineTotalCents() - baseCents);
                totalBaseCents += baseCents;
                vatBreakdown.computeIfAbsent(vatRateBps, ignored -> new VatTotals()).add(baseCents, vatCents);
                appendInvoiceLineWithAmounts(out, line.qty(), line.productName(), unitBaseCents, baseCents);
            }
        }
        out.append(invoiceSeparator()).append('\n');
        appendInvoiceAmountLine(out, "BASE IMPONIBLE", totalBaseCents);
        for (Map.Entry<Integer, VatTotals> entry : vatBreakdown.entrySet()) {
            appendInvoiceAmountLine(out, "IVA (" + vatRateLabel(entry.getKey()) + ")", entry.getValue().vatCents);
        }
        appendInvoiceAmountLine(out, "TOTAL", summary.totalCents());
        appendInvoiceAmountLine(out, "PAGADO", summary.paidCents());
        out.append(invoiceSeparator()).append('\n');
        if (summary.payments() != null && !summary.payments().isEmpty()) {
            out.append("Pagos registrados").append('\n');
            for (TicketSummaryResponse.PaymentSummary payment : summary.payments()) {
                out.append(String.format(
                        Locale.US,
                        "%-8s %8.2f %s",
                        payment.method(),
                        payment.amountCents() / 100.0,
                        payment.createdAt() == null ? "" : DT.format(payment.createdAt())
                )).append('\n');
            }
        }
        appendBottomMargin(out);
        return out.toString();
    }

    private String buildInvoiceText(InvoiceResponse invoice) {
        StringBuilder out = new StringBuilder();
        out.append(clip(blankTo(invoice.businessLegalName(), invoice.businessName()).toUpperCase(Locale.ROOT), INVOICE_LINE_WIDTH)).append('\n');
        out.append("NIF/CIF ").append(blankTo(invoice.businessTaxId(), "-")).append('\n');
        if (!blankTo(invoice.businessAddress(), "").isBlank()) {
            appendWrappedInvoiceText(out, invoice.businessAddress());
        }
        String businessCity = String.format(
                Locale.ROOT,
                "%s %s %s",
                blankTo(invoice.businessPostalCode(), ""),
                blankTo(invoice.businessCity(), ""),
                blankTo(invoice.businessProvince(), "")
        ).trim();
        if (!businessCity.isBlank()) {
            appendWrappedInvoiceText(out, businessCity);
        }
        if (!blankTo(invoice.businessCountry(), "").isBlank()) {
            appendWrappedInvoiceText(out, invoice.businessCountry().toUpperCase(Locale.ROOT));
        }
        out.append(invoiceSeparator()).append('\n');
        out.append("FACTURA ").append(invoice.invoiceNumber()).append('\n');
        out.append("Ticket ").append(invoice.ticketId()).append("  Mesa ")
                .append(invoice.tableNumber() == null ? "-" : invoice.tableNumber()).append('\n');
        out.append("Fecha ").append(invoice.issuedAt() == null ? "-" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(invoice.issuedAt())).append('\n');
        out.append(invoiceSeparator()).append('\n');
        out.append("CLIENTE").append('\n');
        out.append(clip(blankTo(invoice.customerLegalName(), invoice.customerDisplayName()), INVOICE_LINE_WIDTH)).append('\n');
        if (!blankTo(invoice.customerTaxId(), "").isBlank()) {
            out.append("NIF/CIF ").append(invoice.customerTaxId()).append('\n');
        }
        if (!blankTo(invoice.customerAddress(), "").isBlank()) {
            appendWrappedInvoiceText(out, invoice.customerAddress());
        }
        String customerCity = String.format(
                Locale.ROOT,
                "%s %s %s",
                blankTo(invoice.customerPostalCode(), ""),
                blankTo(invoice.customerCity(), ""),
                blankTo(invoice.customerProvince(), "")
        ).trim();
        if (!customerCity.isBlank()) {
            appendWrappedInvoiceText(out, customerCity);
        }
        if (!blankTo(invoice.customerCountry(), "").isBlank()) {
            appendWrappedInvoiceText(out, invoice.customerCountry().toUpperCase(Locale.ROOT));
        }
        out.append(invoiceSeparator()).append('\n');
        appendInvoiceItemsHeader(out);
        out.append(invoiceSeparator()).append('\n');
        Map<Integer, VatTotals> vatBreakdown = new TreeMap<>();
        if (invoice.lines() != null) {
            for (var line : invoice.lines()) {
                int unitNetCents = netFromGrossCents(line.unitGrossCents(), line.vatRateBps());
                appendInvoiceLineWithAmounts(out, line.qty(), line.productName(), unitNetCents, line.lineNetCents());
                vatBreakdown.computeIfAbsent(line.vatRateBps(), ignored -> new VatTotals())
                        .add(line.lineNetCents(), line.lineVatCents());
            }
        }
        out.append(invoiceSeparator()).append('\n');
        appendInvoiceAmountLine(out, "BASE IMPONIBLE", invoice.totalNetCents());
        for (Map.Entry<Integer, VatTotals> entry : vatBreakdown.entrySet()) {
            appendInvoiceAmountLine(out, "IVA (" + vatRateLabel(entry.getKey()) + ")", entry.getValue().vatCents);
        }
        appendInvoiceAmountLine(out, "TOTAL", invoice.totalGrossCents());
        appendBottomMargin(out);
        return out.toString();
    }

    private static String invoiceSeparator() {
        return "-".repeat(INVOICE_LINE_WIDTH);
    }

    private static void appendWrappedInvoiceText(StringBuilder out, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String part : wrapByWords(text, INVOICE_LINE_WIDTH)) {
            out.append(part).append('\n');
        }
    }

    private static void appendBottomMargin(StringBuilder out) {
        out.append('\n').append('\n').append('\n').append('\n').append('\n');
    }

    private static void appendInvoiceAmountLine(StringBuilder out, String label, int amountCents) {
        String safeLabel = (label == null ? "" : label.trim()) + ":";
        String amount = String.format(Locale.US, "%.2f", amountCents / 100.0);
        int maxLeft = Math.max(4, INVOICE_LINE_WIDTH - amount.length() - 1);
        String left = safeLabel.length() <= maxLeft ? safeLabel : safeLabel.substring(0, maxLeft);
        int gap = Math.max(1, INVOICE_LINE_WIDTH - left.length() - amount.length());
        out.append(left).append(" ".repeat(gap)).append(amount).append('\n');
    }

    private static void appendInvoiceItemsHeader(StringBuilder out) {
        out.append(formatInvoiceColumns("CANT", "DESCRIPCION", "PRECIO", "IMPORTE")).append('\n');
    }

    private static void appendInvoiceLineWithAmounts(
            StringBuilder out,
            int qty,
            String productName,
            int unitAmountCents,
            int lineAmountCents
    ) {
        String qtyText = Math.max(1, qty) + "x";
        String unitText = String.format(Locale.US, "%.2f", unitAmountCents / 100.0);
        String amount = String.format(Locale.US, "%.2f", lineAmountCents / 100.0);
        String safeName = productName == null || productName.isBlank() ? "-" : productName.trim();

        List<String> wrapped = wrapByWords(safeName, INVOICE_DESC_COL_WIDTH);
        String firstName = wrapped.isEmpty() ? "-" : wrapped.getFirst();
        out.append(formatInvoiceColumns(qtyText, firstName, unitText, amount)).append('\n');

        if (wrapped.size() <= 1) {
            return;
        }
        for (int i = 1; i < wrapped.size(); i++) {
            out.append(formatInvoiceColumns("", wrapped.get(i), "", "")).append('\n');
        }
    }

    private static String formatInvoiceColumns(String qty, String description, String unitPrice, String amount) {
        return padRight(trimToWidth(qty, INVOICE_QTY_COL_WIDTH), INVOICE_QTY_COL_WIDTH)
                + ' '
                + padRight(trimToWidth(description, INVOICE_DESC_COL_WIDTH), INVOICE_DESC_COL_WIDTH)
                + ' '
                + padLeft(trimToWidth(unitPrice, INVOICE_UNIT_COL_WIDTH), INVOICE_UNIT_COL_WIDTH)
                + ' '
                + padLeft(trimToWidth(amount, INVOICE_TOTAL_COL_WIDTH), INVOICE_TOTAL_COL_WIDTH);
    }

    private static List<String> wrapByWords(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
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

    private static int netFromGrossCents(int grossCents, int vatRateBps) {
        if (vatRateBps <= 0) {
            return grossCents;
        }
        double net = grossCents * 10000.0 / (10000.0 + vatRateBps);
        return (int) Math.round(net);
    }

    private static String vatRateLabel(int vatRateBps) {
        return String.format(Locale.US, "%.2f%%", vatRateBps / 100.0);
    }

    private Map<Long, Integer> loadVatByProductId() {
        Map<Long, Integer> vatByProduct = new LinkedHashMap<>();
        try {
            var products = com.tpv.desktop.api.pos.CatalogApi.products(null);
            if (products == null) {
                return vatByProduct;
            }
            for (var product : products) {
                if (product == null) {
                    continue;
                }
                vatByProduct.put(product.id(), product.vatRateBps());
            }
        } catch (Exception ignored) {
            // Si no se puede cargar catalogo, se usa fallback 10% en el preview.
        }
        return vatByProduct;
    }

    private static final class VatTotals {
        int netCents;
        int vatCents;

        void add(int net, int vat) {
            this.netCents += net;
            this.vatCents += vat;
        }
    }

    private static String blankTo(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1) + ".";
    }
}
