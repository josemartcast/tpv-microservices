package com.tpv.desktop.ui.fiscal;

import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.CashSessionCloseSummaryResponse;
import com.tpv.desktop.api.pos.CashSessionResponse;
import com.tpv.desktop.api.pos.CloseCashSessionRequest;
import com.tpv.desktop.core.MoneyUtil;
import java.util.UUID;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class FiscalClosureController {

  @FXML private Label openingLabel;
  @FXML private Label cashSalesLabel;
  @FXML private Label incidentsInLabel;
  @FXML private Label incidentsOutLabel;
  @FXML private Label incidentsNetLabel;
  @FXML private Label expectedLabel;
  @FXML private Label diffLabel;

  @FXML private TextField countedCashField;
  @FXML private TextArea noteArea;
  @FXML private Label statusLabel;

  private long cashSessionId;
  private int expectedCashCents;
  private String lastCloseAttemptSignature;
  private String lastCloseAttemptKey;

  @FXML
  public void initialize() {
    onRefresh();
  }

  @FXML
  public void onRefresh() {
    statusLabel.setText("");
    diffLabel.setText("-");
    openingLabel.setText("-");
    cashSalesLabel.setText("-");
    incidentsInLabel.setText("-");
    incidentsOutLabel.setText("-");
    incidentsNetLabel.setText("-");
    expectedLabel.setText("-");

    try {
      CashSessionResponse current = CashApi.current();
      cashSessionId = current.id();

      CashSessionCloseSummaryResponse summary = CashApi.closeSummary(cashSessionId);
      expectedCashCents = summary.expectedCashCents();

      openingLabel.setText(MoneyUtil.centsToEuros(summary.openingCashCents()) + " EUR");
      cashSalesLabel.setText(MoneyUtil.centsToEuros(summary.cashPaymentsNetCents()) + " EUR");
      incidentsInLabel.setText(MoneyUtil.centsToEuros(summary.incidentsInCents()) + " EUR");
      incidentsOutLabel.setText(MoneyUtil.centsToEuros(summary.incidentsOutCents()) + " EUR");
      incidentsNetLabel.setText(MoneyUtil.centsToEuros(summary.incidentsNetCents()) + " EUR");
      expectedLabel.setText(MoneyUtil.centsToEuros(expectedCashCents) + " EUR");
    } catch (Exception e) {
      statusLabel.setText("No se pudo cargar cierre: " + e.getMessage());
    }
  }

  @FXML
  public void onCloseCash() {
    statusLabel.setText("");

    try {
      int counted = MoneyUtil.eurosToCents(countedCashField.getText());
      if (counted < 0) {
        statusLabel.setText("El efectivo contado no puede ser negativo.");
        return;
      }

      int diff = counted - expectedCashCents;
      diffLabel.setText(MoneyUtil.centsToEuros(diff) + " EUR");

      CloseCashSessionRequest req = new CloseCashSessionRequest(counted, noteArea.getText());

      String note = noteArea.getText() == null ? "" : noteArea.getText().trim();
      String signature = cashSessionId + "|" + counted + "|" + note;
      String key;
      if (signature.equals(lastCloseAttemptSignature) && lastCloseAttemptKey != null) {
        key = lastCloseAttemptKey;
      } else {
        key = UUID.randomUUID().toString();
        lastCloseAttemptSignature = signature;
        lastCloseAttemptKey = key;
      }

      CashApi.close(cashSessionId, req, key);
      lastCloseAttemptSignature = null;
      lastCloseAttemptKey = null;
      statusLabel.setText("Caja cerrada correctamente.");
      onRefresh();
    } catch (Exception e) {
      statusLabel.setText("Error al cerrar caja: " + e.getMessage());
    }
  }
}
