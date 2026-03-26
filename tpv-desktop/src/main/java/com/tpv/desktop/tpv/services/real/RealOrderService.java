package com.tpv.desktop.tpv.services.real;

import com.tpv.desktop.api.ApiClient.ApiException;
import com.tpv.desktop.api.pos.ComandaApi;
import com.tpv.desktop.api.pos.PaymentApi;
import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.api.pos.SendPreviewResponse;
import com.tpv.desktop.api.pos.TicketApi;
import com.tpv.desktop.api.pos.TicketLineResponse;
import com.tpv.desktop.api.pos.TicketResponse;
import com.tpv.desktop.tpv.domain.model.Destination;
import com.tpv.desktop.tpv.domain.model.Order;
import com.tpv.desktop.tpv.domain.model.OrderLine;
import com.tpv.desktop.tpv.domain.model.Product;
import com.tpv.desktop.tpv.services.CatalogService;
import com.tpv.desktop.tpv.services.OrderService;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RealOrderService implements OrderService {
    private final CatalogService catalogService;
    private final Map<Long, String> localNotes = new java.util.concurrent.ConcurrentHashMap<>();

    public RealOrderService(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @Override
    public Order openOrGetByTable(int tableId) {
        try {
            return toDomain(SalonApi.openTicket(tableId));
        } catch (ApiException ex) {
            Long existing = tryResolveExistingTicketId(ex, tableId);
            if (existing != null) {
                return getById(existing);
            }
            throw new RuntimeException("No se pudo abrir ticket mesa " + tableId + ": " + ex.getMessage(), ex);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo abrir ticket mesa " + tableId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Order getById(long orderId) {
        try {
            return toDomain(TicketApi.getById(orderId));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar ticket " + orderId + ": " + e.getMessage(), e);
        }
    }

    @Override
    public Order addProduct(long orderId, long productId) {
        return addProduct(orderId, productId, 1);
    }

    @Override
    public Order addProduct(long orderId, long productId, int qty) {
        int safeQty = qty <= 0 ? 1 : qty;
        try {
            return toDomain(TicketApi.addLine(orderId, productId, safeQty));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo anadir producto: " + e.getMessage(), e);
        }
    }

    @Override
    public Order addCombinedProduct(long orderId, long baseProductId, long mixerProductId, int qty) {
        int safeQty = qty <= 0 ? 1 : qty;
        try {
            return toDomain(TicketApi.addComboLine(orderId, baseProductId, mixerProductId, safeQty));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo anadir combinado: " + e.getMessage(), e);
        }
    }

    @Override
    public Order updateLineQty(long orderId, long lineId, int qty) {
        int safeQty = qty <= 0 ? 1 : qty;
        try {
            return toDomain(TicketApi.updateQty(orderId, lineId, safeQty));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo actualizar cantidad: " + e.getMessage(), e);
        }
    }

    @Override
    public Order updateLinePrice(long orderId, long lineId, int priceCents) {
        int safePrice = Math.max(0, priceCents);
        try {
            return toDomain(TicketApi.updatePrice(orderId, lineId, safePrice));
        } catch (Exception e) {
            throw new RuntimeException("No se pudo actualizar precio: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeLine(long orderId, long lineId) {
        try {
            TicketApi.deleteLine(orderId, lineId);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo borrar linea: " + e.getMessage(), e);
        }
    }

    @Override
    public void removeLastPendingLine(long orderId) {
        Order order = getById(orderId);
        TicketLineResponse target = null;
        for (OrderLine line : order.getLines()) {
            if (line.getPendingQty() > 0) {
                target = new TicketLineResponse(
                        line.getId(),
                        line.getProductId(),
                        line.getProductName(),
                        null,
                        line.getDestination().name(),
                        line.getPendingQty() == 0,
                        line.getUnitPriceCents(),
                        line.getQty(),
                        line.lineTotalCents(),
                        null,
                        null
                );
            }
        }
        if (target == null) return;
        try {
            if (target.qty() > 1) {
                TicketApi.updateQty(orderId, target.id(), target.qty() - 1);
            } else {
                TicketApi.deleteLine(orderId, target.id());
            }
        } catch (Exception e) {
            throw new RuntimeException("No se pudo borrar linea pendiente: " + e.getMessage(), e);
        }
    }

    @Override
    public void setLastLineNote(long orderId, String note) {
        Order order = getById(orderId);
        for (int i = order.getLines().size() - 1; i >= 0; i--) {
            OrderLine line = order.getLines().get(i);
            if (line.getPendingQty() > 0) {
                localNotes.put(line.getId(), note == null ? "" : note.trim());
                return;
            }
        }
    }

    @Override
    public Map<Destination, Integer> pendingByDestination(long orderId) {
        try {
            SendPreviewResponse preview = ComandaApi.preview(orderId);
            Map<Destination, Integer> map = new EnumMap<>(Destination.class);
            for (Destination d : Destination.values()) map.put(d, 0);
            if (preview.pendingLines() != null) {
                for (TicketLineResponse line : preview.pendingLines()) {
                    Destination d = destinationFromString(line.destination());
                    map.compute(d, (k, v) -> (v == null ? 0 : v) + Math.max(1, line.qty()));
                }
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException("No se pudo calcular pendientes por destino: " + e.getMessage(), e);
        }
    }

    @Override
    public int pendingPaymentCents(long orderId) {
        try {
            return Math.max(0, TicketApi.paymentSummary(orderId).pendingCents());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo calcular pendiente de cobro: " + e.getMessage(), e);
        }
    }

    @Override
    public void addPayment(long orderId, String method, int amountCents) {
        try {
            PaymentApi.addPayment(orderId, method, amountCents, UUID.randomUUID().toString());
        } catch (Exception e) {
            throw new RuntimeException("No se pudo registrar el cobro: " + e.getMessage(), e);
        }
    }

    @Override
    public void send(long orderId, Set<Destination> destinations, boolean deltaOnly) {
        try {
            String target = resolveDestinationTarget(destinations);
            ComandaApi.send(orderId, target);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo enviar comanda: " + e.getMessage(), e);
        }
    }

    @Override
    public void setBillRequested(long orderId, boolean value) {
        try {
            TicketApi.setBillRequested(orderId, value);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo actualizar cuenta solicitada: " + e.getMessage(), e);
        }
    }

    @Override
    public void applyDiscountPercent(long orderId, int percent) {
        try {
            TicketApi.applyDiscount(orderId, percent, null);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo aplicar descuento %: " + e.getMessage(), e);
        }
    }

    @Override
    public void applyDiscountAmount(long orderId, int amountCents) {
        try {
            TicketApi.applyDiscount(orderId, null, amountCents);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo aplicar descuento: " + e.getMessage(), e);
        }
    }

    @Override
    public void clearDiscount(long orderId) {
        try {
            TicketApi.applyDiscount(orderId, null, 0);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo quitar descuento: " + e.getMessage(), e);
        }
    }

    @Override
    public void cancelOrder(long orderId) {
        try {
            TicketApi.cancel(orderId);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo anular ticket: " + e.getMessage(), e);
        }
    }

    @Override
    public void moveOrder(long orderId, int newTableId) {
        try {
            TicketApi.moveTable(orderId, newTableId);
        } catch (Exception e) {
            throw new RuntimeException("No se pudo mover ticket a mesa " + newTableId + ": " + e.getMessage(), e);
        }
    }

    private Order toDomain(TicketResponse t) {
        int table = t.tableNumber() == null ? 0 : t.tableNumber();
        Order out = new Order(t.id(), table, 4, t.createdAt());
        out.setBillRequested(t.billRequested());
        if (t.lines() != null) {
            for (TicketLineResponse line : t.lines()) {
                Product p = catalogService.productById(line.productId());
                OrderLine ol = new OrderLine(line.id(), p, line.qty());
                if (line.productName() != null && !line.productName().isBlank()) {
                    ol.setProductName(line.productName());
                }
                ol.setUnitPriceCents(line.unitPriceCents());
                ol.setDestination(destinationFromString(line.destination()));
                if (line.sent()) {
                    ol.markSentAll();
                }
                String note = line.note();
                if (note == null || note.isBlank()) {
                    note = localNotes.get(line.id());
                }
                if (note != null && !note.isBlank()) {
                    ol.setNote(note);
                }
                out.getLines().add(ol);
            }
        }
        return out;
    }

    private static String resolveDestinationTarget(Set<Destination> destinations) {
        if (destinations == null || destinations.isEmpty() || destinations.size() > 1) return "ALL";
        Destination d = destinations.iterator().next();
        return switch (d) {
            case BAR -> "BAR";
            case COCINA -> "COCINA";
            case POSTRES -> "POSTRES";
        };
    }

    private static Destination destinationFromString(String raw) {
        if (raw == null) return Destination.COCINA;
        return switch (raw.toUpperCase()) {
            case "BAR" -> Destination.BAR;
            case "POSTRES" -> Destination.POSTRES;
            default -> Destination.COCINA;
        };
    }

    private static Long tryResolveExistingTicketId(ApiException ex, int tableId) {
        if (ex.getStatus() != 409) return null;
        Long parsed = parseTicketIdFromConflict(ex.getBody());
        if (parsed != null) return parsed;
        try {
            SalonTableResponse[] tables = SalonApi.tables();
            for (SalonTableResponse t : tables) {
                if (t.tableNumber() == tableId && t.ticketId() != null) {
                    return t.ticketId();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static Long parseTicketIdFromConflict(String body) {
        if (body == null || body.isBlank()) return null;
        Matcher m = Pattern.compile("OPEN ticket:\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(body);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
