package com.tpv.desktop.ui.fiscal;

import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.CashSessionResponse;
import com.tpv.desktop.api.pos.FiscalApi;
import com.tpv.desktop.api.pos.FiscalSummaryResponse;
import com.tpv.desktop.core.MoneyUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class FiscalController {

  @FXML private Label cashSessionIdLabel;

  @FXML private Label paidTicketsLabel;
  @FXML private Label cancelledTicketsLabel;

  @FXML private Label grossLabel;
  @FXML private Label netLabel;
  @FXML private Label vatLabel;

  @FXML private Label cashPaymentsLabel;
  @FXML private Label cardPaymentsLabel;
  @FXML private Label bizumPaymentsLabel;

  @FXML private Label errorLabel;

  @FXML
  public void initialize() {
    onRefresh();
  }

  @FXML
  public void onRefresh() {
    errorLabel.setText("");

    try {
      CashSessionResponse cs = CashApi.current();
      long cashSessionId = cs.id();
      cashSessionIdLabel.setText(String.valueOf(cashSessionId));

      FiscalSummaryResponse s = FiscalApi.summary(cashSessionId);

      paidTicketsLabel.setText(String.valueOf(s.paidTicketsCount()));
      cancelledTicketsLabel.setText(String.valueOf(s.cancelledTicketsCount()));

      grossLabel.setText(MoneyUtil.centsToEuros(s.grossSalesCents()) + " €");
      netLabel.setText(MoneyUtil.centsToEuros(s.netSalesCents()) + " €");
      vatLabel.setText(MoneyUtil.centsToEuros(s.vatSalesCents()) + " €");

      cashPaymentsLabel.setText(MoneyUtil.centsToEuros(s.cashPaymentsCents()) + " €");
      cardPaymentsLabel.setText(MoneyUtil.centsToEuros(s.cardPaymentsCents()) + " €");
      bizumPaymentsLabel.setText(MoneyUtil.centsToEuros(s.bizumPaymentsCents()) + " €");

    } catch (Exception e) {
      errorLabel.setText("No se pudo cargar fiscal summary: " + e.getMessage());
    }
  }
}
