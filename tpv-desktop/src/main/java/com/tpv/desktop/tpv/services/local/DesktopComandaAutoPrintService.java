package com.tpv.desktop.tpv.services.local;

import com.tpv.desktop.api.pos.ComandaApi;
import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.api.pos.SendPreviewResponse;
import com.tpv.desktop.api.pos.TicketApi;
import com.tpv.desktop.api.pos.TicketHistoryApi;
import com.tpv.desktop.api.pos.TicketLineResponse;
import com.tpv.desktop.api.pos.TicketResponse;
import com.tpv.desktop.api.pos.TicketSummaryResponse;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.services.PrintQueueService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Autoimpresion en TPV de eventos originados desde clientes remotos (PDA/Web):
 * - Comandas enviadas
 * - Precuentas solicitadas
 * - Ticket cliente al cerrar cobro
 */
public final class DesktopComandaAutoPrintService implements AutoCloseable {
    private static final long POLL_MS = 2500L;
    private static final long LOCAL_SUPPRESS_MS = 15000L;
    private static final int CLOSED_TICKET_PRINT_MAX_RETRIES = 20;
    private static final long CLOSED_TICKET_PRINT_TTL_MS = 120000L;
    private static final long PAID_PRINT_DEDUP_MS = 120000L;

    private static final int COMANDA_LINE_WIDTH = 42;
    private static final int COMANDA_QTY_COL_WIDTH = 4;
    private static final int COMANDA_DESC_COL_WIDTH = 37;
    private static final String COMANDA_SEPARATOR = "-".repeat(COMANDA_LINE_WIDTH);

    private static final int RECEIPT_LINE_WIDTH = 42;
    private static final int RECEIPT_QTY_COL_WIDTH = 4;
    private static final int RECEIPT_DESC_COL_WIDTH = 21;
    private static final int RECEIPT_UNIT_COL_WIDTH = 7;
    private static final int RECEIPT_TOTAL_COL_WIDTH = 7;
    private static final String RECEIPT_SEPARATOR = "-".repeat(RECEIPT_LINE_WIDTH);

    private static final Map<Long, Long> LOCAL_SEND_SUPPRESS_UNTIL = new ConcurrentHashMap<>();
    private static final Map<Long, Long> LOCAL_PAYMENT_SUPPRESS_UNTIL = new ConcurrentHashMap<>();
    private static final Map<Long, Long> LOCAL_PREBILL_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

    private final PrintQueueService printQueueService;
    private final ScheduledExecutorService worker;

    private final Map<Long, TicketPendingSnapshot> pendingByTicket = new HashMap<>();
    private final Map<Long, Integer> lastPendingCountByTicket = new HashMap<>();
    private final Map<Long, Instant> lastFallbackPrintedAtByTicket = new HashMap<>();
    private final Map<Long, Boolean> lastBillRequestedByTicket = new HashMap<>();
    private final Map<Long, TableCtx> tableCtxByTicket = new HashMap<>();
    private final Map<Long, ClosedTicketRetry> pendingClosedTicketPrints = new HashMap<>();
    private final Map<Long, Long> paidPrintedAtMsByTicket = new HashMap<>();
    private Set<Long> previousOpenTickets = new HashSet<>();

