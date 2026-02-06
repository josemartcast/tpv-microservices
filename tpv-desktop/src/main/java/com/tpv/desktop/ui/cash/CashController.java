package com.tpv.desktop.ui.cash;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.CashSessionResponse;
import com.tpv.desktop.core.MoneyUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class CashController {

  @FXML private Label statusLabel;
  @FXML private Label detailsLabel;
  @FXML private Label errorLabel;

  @FXML private TextField openingEurosField;
  @FXML private TextField openNoteField;
  @FXML private Button openBtn;

  @FXML private TextField closingEurosField;
  @FXML private TextField closeNoteField;
  @FXML private Button closeBtn;

  private CashSessionResponse current;

  private static final DateTimeFormatter DT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
          .withZone(ZoneId.systemDefault());

  @FXML
  public void initialize() {
    onRefresh();
  }

  @FXML
  public void onRefresh() {
    errorLabel.setText("");
    statusLabel.setText("Cargando...");
    detailsLabel.setText("");
    openBtn.setDisable(true);
    closeBtn.setDisable(true);

    try {
      current = CashApi.current();
      renderCurrent(current);
    } catch (ApiException e) {
      // Si no hay sesión abierta, el backend devuelve 404
      if (e.getStatus() == 404) {
        current = null;
        renderNoSession();
        return;
      }
      errorLabel.setText("Error API: " + e.getMessage());
      statusLabel.setText("Error");
    } catch (Exception e) {
      errorLabel.setText("Error inesperado: " + e.getMessage());
      statusLabel.setText("Error");
    }
  }

  @FXML
  public void onOpen() {
    errorLabel.setText("");
    try {
      int openingCents = MoneyUtil.eurosToCents(openingEurosField.getText());
      if (openingCents < 0) {
        errorLabel.setText("El efectivo inicial no puede ser negativo.");
        return;
      }
      String note = openNoteField.getText();

      current = CashApi.open(openingCents, note);
      renderCurrent(current);
    } catch (ApiException e) {
      errorLabel.setText("No se pudo abrir caja: " + e.getMessage());
    } catch (Exception e) {
      errorLabel.setText("Error inesperado: " + e.getMessage());
    }
  }

  @FXML
  public void onClose() {
    errorLabel.setText("");
    if (current == null) {
      errorLabel.setText("No hay sesión abierta para cerrar.");
      return;
    }

    try {
      int closingCents = MoneyUtil.eurosToCents(closingEurosField.getText());
      if (closingCents < 0) {
        errorLabel.setText("El efectivo contado no puede ser negativo.");
        return;
      }
      String note = closeNoteField.getText();

      current = CashApi.close(current.id(), closingCents, note);
      renderCurrent(current);
    } catch (ApiException e) {
      errorLabel.setText("No se pudo cerrar caja: " + e.getMessage());
    } catch (Exception e) {
      errorLabel.setText("Error inesperado: " + e.getMessage());
    }
  }

  private void renderNoSession() {
    statusLabel.setText("CERRADA / SIN SESIÓN ABIERTA");
    detailsLabel.setText("No hay sesión de caja abierta. Abre caja para empezar a vender.");
    openBtn.setDisable(false);
    closeBtn.setDisable(true);
  }

  private void renderCurrent(CashSessionResponse cs) {
    String st = cs.status();
    statusLabel.setText("Sesión #" + cs.id() + " — " + st);

    String openedAt = cs.openedAt() != null ? DT.format(cs.openedAt()) : "-";
    String openedBy = cs.openedBy() != null ? cs.openedBy() : "-";

    String opening = MoneyUtil.centsToEuros(cs.openingCashCents());
    String expected = MoneyUtil.centsToEuros(cs.expectedCashCents());

    StringBuilder sb = new StringBuilder();
    sb.append("Abierta: ").append(openedAt).append(" por ").append(openedBy).append("\n");
    sb.append("Efectivo inicial: ").append(opening).append(" €\n");
    sb.append("Efectivo esperado: ").append(expected).append(" €\n");

    if (cs.closingCashCents() != null) {
      sb.append("Efectivo contado: ").append(MoneyUtil.centsToEuros(cs.closingCashCents())).append(" €\n");
    }
    if (cs.differenceCents() != null) {
      sb.append("Diferencia: ").append(MoneyUtil.centsToEuros(cs.differenceCents())).append(" €\n");
    }
    if (cs.note() != null && !cs.note().isBlank()) {
      sb.append("Nota: ").append(cs.note());
    }

    detailsLabel.setText(sb.toString());

    boolean isOpen = "OPEN".equalsIgnoreCase(st);
    openBtn.setDisable(isOpen);      // si está abierta, no permitas abrir otra
    closeBtn.setDisable(!isOpen);    // si está cerrada, no permitas cerrar
  }
}
