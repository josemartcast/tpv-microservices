package com.tpv.desktop.tpv.services.real;

import com.tpv.desktop.api.pos.SalonApi;
import com.tpv.desktop.api.pos.SalonTableResponse;
import com.tpv.desktop.core.SettingsStore;
import com.tpv.desktop.tpv.domain.model.TableSnapshot;
import com.tpv.desktop.tpv.domain.model.TableStatus;
import com.tpv.desktop.tpv.services.TableService;

import java.util.Arrays;
import java.util.List;

public class RealTableService implements TableService {

    @Override
    public List<TableSnapshot> tables() {
        try {
            String terminalId = SettingsStore.getTerminalId();
            SalonTableResponse[] response = SalonApi.tables();
            return Arrays.stream(response).map(t -> toDomain(t, terminalId)).toList();
        } catch (Exception e) {
            throw new RuntimeException("No se pudo cargar mesas: " + e.getMessage(), e);
        }
    }

    private static TableSnapshot toDomain(SalonTableResponse t, String terminalId) {
        TableStatus status = mapStatus(t.status(), t.lockedTerminalId(), terminalId, t.pendingLines(), t.ticketId());
        String salonName = t.salonName() == null || t.salonName().isBlank() ? "Salon" : t.salonName().trim();
        String alias = t.tableAlias() == null ? "" : t.tableAlias().trim();
        String label = salonName + " - Mesa " + t.tableNumber();
        if (!alias.isBlank()) {
            label = label + " \"" + alias + "\"";
        }
        return new TableSnapshot(
                t.tableNumber(),
                salonName,
                label,
                status,
                t.totalCents(),
                t.elapsedMinutes(),
                t.pendingLines(),
                false,
                t.lockedBy(),
                t.lockedTerminalId(),
                t.ticketId() == null ? 0 : t.ticketId()
        );
    }

    private static TableStatus mapStatus(String apiStatus, String lockedTerminalId, String currentTerminal, int pending, Long ticketId) {
        String status = apiStatus == null ? "FREE" : apiStatus.toUpperCase();
        boolean lockByOther = lockedTerminalId != null && !lockedTerminalId.isBlank() && !lockedTerminalId.equalsIgnoreCase(currentTerminal);
        boolean lockByMe = lockedTerminalId != null && lockedTerminalId.equalsIgnoreCase(currentTerminal);

        if (lockByOther) return TableStatus.LOCKED_BY_OTHER;
        if (lockByMe) return TableStatus.LOCKED_BY_ME;
        if ("BILL_REQUESTED".equals(status)) return TableStatus.BILL_REQUESTED;
        if ("PENDING_SEND".equals(status) || pending > 0) return TableStatus.PENDING_SEND;
        if ("OCCUPIED".equals(status)) return TableStatus.OCCUPIED;
        if ("LOCKED".equals(status) && ticketId != null) return pending > 0 ? TableStatus.PENDING_SEND : TableStatus.OCCUPIED;
        return TableStatus.FREE;
    }
}
