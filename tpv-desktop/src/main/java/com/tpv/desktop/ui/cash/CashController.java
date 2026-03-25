package com.tpv.desktop.ui.cash;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.ui.UiDialogs;
import com.tpv.desktop.api.pos.CashApi;
import com.tpv.desktop.api.pos.CashSessionCloseSummaryResponse;
import com.tpv.desktop.api.pos.CashSessionOpenTicketResponse;
import com.tpv.desktop.api.pos.CashSessionResponse;
import com.tpv.desktop.api.pos.CashIncidentResponse;
import com.tpv.desktop.api.pos.FiscalExerciseResponse;
import com.tpv.desktop.api.pos.ResolveOpenTicketsResponse;
import com.tpv.desktop.api.pos.TicketHistoryApi;
import com.tpv.desktop.api.pos.TicketResponse;
import com.tpv.desktop.api.pos.TicketSummaryResponse;
import com.tpv.desktop.core.PrinterSettingsStore;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.core.MoneyUtil;
import com.tpv.desktop.tpv.ui.util.PrintUtil;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.fxml.FXMLLoader;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import com.tpv.desktop.ui.components.NumericPadController;

public class CashController {
  private static final int REPORT_LINE_WIDTH = 42;

  @FXML private Label statusLabel;
  @FXML private Label detailsLabel;
  @FXML private Label fiscalStatusLabel;
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
  private FiscalExerciseResponse currentFiscalExercise;
  private Stage numericPadStage;
  private NumericPadController numericPadController;

  private static final DateTimeFormatter DT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
          .withZone(ZoneId.systemDefault());

  @FXML
  public void initialize() {
    incidentDirectionChoice.getItems().setAll("IN", "OUT");
    incidentDirectionChoice.setValue("OUT");
    setupNumericPadPopup();
    onRefresh();
  }

  private void setupNumericPadPopup() {
    configureNumericField(openingEurosField);
    configureNumericField(incidentEurosField);
    configureNumericField(closingEurosField);

    openingEurosField.sceneProperty().addListener((obsScene, oldScene, newScene) -> {
      if (newScene == null) {
        return;
      }
      newScene.windowProperty().addListener((obsWindow, oldWindow, newWindow) -> {
        if (newWindow != null) {
          newWindow.addEventHandler(WindowEvent.WINDOW_HIDDEN, evt -> closeNumericPad());
        }
      });
    });
  }

  private void configureNumericField(TextField field) {
    if (field == null) {
      return;
    }
    field.focusedProperty().addListener((obs, oldV, focused) -> {
      if (Boolean.TRUE.equals(focused)) {
        showNumericPadFor(field);
      }
    });
    field.setOnMouseClicked(evt -> showNumericPadFor(field));
  }

  private void showNumericPadFor(TextField field) {
    if (field == null) {
      return;
    }
    try {
      ensureNumericPadLoaded(field);
      numericPadController.setTarget(field);
      placeNumericPadNearField(field);
      if (!numericPadStage.isShowing()) {
        numericPadStage.show();
      } else {
        numericPadStage.toFront();
      }
    } catch (Exception e) {
      errorLabel.setText("No se pudo abrir teclado numerico: " + e.getMessage());
    }
  }

  private void ensureNumericPadLoaded(TextField field) throws Exception {
    if (numericPadStage != null && numericPadController != null) {
      return;
    }
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/components/NumericPad.fxml"));
    Parent root = loader.load();
    numericPadController = loader.getController();

    numericPadStage = new Stage(StageStyle.UTILITY);
    Window owner = field.getScene() == null ? null : field.getScene().getWindow();
    if (owner != null) {
      numericPadStage.initOwner(owner);
    }
    numericPadStage.initModality(Modality.NONE);
    numericPadStage.setAlwaysOnTop(true);
    numericPadStage.setResizable(false);
    numericPadStage.setTitle("Teclado numerico");
    numericPadStage.setScene(new Scene(root));
  }

  private void placeNumericPadNearField(TextField field) {
    if (numericPadStage == null || field.getScene() == null) {
      return;
    }
    Point2D screen = field.localToScreen(0, 0);
    if (screen == null) {
      return;
    }
    numericPadStage.setX(screen.getX() + field.getWidth() + 12);
    numericPadStage.setY(Math.max(20, screen.getY() - 20));
  }

  private void closeNumericPad() {
    if (numericPadStage != null) {
      numericPadStage.hide();
    }
  }

