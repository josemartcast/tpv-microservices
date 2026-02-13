package com.tpv.desktop.ui.cash;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.CashSessionCloseSummaryResponse;
import com.tpv.desktop.api.pos.CashSessionOpenTicketResponse;
import com.tpv.desktop.api.pos.CashSessionResponse;
import com.tpv.desktop.api.pos.ResolveOpenTicketsResponse;
import com.tpv.desktop.core.MoneyUtil;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

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
  @FXML private Button resolveOpenTicketsBtn;

  @FXML private ChoiceBox<String> incidentDirectionChoice;
  @FXML private TextField incidentEurosField;
  @FXML private TextField incidentNoteField;
  @FXML private Button addIncidentBtn;

  private CashSessionResponse current;

  private static final DateTimeFormatter DT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
          .withZone(ZoneId.systemDefault());

  @FXML
  public void initialize() {
    incidentDirectionChoice.getItems().setAll("IN", "OUT");
    incidentDirectionChoice.setValue("OUT");
    onRefresh();
  }

  @FXML
  public void onRefresh() {
    errorLabel.setText("");
    statusLabel.setText("Cargando...");
    detailsLabel.setText("");
    openBtn.setDisable(true);
    closeBtn.setDisable(true);
    addIncidentBtn.setDisable(true);
    if (resolveOpenTicketsBtn != null) {
      resolveOpenTicketsBtn.setDisable(true);
    }

    try {
      current = CashApi.current();
      renderCurrent(current);
    } catch (ApiException e) {
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
      String msg = "No hay sesion abierta para cerrar.";
      errorLabel.setText(msg);
      showInfoDialog("Cerrar caja", msg);
      return;
    }

    final int closingCents;
    final String note = closeNoteField.getText();
    try {
      closingCents = MoneyUtil.eurosToCents(closingEurosField.getText());
      if (closingCents < 0) {
        String msg = "El efectivo contado no puede ser negativo.";
        errorLabel.setText(msg);
        showInfoDialog("Cerrar caja", msg);
        return;
      }
    } catch (Exception e) {
      String msg = "Error en efectivo contado: revisa el formato (ej: 245.50).";
      errorLabel.setText(msg);
      showInfoDialog("Cerrar caja", msg);
      return;
    }

    try {
      List<CashSessionOpenTicketResponse> openTickets = CashApi.openTickets(current.id());
      List<CashSessionOpenTicketResponse> pendingWithAmount = openTickets.stream()
          .filter(t -> t.totalCents() > 0)
          .toList();
      if (!pendingWithAmount.isEmpty()) {
        showPendingTicketsAlert(pendingWithAmount);
        errorLabel.setText("No se pudo cerrar caja: faltan tickets por cobrar.");
        return;
      }

      String resolveMessage = "";
      if (!openTickets.isEmpty()) {
        ResolveOpenTicketsResponse resolved = CashApi.resolveOpenTickets(current.id());
        if (resolved.openAfter() > 0) {
          showPendingTicketsAlert(resolved.remainingOpenTickets());
          errorLabel.setText(
              "No se pudo cerrar caja: quedan " + resolved.openAfter() + " tickets con importe. "
                  + formatOpenTickets(resolved.remainingOpenTickets()));
          return;
        }
        if (resolved.autoCancelled() > 0) {
          resolveMessage = "Se resolvieron " + resolved.autoCancelled() + " tickets vacios. ";
        }
      }

      current = CashApi.close(current.id(), closingCents, note);
      renderCurrent(current);
      String success = resolveMessage + "Caja cerrada correctamente.";
      errorLabel.setText(success);
      showInfoDialog("Cerrar caja", success);
    } catch (ApiException e) {
      if (e.getStatus() == 409 && e.getMessage() != null && e.getMessage().contains("OPEN tickets")) {
        try {
          List<CashSessionOpenTicketResponse> open = CashApi.openTickets(current.id());
          List<CashSessionOpenTicketResponse> pendingWithAmount = open.stream()
              .filter(t -> t.totalCents() > 0)
              .toList();
          if (!pendingWithAmount.isEmpty()) {
            showPendingTicketsAlert(pendingWithAmount);
            errorLabel.setText("No se pudo cerrar caja: faltan tickets por cobrar.");
            return;
          }
          String msg = "No se pudo cerrar caja: hay tickets OPEN en esta caja.";
          errorLabel.setText(msg);
          showInfoDialog("Cerrar caja", msg);
        } catch (Exception retryErr) {
          String msg = "No se pudo cerrar caja: " + retryErr.getMessage();
          errorLabel.setText(msg);
          showInfoDialog("Cerrar caja", msg);
        }
      } else {
        String msg = "No se pudo cerrar caja: " + e.getMessage();
        errorLabel.setText(msg);
        showInfoDialog("Cerrar caja", msg);
      }
    } catch (Exception e) {
      String msg = "No se pudo cerrar caja: " + e.getMessage();
      errorLabel.setText(msg);
      showInfoDialog("Cerrar caja", msg);
    }
  }

  @FXML
  public void onAddIncident() {
    errorLabel.setText("");
    if (current == null) {
      errorLabel.setText("No hay sesion abierta para registrar incidencias.");
      return;
    }

    try {
      int amountCents = MoneyUtil.eurosToCents(incidentEurosField.getText());
      if (amountCents <= 0) {
        errorLabel.setText("El importe de incidencia debe ser mayor que 0.");
        return;
      }
      String direction = incidentDirectionChoice.getValue();
      if (direction == null || direction.isBlank()) {
        errorLabel.setText("Selecciona direccion IN/OUT.");
        return;
      }

      CashApi.addIncident(current.id(), direction, amountCents, incidentNoteField.getText());
      incidentEurosField.clear();
      incidentNoteField.clear();
      onRefresh();
    } catch (ApiException e) {
      errorLabel.setText("No se pudo registrar incidencia: " + e.getMessage());
    } catch (Exception e) {
      errorLabel.setText("Error inesperado: " + e.getMessage());
    }
  }

  @FXML
  public void onResolveOpenTickets() {
    errorLabel.setText("");
    if (current == null) {
      String msg = "No hay sesion abierta.";
      errorLabel.setText(msg);
      showInfoDialog("Resolver tickets abiertos", msg);
      return;
    }

    try {
      ResolveOpenTicketsResponse response = CashApi.resolveOpenTickets(current.id());
      String message;
      if (response.openAfter() == 0) {
        message = "Tickets abiertos resueltos. Auto-cancelados: " + response.autoCancelled() + ".";
      } else {
        showPendingTicketsAlert(response.remainingOpenTickets());
        message = "Auto-cancelados " + response.autoCancelled()
                + " tickets vacios. Quedan " + response.openAfter()
                + " tickets con importe: "
                + response.remainingOpenTickets().stream().map(this::formatOpenTicket).collect(Collectors.joining(", "));
      }
      onRefresh();
      errorLabel.setText(message);
      showInfoDialog("Resolver tickets abiertos", message);
    } catch (ApiException e) {
      String msg = "No se pudieron resolver tickets abiertos: " + e.getMessage();
      errorLabel.setText(msg);
      showInfoDialog("Resolver tickets abiertos", msg);
    } catch (Exception e) {
      String msg = "Error inesperado: " + e.getMessage();
      errorLabel.setText(msg);
      showInfoDialog("Resolver tickets abiertos", msg);
    }
  }

  private void renderNoSession() {
    statusLabel.setText("CERRADA / SIN SESION ABIERTA");
    detailsLabel.setText("No hay sesion de caja abierta. Abre caja para empezar a vender.");
    openBtn.setDisable(false);
    closeBtn.setDisable(true);
    addIncidentBtn.setDisable(true);
  }

  private void renderCurrent(CashSessionResponse cs) {
    String st = cs.status();
    statusLabel.setText("Sesion #" + cs.id() + " - " + st);

    String openedAt = cs.openedAt() != null ? DT.format(cs.openedAt()) : "-";
    String openedBy = cs.openedBy() != null ? cs.openedBy() : "-";

    StringBuilder sb = new StringBuilder();
    sb.append("Abierta: ").append(openedAt).append(" por ").append(openedBy).append("\n");
    sb.append("Efectivo inicial: ").append(MoneyUtil.centsToEuros(cs.openingCashCents())).append(" EUR\n");

    try {
      CashSessionCloseSummaryResponse summary = CashApi.closeSummary(cs.id());
      sb.append("Ventas efectivo (neto): ")
          .append(MoneyUtil.centsToEuros(summary.cashPaymentsNetCents())).append(" EUR\n");
      sb.append("Incidencias IN: ")
          .append(MoneyUtil.centsToEuros(summary.incidentsInCents())).append(" EUR\n");
      sb.append("Incidencias OUT: ")
          .append(MoneyUtil.centsToEuros(summary.incidentsOutCents())).append(" EUR\n");
      sb.append("Efectivo esperado: ")
          .append(MoneyUtil.centsToEuros(summary.expectedCashCents())).append(" EUR\n");

      try {
        List<CashSessionOpenTicketResponse> open = CashApi.openTickets(cs.id());
        if (open.isEmpty()) {
          sb.append("Tickets OPEN: 0\n");
        } else {
          sb.append("Tickets OPEN: ").append(open.size()).append(" (")
              .append(open.stream()
                  .map(this::formatOpenTicket)
                  .collect(Collectors.joining(", ")))
              .append(")\n");
        }
      } catch (Exception ignored) {
        // non-blocking diagnostic info
      }
    } catch (Exception e) {
      sb.append("Efectivo esperado: ").append(MoneyUtil.centsToEuros(cs.expectedCashCents())).append(" EUR\n");
    }

    if (cs.closingCashCents() != null) {
      sb.append("Efectivo contado: ").append(MoneyUtil.centsToEuros(cs.closingCashCents())).append(" EUR\n");
    }
    if (cs.differenceCents() != null) {
      sb.append("Diferencia: ").append(MoneyUtil.centsToEuros(cs.differenceCents())).append(" EUR\n");
    }
    if (cs.note() != null && !cs.note().isBlank()) {
      sb.append("Nota: ").append(cs.note());
    }

    detailsLabel.setText(sb.toString());

    boolean isOpen = "OPEN".equalsIgnoreCase(st);
    openBtn.setDisable(isOpen);
    closeBtn.setDisable(!isOpen);
    addIncidentBtn.setDisable(!isOpen);
    if (resolveOpenTicketsBtn != null) {
      resolveOpenTicketsBtn.setDisable(!isOpen);
    }
  }

  private String formatOpenTickets(List<CashSessionOpenTicketResponse> open) {
    if (open == null || open.isEmpty()) {
      return "";
    }
    return "Abiertos: " + open.stream()
        .map(this::formatOpenTicket)
        .collect(Collectors.joining(", "));
  }

  private String formatOpenTicket(CashSessionOpenTicketResponse t) {
    String mesa = t.tableNumber() == null ? "sin mesa" : ("Mesa " + t.tableNumber());
    return "#" + t.ticketId() + " (" + mesa + ")";
  }

  private void showPendingTicketsAlert(List<CashSessionOpenTicketResponse> tickets) {
    if (tickets == null || tickets.isEmpty()) {
      return;
    }
    String detail = tickets.stream()
        .map(t -> "Ticket #" + t.ticketId()
            + " - " + (t.tableNumber() == null ? "Sin mesa" : ("Mesa " + t.tableNumber()))
            + " - " + MoneyUtil.centsToEuros(t.totalCents()) + " EUR")
        .collect(Collectors.joining("\n"));

    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("Tickets pendientes");
    alert.setHeaderText("Faltan tickets por cobrar");
    alert.setContentText(detail);
    alert.getButtonTypes().setAll(ButtonType.OK);
    alert.showAndWait();
  }

  private void showInfoDialog(String title, String message) {
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle(title);
    alert.setHeaderText(null);
    alert.setContentText(message);
    alert.getButtonTypes().setAll(ButtonType.OK);
    alert.showAndWait();
  }
}
