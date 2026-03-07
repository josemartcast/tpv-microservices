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
import java.util.Arrays;
import java.util.Locale;
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

        invoiceTable.getColumns().setAll(colInvNumber, colInvDate, colInvTicket, colInvTable, colInvCustomer, colInvTotal);

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
                    PrintUtil.printTextToPdf(
                            buildInvoiceText(invoice),
                            dialog.getDialogPane().getScene() == null ? null : dialog.getDialogPane().getScene().getWindow()
                    );
                    invoiceErrorLabel.setStyle("-fx-text-fill: #1e7e34;");
                    invoiceErrorLabel.setText("Factura " + invoice.invoiceNumber() + " enviada a Print to PDF.");
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
            ButtonType pdfBtn = new ButtonType("Print to PDF", ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().addAll(copyBtn, pdfBtn, ButtonType.CLOSE);

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
            if (action == pdfBtn) {
                CustomerResponse selectedCustomer = customerBox.getValue();
                if (selectedCustomer == null) {
                    detailErrorLabel.setText("Selecciona un cliente fiscal.");
                    return;
                }
                InvoiceResponse invoice = TicketApi.issueInvoice(selectedTicket.id(), selectedCustomer.id());
                String finalText = buildInvoiceText(invoice);
                PrintUtil.printTextToPdf(finalText,
                        detailErrorLabel != null && detailErrorLabel.getScene() != null ? detailErrorLabel.getScene().getWindow() : null);
                detailErrorLabel.setText("Factura " + invoice.invoiceNumber() + " guardada y enviada a Print to PDF.");
            }
        } catch (Exception e) {
            detailErrorLabel.setText("No se pudo generar factura: " + e.getMessage());
        }
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

        out.append(clip(businessName.toUpperCase(Locale.ROOT), 46)).append('\n');
        out.append("NIF/CIF ").append(taxId).append('\n');
        if (!blankTo(address, "").isBlank()) {
            out.append(clip(address, 46)).append('\n');
        }
        String cityLine = String.format(
                Locale.ROOT,
                "%s %s %s",
                blankTo(cp, ""),
                blankTo(city, ""),
                blankTo(province, "")
        ).trim();
        if (!cityLine.isBlank()) {
            out.append(clip(cityLine, 46)).append('\n');
        }
        if (!blankTo(country, "").isBlank()) {
            out.append(clip(country.toUpperCase(Locale.ROOT), 46)).append('\n');
        }

        out.append("----------------------------------------------").append('\n');
        out.append("FACTURA ").append(invoiceNumber).append('\n');
        out.append("Ticket ").append(summary.id()).append("  Mesa ")
                .append(selectedTicket == null || selectedTicket.tableNumber() == null ? "-" : selectedTicket.tableNumber())
                .append('\n');
        out.append("Fecha ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        out.append("----------------------------------------------").append('\n');
        out.append("CLIENTE").append('\n');
        out.append(clip(blankTo(customer == null ? "" : customer.legalName(), customer == null ? "" : customer.displayName()), 46)).append('\n');
        if (customer != null && !blankTo(customer.taxId(), "").isBlank()) {
            out.append("NIF/CIF ").append(customer.taxId()).append('\n');
        }
        if (customer != null && !blankTo(customer.fiscalAddress(), "").isBlank()) {
            out.append(clip(customer.fiscalAddress(), 46)).append('\n');
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
                out.append(clip(customerCity, 46)).append('\n');
            }
            if (!blankTo(customer.country(), "").isBlank()) {
                out.append(clip(customer.country().toUpperCase(Locale.ROOT), 46)).append('\n');
            }
        }

        out.append("----------------------------------------------").append('\n');
        if (summary.lines() != null) {
            for (TicketSummaryResponse.TicketLineSummary line : summary.lines()) {
                out.append(String.format(
                        Locale.US,
                        "%2dx %-24s %10.2f",
                        line.qty(),
                        clip(line.productName(), 24),
                        line.lineTotalCents() / 100.0
                )).append('\n');
            }
        }
        out.append("----------------------------------------------").append('\n');
        out.append(String.format(Locale.US, "TOTAL:%36.2f", summary.totalCents() / 100.0)).append('\n');
        out.append(String.format(Locale.US, "PAGADO:%35.2f", summary.paidCents() / 100.0)).append('\n');
        out.append(String.format(Locale.US, "PENDIENTE:%32.2f", summary.remainingCents() / 100.0)).append('\n');
        out.append("----------------------------------------------").append('\n');
        if (summary.payments() != null && !summary.payments().isEmpty()) {
            out.append("Pagos registrados").append('\n');
            for (TicketSummaryResponse.PaymentSummary payment : summary.payments()) {
                out.append(String.format(
                        Locale.US,
                        "%-10s %10.2f %s",
                        payment.method(),
                        payment.amountCents() / 100.0,
                        payment.createdAt() == null ? "" : DT.format(payment.createdAt())
                )).append('\n');
            }
        }
        return out.toString();
    }

    private String buildInvoiceText(InvoiceResponse invoice) {
        StringBuilder out = new StringBuilder();
        out.append(clip(blankTo(invoice.businessLegalName(), invoice.businessName()).toUpperCase(Locale.ROOT), 46)).append('\n');
        out.append("NIF/CIF ").append(blankTo(invoice.businessTaxId(), "-")).append('\n');
        if (!blankTo(invoice.businessAddress(), "").isBlank()) {
            out.append(clip(invoice.businessAddress(), 46)).append('\n');
        }
        String businessCity = String.format(
                Locale.ROOT,
                "%s %s %s",
                blankTo(invoice.businessPostalCode(), ""),
                blankTo(invoice.businessCity(), ""),
                blankTo(invoice.businessProvince(), "")
        ).trim();
        if (!businessCity.isBlank()) {
            out.append(clip(businessCity, 46)).append('\n');
        }
        if (!blankTo(invoice.businessCountry(), "").isBlank()) {
            out.append(clip(invoice.businessCountry().toUpperCase(Locale.ROOT), 46)).append('\n');
        }
        out.append("----------------------------------------------").append('\n');
        out.append("FACTURA ").append(invoice.invoiceNumber()).append('\n');
        out.append("Ticket ").append(invoice.ticketId()).append("  Mesa ")
                .append(invoice.tableNumber() == null ? "-" : invoice.tableNumber()).append('\n');
        out.append("Fecha ").append(invoice.issuedAt() == null ? "-" : DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault()).format(invoice.issuedAt())).append('\n');
        out.append("----------------------------------------------").append('\n');
        out.append("CLIENTE").append('\n');
        out.append(clip(blankTo(invoice.customerLegalName(), invoice.customerDisplayName()), 46)).append('\n');
        if (!blankTo(invoice.customerTaxId(), "").isBlank()) {
            out.append("NIF/CIF ").append(invoice.customerTaxId()).append('\n');
        }
        if (!blankTo(invoice.customerAddress(), "").isBlank()) {
            out.append(clip(invoice.customerAddress(), 46)).append('\n');
        }
        String customerCity = String.format(
                Locale.ROOT,
                "%s %s %s",
                blankTo(invoice.customerPostalCode(), ""),
                blankTo(invoice.customerCity(), ""),
                blankTo(invoice.customerProvince(), "")
        ).trim();
        if (!customerCity.isBlank()) {
            out.append(clip(customerCity, 46)).append('\n');
        }
        if (!blankTo(invoice.customerCountry(), "").isBlank()) {
            out.append(clip(invoice.customerCountry().toUpperCase(Locale.ROOT), 46)).append('\n');
        }
        out.append("----------------------------------------------").append('\n');
        if (invoice.lines() != null) {
            for (var line : invoice.lines()) {
                out.append(String.format(
                        Locale.US,
                        "%2dx %-24s %10.2f",
                        line.qty(),
                        clip(line.productName(), 24),
                        line.lineGrossCents() / 100.0
                )).append('\n');
            }
        }
        out.append("----------------------------------------------").append('\n');
        out.append(String.format(Locale.US, "TOTAL:%36.2f", invoice.totalGrossCents() / 100.0)).append('\n');
        out.append(String.format(Locale.US, "BASE:%37.2f", invoice.totalNetCents() / 100.0)).append('\n');
        out.append(String.format(Locale.US, "IVA:%38.2f", invoice.totalVatCents() / 100.0)).append('\n');
        return out.toString();
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
