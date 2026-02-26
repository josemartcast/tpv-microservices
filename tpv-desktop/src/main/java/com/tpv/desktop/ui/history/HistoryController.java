package com.tpv.desktop.ui.history;

import com.tpv.desktop.api.pos.PaymentApi;
import com.tpv.desktop.api.pos.TicketApi;
import com.tpv.desktop.api.pos.TicketHistoryApi;
import com.tpv.desktop.api.pos.TicketResponse;
import com.tpv.desktop.api.pos.TicketSummaryResponse;
import com.tpv.desktop.core.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.UUID;

public class HistoryController {

    @FXML private TableView<TicketResponse> ticketsTable;
    @FXML private TableColumn<TicketResponse, String> colId;
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

    private final ObservableList<TicketResponse> tickets = FXCollections.observableArrayList();
    private final ObservableList<TicketSummaryResponse.TicketLineSummary> lines = FXCollections.observableArrayList();
    private final ObservableList<TicketSummaryResponse.PaymentSummary> payments = FXCollections.observableArrayList();
    private TicketResponse selectedTicket;
    private TicketSummaryResponse.PaymentSummary selectedPayment;

    private static final DateTimeFormatter DT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @FXML
    public void initialize() {
        openInSalesBtn.setDisable(true);
        refundBtn.setDisable(true);
        reopenPaidBtn.setDisable(true);
        setupTables();
        ticketsTable.setItems(tickets);
        linesTable.setItems(lines);
        paymentsTable.setItems(payments);

        ticketsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedTicket = newV;
            selectedPayment = null;
            openInSalesBtn.setDisable(true);
            refundBtn.setDisable(true);
            reopenPaidBtn.setDisable(true);
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
            TicketResponse[] arr = TicketHistoryApi.listOpen();
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
            reopenPaidBtn.setDisable(!isPaid);
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
        reopenPaidBtn.setDisable(true);
        refundBtn.setDisable(true);
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

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Se reabrira el ticket #" + selectedTicket.id() + " y se revertiran sus pagos netos.\nDeseas continuar?",
                ButtonType.OK,
                ButtonType.CANCEL
        );
        confirm.setTitle("Confirmar reapertura");
        confirm.setHeaderText("Modificar ticket pagado");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
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
}