    public DesktopComandaAutoPrintService(PrintQueueService printQueueService) {
        this.printQueueService = printQueueService;
        this.worker = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "tpv-comanda-autoprint");
                t.setDaemon(true);
                return t;
            }
        });
        this.worker.scheduleWithFixedDelay(this::pollSafely, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
    }

    public static void markLocalSend(long ticketId) {
        markSuppress(LOCAL_SEND_SUPPRESS_UNTIL, ticketId);
    }

    public static void markLocalPayment(long ticketId) {
        markSuppress(LOCAL_PAYMENT_SUPPRESS_UNTIL, ticketId);
    }

    public static void markLocalPrebillRequest(long ticketId) {
        markSuppress(LOCAL_PREBILL_SUPPRESS_UNTIL, ticketId);
    }

    @Override
    public void close() {
        worker.shutdownNow();
    }

    private static void markSuppress(Map<Long, Long> bucket, long ticketId) {
        if (ticketId <= 0) {
            return;
        }
        bucket.put(ticketId, System.currentTimeMillis() + LOCAL_SUPPRESS_MS);
    }

    private void pollSafely() {
        try {
            poll();
        } catch (Exception ignored) {
            // Best-effort: no bloquear UX.
        }
    }

    private void poll() throws Exception {
        SalonTableResponse[] tables = SalonApi.tables();
        if (tables == null) {
            return;
        }

        Set<Long> openTickets = new HashSet<>();
        Map<Long, TableCtx> currentCtx = new HashMap<>();
        for (SalonTableResponse t : tables) {
            if (t == null || t.ticketId() == null) {
                continue;
            }
            long ticketId = t.ticketId();
            openTickets.add(ticketId);
            TableCtx ctx = new TableCtx(ticketId, t.tableNumber(), safe(t.salonName()));
            currentCtx.put(ticketId, ctx);
            tableCtxByTicket.put(ticketId, ctx);

            processComandaSend(ticketId, t, ctx);
            processPrebillRequest(ticketId, ctx);
            processInPlacePaidTicket(ticketId, ctx);
        }

        Set<Long> closedTickets = new HashSet<>(previousOpenTickets);
        closedTickets.removeAll(openTickets);
        for (Long ticketId : closedTickets) {
            queueClosedTicketPrint(ticketId);
        }
        processPendingClosedTicketPrints();
        previousOpenTickets = openTickets;

        cleanupStateForClosed(openTickets);
        cleanupSuppressions();
    }

    private void processComandaSend(long ticketId, SalonTableResponse t, TableCtx ctx) {
        int currentPending = Math.max(0, t.pendingLines());
        int previousPending = lastPendingCountByTicket.getOrDefault(ticketId, currentPending);
        lastPendingCountByTicket.put(ticketId, currentPending);

        if (currentPending > 0) {
            try {
                SendPreviewResponse preview = ComandaApi.preview(ticketId);
                List<TicketLineResponse> pending = preview == null || preview.pendingLines() == null
                        ? List.of()
                        : preview.pendingLines();
                if (!pending.isEmpty()) {
                    pendingByTicket.put(ticketId, new TicketPendingSnapshot(ticketId, copyOf(pending)));
                }
            } catch (Exception ignored) {
                // Si falla preview, esperamos siguiente ciclo.
            }
            return;
        }

        TicketPendingSnapshot snapshot = pendingByTicket.remove(ticketId);
        if (snapshot == null || snapshot.lines().isEmpty()) {
            if (previousPending > 0 && !isSuppressed(LOCAL_SEND_SUPPRESS_UNTIL, ticketId)) {
                enqueueFallbackFromRecentSentLines(ticketId, ctx);
            }
            return;
        }
        if (isSuppressed(LOCAL_SEND_SUPPRESS_UNTIL, ticketId)) {
            return;
        }
        enqueueSnapshotForPrint(snapshot, ctx);
    }

    private void processPrebillRequest(long ticketId, TableCtx ctx) {
        try {
            TicketResponse ticket = TicketApi.getById(ticketId);
            if (ticket == null) {
                return;
            }
            boolean requested = ticket.billRequested();
            lastBillRequestedByTicket.put(ticketId, requested);
            if (!requested) {
                return;
            }
            if (isSuppressed(LOCAL_PREBILL_SUPPRESS_UNTIL, ticketId)) {
                return;
            }

            String prebill = buildPrebillText(ticket, ctx);
            printQueueService.enqueue("GENERAL", prebill);
            try {
                TicketApi.setBillRequested(ticketId, false);
            } catch (Exception ignored) {
                // Si no se puede limpiar bandera, evitamos repetir por cache local.
            }
            lastBillRequestedByTicket.put(ticketId, false);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void processClosedTicketPrint(long ticketId) {
        ClosedTicketRetry retry = pendingClosedTicketPrints.get(ticketId);
        if (retry == null) {
            return;
        }
        if (wasPaidPrintedRecently(ticketId)) {
            pendingClosedTicketPrints.remove(ticketId);
            return;
        }
        if (isSuppressed(LOCAL_PAYMENT_SUPPRESS_UNTIL, ticketId)) {
            pendingClosedTicketPrints.remove(ticketId);
            return;
        }
        if (retry.attempts() >= CLOSED_TICKET_PRINT_MAX_RETRIES) {
            pendingClosedTicketPrints.remove(ticketId);
            return;
        }
        long ageMs = System.currentTimeMillis() - retry.createdAtMs();
        if (ageMs > CLOSED_TICKET_PRINT_TTL_MS) {
            pendingClosedTicketPrints.remove(ticketId);
            return;
        }
        try {
            TicketSummaryResponse summary = TicketHistoryApi.summary(ticketId);
            if (summary == null) {
                pendingClosedTicketPrints.put(ticketId, retry.nextAttempt());
                return;
            }
            if (summary.totalCents() <= 0 || summary.paidCents() <= 0 || summary.remainingCents() > 0) {
                pendingClosedTicketPrints.remove(ticketId);
                return;
            }
            TableCtx ctx = retry.tableCtx() == null ? tableCtxByTicket.get(ticketId) : retry.tableCtx();
            String paidTicket = buildPaidTicketText(summary, ctx);
            printQueueService.enqueue("GENERAL", paidTicket);
            markPaidPrinted(ticketId);
            pendingClosedTicketPrints.remove(ticketId);
        } catch (Exception ignored) {
            pendingClosedTicketPrints.put(ticketId, retry.nextAttempt());
        }
    }

    private void processInPlacePaidTicket(long ticketId, TableCtx ctx) {
        if (ticketId <= 0 || wasPaidPrintedRecently(ticketId) || isSuppressed(LOCAL_PAYMENT_SUPPRESS_UNTIL, ticketId)) {
            return;
        }
        try {
            TicketResponse ticket = TicketApi.getById(ticketId);
            if (ticket == null || ticket.status() == null || !ticket.status().equalsIgnoreCase("PAID")) {
                return;
            }
            TicketSummaryResponse summary = TicketHistoryApi.summary(ticketId);
            if (summary == null || summary.totalCents() <= 0 || summary.paidCents() <= 0 || summary.remainingCents() > 0) {
                return;
            }
            printQueueService.enqueue("GENERAL", buildPaidTicketText(summary, ctx));
            markPaidPrinted(ticketId);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void queueClosedTicketPrint(long ticketId) {
        if (ticketId <= 0 || isSuppressed(LOCAL_PAYMENT_SUPPRESS_UNTIL, ticketId)) {
            return;
        }
        pendingClosedTicketPrints.computeIfAbsent(
                ticketId,
                id -> new ClosedTicketRetry(id, tableCtxByTicket.get(id), 0, System.currentTimeMillis())
        );
    }

    private void processPendingClosedTicketPrints() {
        if (pendingClosedTicketPrints.isEmpty()) {
            return;
        }
        List<Long> ticketIds = new ArrayList<>(pendingClosedTicketPrints.keySet());
        for (Long ticketId : ticketIds) {
            processClosedTicketPrint(ticketId);
        }
    }

    private void enqueueFallbackFromRecentSentLines(long ticketId, TableCtx ctx) {
        try {
            TicketResponse ticket = TicketApi.getById(ticketId);
            if (ticket == null || ticket.lines() == null || ticket.lines().isEmpty()) {
                return;
            }
            Instant now = Instant.now();
            Instant recentFloor = now.minusSeconds(20);
            Instant lastPrinted = lastFallbackPrintedAtByTicket.getOrDefault(ticketId, Instant.EPOCH);
            List<TicketLineResponse> recent = ticket.lines().stream()
                    .filter(TicketLineResponse::sent)
                    .filter(line -> line.updatedAt() != null)
                    .filter(line -> line.updatedAt().isAfter(recentFloor))
                    .filter(line -> line.updatedAt().isAfter(lastPrinted))
                    .toList();
            if (recent.isEmpty()) {
                return;
            }
            enqueueSnapshotForPrint(new TicketPendingSnapshot(ticketId, recent), ctx);
            Instant maxPrinted = recent.stream()
                    .map(TicketLineResponse::updatedAt)
                    .max(Instant::compareTo)
                    .orElse(now);
            lastFallbackPrintedAtByTicket.put(ticketId, maxPrinted);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private void enqueueSnapshotForPrint(TicketPendingSnapshot snapshot, TableCtx table) {
        Map<DestinationKey, List<TicketLineResponse>> grouped = new LinkedHashMap<>();
        for (TicketLineResponse line : snapshot.lines()) {
            DestinationKey key = DestinationKey.from(line.destination());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(line);
        }
        for (Map.Entry<DestinationKey, List<TicketLineResponse>> entry : grouped.entrySet()) {
            String payload = buildComandaPayload(snapshot.ticketId(), table, entry.getKey(), entry.getValue());
            printQueueService.enqueue(entry.getKey().name(), payload);
        }
    }

    private String buildComandaPayload(long ticketId, TableCtx table, DestinationKey destination, List<TicketLineResponse> lines) {
        StringBuilder out = new StringBuilder();
        out.append(restaurantNameForPrint()).append('\n');
        out.append("Mesa ").append(table.tableNumber());
        if (!table.salonName().isBlank()) {
            out.append(" (").append(table.salonName()).append(")");
        }
        out.append("  Ticket ").append(ticketId).append('\n');
        out.append("Fecha ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append('\n');
        out.append(COMANDA_SEPARATOR).append('\n');
        out.append("DESTINO ").append(destination.name()).append('\n');
        int qty = lines.stream().mapToInt(line -> Math.max(1, line.qty())).sum();
        out.append("PRODUCTOS ").append(qty).append('\n');
        out.append(COMANDA_SEPARATOR).append('\n');
        out.append(padRight("CANT", COMANDA_QTY_COL_WIDTH)).append(' ')
                .append(padRight("DESCRIPCION", COMANDA_DESC_COL_WIDTH)).append('\n');
        out.append(COMANDA_SEPARATOR).append('\n');
        for (TicketLineResponse line : lines) {
            appendComandaLine(out, Math.max(1, line.qty()), safe(line.productName()));
        }
        out.append(COMANDA_SEPARATOR).append('\n');
        return out.toString();
    }

    private String buildPrebillText(TicketResponse ticket, TableCtx ctx) {
        StringBuilder out = new StringBuilder();
        appendBusinessHeader(out, "PRECUENTA");
        appendWrappedLine(out, tableLabelForTicket(ticket, ctx) + " Ticket " + ticket.id());
        appendWrappedLine(out, "Fecha " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        out.append(RECEIPT_SEPARATOR).append('\n');
        out.append(formatReceiptColumns("CANT", "DESCRIPCION", "PRECIO", "IMPORTE")).append('\n');
        out.append(RECEIPT_SEPARATOR).append('\n');
        if (ticket.lines() != null) {
            for (TicketLineResponse line : ticket.lines()) {
                appendReceiptLineWithAmounts(
                        out,
                        Math.max(1, line.qty()),
                        safe(line.productName()),
                        Math.max(0, line.unitPriceCents()),
                        Math.max(0, line.lineTotalCents())
                );
            }
        }
        out.append(RECEIPT_SEPARATOR).append('\n');
        appendReceiptAmountLine(out, "TOTAL", Math.max(0, ticket.totalCents()));
        out.append("IVA INCLUIDO").append('\n');
        out.append(RECEIPT_SEPARATOR).append('\n');
        out.append("Gracias. Esta pre-cuenta no es factura.").append('\n');
        return out.toString();
    }

    private String buildPaidTicketText(TicketSummaryResponse summary, TableCtx ctx) {
        StringBuilder out = new StringBuilder();
        appendBusinessHeader(out, "TICKET CLIENTE");
        appendWrappedLine(out, tableLabelForTicket(summary, ctx) + " Ticket " + summary.id());
        appendWrappedLine(out, "Fecha " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        out.append(RECEIPT_SEPARATOR).append('\n');
        out.append(formatReceiptColumns("CANT", "DESCRIPCION", "PRECIO", "IMPORTE")).append('\n');
        out.append(RECEIPT_SEPARATOR).append('\n');
        if (summary.lines() != null) {
            for (TicketSummaryResponse.TicketLineSummary line : summary.lines()) {
                appendReceiptLineWithAmounts(
                        out,
                        Math.max(1, line.qty()),
                        safe(line.productName()),
                        Math.max(0, line.unitPriceCents()),
                        Math.max(0, line.lineTotalCents())
                );
            }
        }
        out.append(RECEIPT_SEPARATOR).append('\n');
        appendReceiptAmountLine(out, "TOTAL", Math.max(0, summary.totalCents()));
        out.append("IVA INCLUIDO").append('\n');
        appendReceiptAmountLine(out, "PAGADO", Math.max(0, summary.paidCents()));
        Map<String, Integer> paymentBreakdown = paymentBreakdown(summary);
        if (paymentBreakdown.isEmpty()) {
            appendReceiptFieldLine(out, "METODO", "OTRO");
        } else {
            for (Map.Entry<String, Integer> e : paymentBreakdown.entrySet()) {
                appendReceiptAmountLine(out, e.getKey(), e.getValue());
            }
        }
        out.append(RECEIPT_SEPARATOR).append('\n');
        out.append("Gracias por su visita.").append('\n');
        return out.toString();
    }

    private static Map<String, Integer> paymentBreakdown(TicketSummaryResponse summary) {
        LinkedHashMap<String, Integer> breakdown = new LinkedHashMap<>();
        if (summary.payments() == null) {
            return breakdown;
        }
        for (TicketSummaryResponse.PaymentSummary p : summary.payments()) {
            if (p == null || p.amountCents() <= 0) {
                continue;
            }
            String method = normalizePaymentMethod(p.method());
            breakdown.merge(method, p.amountCents(), Integer::sum);
        }
        return breakdown;
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

    private static String tableLabelForTicket(TicketResponse ticket, TableCtx ctx) {
        int tableNumber = ticket.tableNumber() == null ? (ctx == null ? 0 : ctx.tableNumber()) : ticket.tableNumber();
        if (ctx != null && !ctx.salonName().isBlank()) {
            return ctx.salonName() + " - Mesa " + tableNumber;
        }
        return "Mesa " + tableNumber;
    }

    private static String tableLabelForTicket(TicketSummaryResponse summary, TableCtx ctx) {
        if (ctx != null && !ctx.salonName().isBlank()) {
            return ctx.salonName() + " - Mesa " + ctx.tableNumber();
        }
        return "Mesa " + (ctx == null ? "-" : ctx.tableNumber());
    }

    private static void appendBusinessHeader(StringBuilder out, String documentTitle) {
        String restaurantName = AppStateHolder.restaurantName();
        if (restaurantName == null || restaurantName.isBlank()) {
            restaurantName = SettingsStore.getRestaurantName();
        }
        String taxId = SettingsStore.getFiscalTaxId();
        String fiscalAddress = SettingsStore.getFiscalAddress();
        String fiscalPostalCode = SettingsStore.getFiscalPostalCode();
        String fiscalCity = SettingsStore.getFiscalCity();
        String fiscalProvince = SettingsStore.getFiscalProvince();
        String fiscalCountry = SettingsStore.getFiscalCountry();

        String headerName = (restaurantName == null || restaurantName.isBlank()) ? "RESTAURANTE" : restaurantName;
        out.append(headerName.toUpperCase(Locale.ROOT)).append('\n');
        if (taxId != null && !taxId.isBlank()) {
            out.append("NIF/CIF ").append(taxId).append('\n');
        }
        if ((fiscalAddress != null && !fiscalAddress.isBlank())
                || (fiscalPostalCode != null && !fiscalPostalCode.isBlank())
                || (fiscalCity != null && !fiscalCity.isBlank())) {
            if (fiscalAddress != null && !fiscalAddress.isBlank()) {
                out.append(clip(fiscalAddress, RECEIPT_LINE_WIDTH)).append('\n');
            }
            String cityLine = String.format(
                    Locale.ROOT,
                    "%s %s %s",
                    fiscalPostalCode == null ? "" : fiscalPostalCode.trim(),
                    fiscalCity == null ? "" : fiscalCity.trim(),
                    fiscalProvince == null ? "" : fiscalProvince.trim()
            ).trim();
            if (!cityLine.isBlank()) {
                out.append(clip(cityLine, RECEIPT_LINE_WIDTH)).append('\n');
            }
            if (fiscalCountry != null && !fiscalCountry.isBlank()) {
                out.append(fiscalCountry.trim().toUpperCase(Locale.ROOT)).append('\n');
            }
        }
        out.append(documentTitle).append('\n');
    }

    private static void appendReceiptLineWithAmounts(StringBuilder out, int qty, String productName, int unitPriceCents, int lineTotalCents) {
        String qtyText = Math.max(1, qty) + "x";
        String unitText = String.format(Locale.US, "%.2f", unitPriceCents / 100.0);
        String totalText = String.format(Locale.US, "%.2f", lineTotalCents / 100.0);
        String safeName = productName == null || productName.isBlank() ? "-" : productName.trim();

        List<String> wrapped = wrapByWords(safeName, RECEIPT_DESC_COL_WIDTH);
        String firstName = wrapped.isEmpty() ? "-" : wrapped.getFirst();
        out.append(formatReceiptColumns(qtyText, firstName, unitText, totalText)).append('\n');

        for (int i = 1; i < wrapped.size(); i++) {
            out.append(formatReceiptColumns("", wrapped.get(i), "", "")).append('\n');
        }
    }

    private static String formatReceiptColumns(String qty, String description, String unitPrice, String amount) {
        return padRight(trimToWidth(qty, RECEIPT_QTY_COL_WIDTH), RECEIPT_QTY_COL_WIDTH)
                + ' '
                + padRight(trimToWidth(description, RECEIPT_DESC_COL_WIDTH), RECEIPT_DESC_COL_WIDTH)
                + ' '
                + padLeft(trimToWidth(unitPrice, RECEIPT_UNIT_COL_WIDTH), RECEIPT_UNIT_COL_WIDTH)
                + ' '
                + padLeft(trimToWidth(amount, RECEIPT_TOTAL_COL_WIDTH), RECEIPT_TOTAL_COL_WIDTH);
    }

    private static void appendReceiptAmountLine(StringBuilder out, String label, int amountCents) {
        appendReceiptFieldLine(out, label, String.format(Locale.US, "%.2f", amountCents / 100.0));
    }

    private static void appendReceiptFieldLine(StringBuilder out, String label, String value) {
        String safeLabel = (label == null ? "" : label.trim()) + ":";
        String safeValue = value == null || value.isBlank() ? "-" : value.trim();
        int maxLeft = Math.max(4, RECEIPT_LINE_WIDTH - safeValue.length() - 1);
        String left = safeLabel.length() <= maxLeft ? safeLabel : safeLabel.substring(0, maxLeft);
        int gap = Math.max(1, RECEIPT_LINE_WIDTH - left.length() - safeValue.length());
        out.append(left).append(" ".repeat(gap)).append(safeValue).append('\n');
    }

    private static void appendWrappedLine(StringBuilder out, String text) {
        for (String part : wrapByWords(text, RECEIPT_LINE_WIDTH)) {
            out.append(part).append('\n');
        }
    }

    private static void appendComandaLine(StringBuilder out, int qty, String productName) {
        String qtyCell = qty + "x";
        List<String> wrapped = wrapByWords(productName, COMANDA_DESC_COL_WIDTH);
        if (wrapped.isEmpty()) {
            wrapped = List.of("-");
        }
        out.append(padRight(qtyCell, COMANDA_QTY_COL_WIDTH))
                .append(' ')
                .append(padRight(wrapped.getFirst(), COMANDA_DESC_COL_WIDTH))
                .append('\n');
        for (int i = 1; i < wrapped.size(); i++) {
            out.append(padRight("", COMANDA_QTY_COL_WIDTH))
                    .append(' ')
                    .append(padRight(wrapped.get(i), COMANDA_DESC_COL_WIDTH))
                    .append('\n');
        }
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
        return safe.length() <= width ? safe : safe.substring(0, width);
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

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + ".";
    }

    private static String restaurantNameForPrint() {
        String fromState = AppStateHolder.restaurantName();
        if (!fromState.isBlank()) {
            return fromState;
        }
        String fromSettings = SettingsStore.getRestaurantName();
        return fromSettings == null || fromSettings.isBlank() ? "RESTAURANTE" : fromSettings.toUpperCase(Locale.ROOT);
    }

    private static List<TicketLineResponse> copyOf(List<TicketLineResponse> pending) {
        List<TicketLineResponse> out = new ArrayList<>(pending.size());
        out.addAll(pending);
        return out;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isSuppressed(Map<Long, Long> bucket, long ticketId) {
        Long until = bucket.get(ticketId);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() <= until) {
            return true;
        }
        bucket.remove(ticketId);
        return false;
    }

    private void cleanupSuppressions() {
        long now = System.currentTimeMillis();
        LOCAL_SEND_SUPPRESS_UNTIL.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
        LOCAL_PAYMENT_SUPPRESS_UNTIL.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
        LOCAL_PREBILL_SUPPRESS_UNTIL.entrySet().removeIf(e -> e.getValue() == null || e.getValue() < now);
    }

    private void cleanupStateForClosed(Set<Long> openTickets) {
        pendingByTicket.keySet().removeIf(ticketId -> !openTickets.contains(ticketId));
        lastPendingCountByTicket.keySet().removeIf(ticketId -> !openTickets.contains(ticketId));
        lastFallbackPrintedAtByTicket.keySet().removeIf(ticketId -> !openTickets.contains(ticketId));
        lastBillRequestedByTicket.keySet().removeIf(ticketId -> !openTickets.contains(ticketId));
        tableCtxByTicket.keySet().removeIf(ticketId -> !openTickets.contains(ticketId));
        pendingClosedTicketPrints.entrySet().removeIf(entry -> {
            ClosedTicketRetry retry = entry.getValue();
            if (retry == null) {
                return true;
            }
            if (isSuppressed(LOCAL_PAYMENT_SUPPRESS_UNTIL, entry.getKey())) {
                return true;
            }
            long ageMs = System.currentTimeMillis() - retry.createdAtMs();
            return ageMs > CLOSED_TICKET_PRINT_TTL_MS || retry.attempts() >= CLOSED_TICKET_PRINT_MAX_RETRIES;
        });
        paidPrintedAtMsByTicket.entrySet().removeIf(entry -> {
            Long at = entry.getValue();
            if (at == null) {
                return true;
            }
            return (System.currentTimeMillis() - at) > PAID_PRINT_DEDUP_MS;
        });
    }

    private boolean wasPaidPrintedRecently(long ticketId) {
        Long at = paidPrintedAtMsByTicket.get(ticketId);
        if (at == null) {
            return false;
        }
        if ((System.currentTimeMillis() - at) <= PAID_PRINT_DEDUP_MS) {
            return true;
        }
        paidPrintedAtMsByTicket.remove(ticketId);
        return false;
    }

    private void markPaidPrinted(long ticketId) {
        if (ticketId <= 0) {
            return;
        }
        paidPrintedAtMsByTicket.put(ticketId, System.currentTimeMillis());
    }

    private record TicketPendingSnapshot(long ticketId, List<TicketLineResponse> lines) {
    }

    private record TableCtx(long ticketId, int tableNumber, String salonName) {
    }

    private record ClosedTicketRetry(long ticketId, TableCtx tableCtx, int attempts, long createdAtMs) {
        ClosedTicketRetry nextAttempt() {
            return new ClosedTicketRetry(ticketId, tableCtx, attempts + 1, createdAtMs);
        }
    }

    private enum DestinationKey {
        BAR,
        COCINA,
        POSTRES,
        ALL;

        static DestinationKey from(String raw) {
            if (raw == null || raw.isBlank()) {
                return COCINA;
            }
            try {
                return DestinationKey.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return COCINA;
            }
        }
    }

    private static final class AppStateHolder {
        static String restaurantName() {
            try {
                return com.tpv.desktop.tpv.app.AppContext.get().appState().restaurantNameProperty().get();
            } catch (Exception ignored) {
                return "";
            }
        }
    }
}