  @FXML
  public void onRefresh() {
    errorLabel.setText("");
    statusLabel.setText("Cargando...");
    detailsLabel.setText("");
    if (fiscalStatusLabel != null) {
      fiscalStatusLabel.setText("Cargando ejercicio fiscal...");
    }
    openBtn.setDisable(true);
    closeBtn.setDisable(true);
    addIncidentBtn.setDisable(true);
    if (resolveOpenTicketsBtn != null) {
      resolveOpenTicketsBtn.setDisable(true);
    }

    refreshFiscalExerciseBlock();

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
    FiscalExerciseResponse before = currentFiscalExercise;
    int currentYear = Year.now().getValue();

    try {
      int openingCents = MoneyUtil.eurosToCents(openingEurosField.getText());
      if (openingCents < 0) {
        errorLabel.setText("El efectivo inicial no puede ser negativo.");
        return;
      }
      String note = openNoteField.getText();

      current = CashApi.open(openingCents, note);
      onRefresh();

      if (currentFiscalExercise != null && "OPEN".equalsIgnoreCase(currentFiscalExercise.status())) {
        if (before == null) {
          showInfoDialog(
              "Ejercicio fiscal",
              "Se ha abierto automaticamente el ejercicio fiscal " + currentFiscalExercise.fiscalYear() + "."
          );
        } else if (!"OPEN".equalsIgnoreCase(before.status())
            && before.fiscalYear() == currentYear
            && currentFiscalExercise.fiscalYear() == currentYear) {
          showInfoDialog(
              "Ejercicio fiscal",
              "Se ha reabierto automaticamente el ejercicio fiscal " + currentYear + "."
          );
        } else if (before.fiscalYear() < currentYear && currentFiscalExercise.fiscalYear() == currentYear) {
          showInfoDialog(
              "Ejercicio fiscal",
              "El ejercicio fiscal " + before.fiscalYear()
                  + " se ha cerrado automaticamente.\n"
                  + "Se ha abierto el nuevo ejercicio fiscal " + currentYear + "."
          );
        }
      }
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

      CashSessionCloseSummaryResponse closeSummary = null;
      try {
        closeSummary = CashApi.closeSummary(current.id());
      } catch (Exception ignored) {
        // Si no se puede precargar resumen, cerramos igual con confirmacion basica.
      }

      if (!confirmClose(closingCents, closeSummary)) {
        String cancelled = "Cierre de caja cancelado.";
        errorLabel.setText(cancelled);
        showInfoDialog("Cerrar caja", cancelled);
        return;
      }

      CashCloseReport report = buildCashCloseReportSnapshot(current.id(), closeSummary);
      current = CashApi.close(current.id(), closingCents, note);
      renderCurrent(current);
      StringBuilder success = new StringBuilder(resolveMessage).append("Caja cerrada correctamente.");

      try {
        String target = printCloseReport(report);
        success.append(" Resumen enviado a ").append(target).append(".");
      } catch (Exception printErr) {
        success.append(" Cierre realizado, pero no se pudo imprimir resumen: ")
            .append(printErr.getMessage());
      }

      String successMsg = success.toString();
      errorLabel.setText(successMsg);
      showInfoDialog("Cerrar caja", successMsg);
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

  private void refreshFiscalExerciseBlock() {
    try {
      currentFiscalExercise = CashApi.currentFiscalExercise();
      String openedBy = currentFiscalExercise.openedBy() == null ? "-" : currentFiscalExercise.openedBy();
      String openedAt = currentFiscalExercise.openedAt() == null ? "-" : DT.format(currentFiscalExercise.openedAt());
      if (fiscalStatusLabel != null) {
        fiscalStatusLabel.setText(
            "Ejercicio " + currentFiscalExercise.fiscalYear()
                + " - " + currentFiscalExercise.status()
                + " (abierto " + openedAt + " por " + openedBy + ")"
        );
      }
    } catch (ApiException e) {
      if (e.getStatus() == 404) {
        currentFiscalExercise = null;
        if (fiscalStatusLabel != null) {
          fiscalStatusLabel.setText("Sin ejercicio fiscal abierto. Se creara automaticamente al abrir caja.");
        }
        return;
      }
      currentFiscalExercise = null;
      if (fiscalStatusLabel != null) {
        fiscalStatusLabel.setText("No se pudo cargar ejercicio fiscal: " + e.getMessage());
      }
    } catch (Exception e) {
      currentFiscalExercise = null;
      if (fiscalStatusLabel != null) {
        fiscalStatusLabel.setText("No se pudo cargar ejercicio fiscal: " + e.getMessage());
      }
    }
  }

  private void renderNoSession() {
    statusLabel.setText("CERRADA / SIN SESION ABIERTA");
    if (currentFiscalExercise == null || !"OPEN".equalsIgnoreCase(currentFiscalExercise.status())) {
      detailsLabel.setText("No hay sesion de caja abierta. Debes abrir caja y se creara/recuperara automaticamente el ejercicio fiscal.");
      openBtn.setDisable(false);
    } else {
      detailsLabel.setText("No hay sesion de caja abierta. Abre caja para empezar a vender.");
      openBtn.setDisable(false);
    }
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

      try {
        List<CashIncidentResponse> incidents = CashApi.listIncidents(cs.id());
        long ticketIncidents = incidents.stream()
            .filter(i -> i.note() != null && i.note().toUpperCase(Locale.ROOT).contains("REOPEN_PAID"))
            .count();
        if (ticketIncidents > 0) {
          sb.append("Incidencias ticket: ").append(ticketIncidents).append("\n");
          incidents.stream()
              .filter(i -> i.note() != null && i.note().toUpperCase(Locale.ROOT).contains("REOPEN_PAID"))
              .limit(5)
              .forEach(i -> sb.append(" - ").append(i.note()).append("\n"));
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

    UiDialogs.info("Tickets pendientes", "Faltan tickets por cobrar\n\n" + detail);
  }

  private void showInfoDialog(String title, String message) {
    UiDialogs.info(title, message);
  }

  private boolean confirmClose(int closingCents, CashSessionCloseSummaryResponse summary) {
    String counted = MoneyUtil.centsToEuros(closingCents) + " EUR";
    String expected = summary == null
        ? (current == null ? "-" : MoneyUtil.centsToEuros(current.expectedCashCents()) + " EUR")
        : MoneyUtil.centsToEuros(summary.expectedCashCents()) + " EUR";
    String diff = summary == null
        ? "-"
        : MoneyUtil.centsToEuros(closingCents - summary.expectedCashCents()) + " EUR";

    String message = "Estas seguro de cerrar caja?\n\n"
        + "Efectivo contado: " + counted + "\n"
        + "Efectivo esperado: " + expected + "\n"
        + "Diferencia: " + diff + "\n\n"
        + "Si confirmas, se cerrara la caja e imprimira resumen de ventas.";
    return UiDialogs.confirm("Cerrar caja", message);
  }

  private CashCloseReport buildCashCloseReportSnapshot(long cashSessionId, CashSessionCloseSummaryResponse closeSummary) {
    List<CashCloseTicketRow> rows = new ArrayList<>();
    Map<String, Integer> totalsByMethod = new LinkedHashMap<>();
    totalsByMethod.put("EFECTIVO", 0);
    totalsByMethod.put("TARJETA", 0);
    totalsByMethod.put("BIZUM", 0);

    try {
      TicketResponse[] all = TicketHistoryApi.listCurrentCashHistory();
      if (all != null) {
        for (TicketResponse ticket : all) {
          if (ticket == null || ticket.status() == null || !"PAID".equalsIgnoreCase(ticket.status())) {
            continue;
          }
          TicketSummaryResponse summary = TicketHistoryApi.summary(ticket.id());
          int ticketTotal = summary != null && summary.totalCents() > 0 ? summary.totalCents() : ticket.totalCents();

          Map<String, Integer> perTicket = new LinkedHashMap<>();
          if (summary != null && summary.payments() != null) {
            for (TicketSummaryResponse.PaymentSummary p : summary.payments()) {
              if (p == null || p.amountCents() == 0) {
                continue;
              }
              String method = normalizePaymentMethod(p.method());
              perTicket.merge(method, p.amountCents(), Integer::sum);
              totalsByMethod.merge(method, p.amountCents(), Integer::sum);
            }
          }
          perTicket.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);

          String methodLabel;
          if (perTicket.isEmpty()) {
            methodLabel = "SIN PAGO";
          } else if (perTicket.size() == 1) {
            methodLabel = perTicket.keySet().iterator().next();
          } else {
            methodLabel = "MIXTO";
          }

          rows.add(new CashCloseTicketRow(
              ticket.id(),
              ticket.tableNumber(),
              ticketTotal,
              methodLabel
          ));
        }
      }
    } catch (Exception ignored) {
      // Si falla el snapshot, imprimimos igualmente cabecera y totales conocidos.
    }

    rows.sort(Comparator.comparingLong(CashCloseTicketRow::ticketId));
    totalsByMethod.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);
    int cash = totalsByMethod.getOrDefault("EFECTIVO", 0);
    int card = totalsByMethod.getOrDefault("TARJETA", 0);
    int bizum = totalsByMethod.getOrDefault("BIZUM", 0);
    int methodsTotal = totalsByMethod.values().stream().mapToInt(Integer::intValue).sum();

    return new CashCloseReport(
        cashSessionId,
        closeSummary == null ? null : closeSummary.closedAt(),
        rows,
        cash,
        card,
        bizum,
        methodsTotal
    );
  }

  private String printCloseReport(CashCloseReport report) {
    String text = buildCloseReportText(report);
    List<String> candidates = new ArrayList<>();
    addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("GENERAL"));
    addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("ALL"));
    addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("BAR"));
    addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("COCINA"));
    addPrinters(candidates, PrinterSettingsStore.resolveSystemPrintersForDestination("POSTRES"));

    Window owner = errorLabel != null && errorLabel.getScene() != null ? errorLabel.getScene().getWindow() : null;
    for (String printer : candidates) {
      try {
        PrintUtil.printTextToPrinterWithBottomMargin(printer, text, owner);
        return printer;
      } catch (Exception ignored) {
        // seguimos probando siguiente impresora
      }
    }

    PrintUtil.printTextToPdfWithBottomMargin(text, owner);
    return "Print to PDF";
  }

  private String buildCloseReportText(CashCloseReport report) {
    StringBuilder out = new StringBuilder();
    String businessName = SettingsStore.getRestaurantName();
    out.append((businessName == null || businessName.isBlank() ? "RESTAURANTE" : businessName).toUpperCase(Locale.ROOT)).append('\n');
    out.append("CIERRE DE CAJA").append('\n');
    out.append("Sesion ").append(report.cashSessionId()).append('\n');
    if (report.closedAt() != null) {
      out.append("Fecha ").append(DT.format(report.closedAt())).append('\n');
    }
    out.append("-".repeat(REPORT_LINE_WIDTH)).append('\n');
    out.append(String.format(Locale.US, "%-8s %-6s %9s %-15s", "TICKET", "MESA", "IMPORTE", "METODO")).append('\n');
    out.append("-".repeat(REPORT_LINE_WIDTH)).append('\n');

    for (CashCloseTicketRow row : report.rows()) {
      String ticketId = String.valueOf(row.ticketId());
      String table = row.tableNumber() == null ? "-" : String.valueOf(row.tableNumber());
      String amount = String.format(Locale.US, "%.2f", row.totalCents() / 100.0);
      String method = clip(row.methodLabel(), 15);
      out.append(String.format(Locale.US, "%-8s %-6s %9s %-15s", ticketId, table, amount, method)).append('\n');
    }

    out.append("-".repeat(REPORT_LINE_WIDTH)).append('\n');
    out.append(amountLine("EFECTIVO", report.cashTotalCents()));
    out.append(amountLine("TARJETA", report.cardTotalCents()));
    out.append(amountLine("BIZUM", report.bizumTotalCents()));
    out.append(amountLine("TOTAL", report.methodsTotalCents()));
    out.append("-".repeat(REPORT_LINE_WIDTH)).append('\n');
    out.append('\n').append('\n').append('\n').append('\n').append('\n');
    return out.toString();
  }

  private static String amountLine(String label, int cents) {
    String amount = String.format(Locale.US, "%.2f", cents / 100.0);
    return String.format(Locale.US, "%-28s %12s%n", label + ":", amount);
  }

  private static String normalizePaymentMethod(String method) {
    if (method == null || method.isBlank()) {
      return "OTRO";
    }
    String m = method.trim().toUpperCase(Locale.ROOT);
    return switch (m) {
      case "CASH", "EFECTIVO" -> "EFECTIVO";
      case "CARD", "TARJETA" -> "TARJETA";
      case "BIZUM" -> "BIZUM";
      default -> m;
    };
  }

  private static String clip(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + ".";
  }

  private static void addPrinters(List<String> target, List<String> source) {
    if (source == null || source.isEmpty()) {
      return;
    }
    for (String printer : source) {
      if (printer == null || printer.isBlank()) {
        continue;
      }
      String normalized = printer.trim();
      if (!target.contains(normalized)) {
        target.add(normalized);
      }
    }
  }

  private record CashCloseTicketRow(long ticketId, Integer tableNumber, int totalCents, String methodLabel) {}

  private record CashCloseReport(
      long cashSessionId,
      java.time.Instant closedAt,
      List<CashCloseTicketRow> rows,
      int cashTotalCents,
      int cardTotalCents,
      int bizumTotalCents,
      int methodsTotalCents
  ) {}
}
