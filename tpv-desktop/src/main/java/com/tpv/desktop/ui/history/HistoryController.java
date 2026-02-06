package com.tpv.desktop.ui.history;

import com.tpv.desktop.api.pos.TicketHistoryApi;
import com.tpv.desktop.api.pos.TicketResponse;
import com.tpv.desktop.api.pos.TicketSummaryResponse;
import com.tpv.desktop.core.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class HistoryController {

    @FXML
    private TableView<TicketResponse> ticketsTable;
    @FXML
    private TableColumn<TicketResponse, String> colId;
    @FXML
    private TableColumn<TicketResponse, String> colTotal;
    @FXML
    private TableColumn<TicketResponse, String> colCreated;
    @FXML
    private Label listErrorLabel;

    @FXML
    private Label detailTitle;
    @FXML
    private Label detailTotal;
    @FXML
    private Label detailPaid;
    @FXML
    private Label detailRemaining;
    @FXML
    private Label detailErrorLabel;

    @FXML
    private TableView<TicketSummaryResponse.TicketLineSummary> linesTable;
    @FXML
    private TableColumn<TicketSummaryResponse.TicketLineSummary, String> colLineName;
    @FXML
    private TableColumn<TicketSummaryResponse.TicketLineSummary, Integer> colLineQty;
    @FXML
    private TableColumn<TicketSummaryResponse.TicketLineSummary, String> colLineUnit;
    @FXML
    private TableColumn<TicketSummaryResponse.TicketLineSummary, String> colLineTotal;

    @FXML
    private TableView<TicketSummaryResponse.PaymentSummary> paymentsTable;
    @FXML
    private TableColumn<TicketSummaryResponse.PaymentSummary, String> colPayMethod;
    @FXML
    private TableColumn<TicketSummaryResponse.PaymentSummary, String> colPayAmount;
    @FXML
    private TableColumn<TicketSummaryResponse.PaymentSummary, String> colPayDate;
    @FXML
    private Button openInSalesBtn;

    private final ObservableList<TicketResponse> tickets = FXCollections.observableArrayList();
    private final ObservableList<TicketSummaryResponse.TicketLineSummary> lines = FXCollections.observableArrayList();
    private final ObservableList<TicketSummaryResponse.PaymentSummary> payments = FXCollections.observableArrayList();
    private TicketResponse selectedTicket;
    private static final DateTimeFormatter DT
            = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @FXML
    public void initialize() {
        openInSalesBtn.setDisable(true);
        setupTables();
        ticketsTable.setItems(tickets);
        linesTable.setItems(lines);
        paymentsTable.setItems(payments);

        ticketsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            selectedTicket = newV;
            openInSalesBtn.setDisable(newV == null);
            if (newV != null) {
                loadDetail(newV.id());
            }
        });

        onRefresh();
    }

    private void setupTables() {
        colId.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(String.valueOf(c.getValue().id())));
        colTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().totalCents()) + " €"));
        colCreated.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(
                c.getValue().createdAt() == null ? "-" : DT.format(c.getValue().createdAt())
        ));

        colLineName.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().productName()));
        colLineQty.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().qty()));
        colLineUnit.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().unitPriceCents())));
        colLineTotal.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().lineTotalCents())));

        colPayMethod.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().method()));
        colPayAmount.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(MoneyUtil.centsToEuros(c.getValue().amountCents()) + " €"));
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

    private void loadDetail(long ticketId) {
        detailErrorLabel.setText("");
        try {
            TicketSummaryResponse s = TicketHistoryApi.summary(ticketId);

            detailTitle.setText("Ticket #" + s.id() + " — " + s.status());
            detailTotal.setText(MoneyUtil.centsToEuros(s.totalCents()) + " €");
            detailPaid.setText(MoneyUtil.centsToEuros(s.paidCents()) + " €");
            detailRemaining.setText(MoneyUtil.centsToEuros(s.remainingCents()) + " €");

            lines.setAll(s.lines() == null ? FXCollections.observableArrayList() : s.lines());
            payments.setAll(s.payments() == null ? FXCollections.observableArrayList() : s.payments());

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
        lines.clear();
        payments.clear();
    }

    @FXML
    public void onOpenInSales() {
        if (selectedTicket == null) {
            return;
        }

        com.tpv.desktop.core.AppState.setResumeTicketId(selectedTicket.id());
        com.tpv.desktop.core.Nav.goToSales();
    }

}
