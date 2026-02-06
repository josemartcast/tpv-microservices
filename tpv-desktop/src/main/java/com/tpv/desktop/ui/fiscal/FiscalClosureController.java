package com.tpv.desktop.ui.fiscal;

import com.tpv.desktop.api.pos.*;
import com.tpv.desktop.core.MoneyUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

public class FiscalClosureController {

  @FXML private Label openingLabel;
  @FXML private Label cashSalesLabel;
  @FXML private Label expectedLabel;
  @FXML private Label diffLabel;

  @FXML private TextField countedCashField;
  @FXML private TextArea noteArea;
  @FXML private Label statusLabel;

  private long cashSessionId;
  private int expectedCashCents;

  @FXML
  public void initialize() {
    onRefresh();
  }

  @FXML
  public void onRefresh() {
    statusLabel.setText("");
    diffLabel.setText("-");

    try {
      CashSessionResponse cs = CashApi.current();
      cashSessionId = cs.id();

      FiscalClosureResponse c = FiscalApi.closure(cashSessionId);

      openingLabel.setText(MoneyUtil.centsToEuros(c.openingCashCents()) + " €");
      cashSalesLabel.setText(MoneyUtil.centsToEuros(c.cashPaymentsCents()) + " €");
      expectedCashCents = c.expectedCashCents();
      expectedLabel.setText(MoneyUtil.centsToEuros(expectedCashCents) + " €");

    } catch (Exception e) {
      statusLabel.setText("No se pudo cargar cierre: " + e.getMessage());
    }
  }

  @FXML
  public void onCloseCash() {
    statusLabel.setText("");

    try {
      int counted = MoneyUtil.eurosToCents(countedCashField.getText());
      int diff = counted - expectedCashCents;
      diffLabel.setText(MoneyUtil.centsToEuros(diff) + " €");

      CloseCashSessionRequest req =
          new CloseCashSessionRequest(counted, noteArea.getText());

      CashApi.close(cashSessionId, req);

      statusLabel.setText("Caja cerrada correctamente.");

      // Opcional: bloquear UI o volver a pantalla caja
      // Nav.goToCash();

    } catch (Exception e) {
      statusLabel.setText("Error al cerrar caja: " + e.getMessage());
    }
  }
}
