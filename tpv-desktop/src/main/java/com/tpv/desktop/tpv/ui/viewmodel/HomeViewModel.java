package com.tpv.desktop.tpv.ui.viewmodel;

import com.tpv.desktop.tpv.app.AppContext;
import com.tpv.desktop.tpv.domain.model.TableSnapshot;
import com.tpv.desktop.tpv.domain.model.TableStatus;
import com.tpv.desktop.tpv.services.LockService;
import com.tpv.desktop.tpv.services.OrderService;
import com.tpv.desktop.tpv.services.TableService;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class HomeViewModel {
    private static final String ALL_SALONS = "Todos";

    private final TableService tableService;
    private final OrderService orderService;
    private final LockService lockService;
    private final ObservableList<TableCardViewModel> allTables = FXCollections.observableArrayList();
    private final ObservableList<TableCardViewModel> tables = FXCollections.observableArrayList();
    private final ObservableList<String> salons = FXCollections.observableArrayList(ALL_SALONS);
    private final StringProperty selectedSalon = new SimpleStringProperty(ALL_SALONS);

    public HomeViewModel() {
        AppContext ctx = AppContext.get();
        tableService = ctx.tableService();
        orderService = ctx.orderService();
        lockService = ctx.lockService();
    }

    public ObservableList<TableCardViewModel> tables() {
        return tables;
    }

    public ObservableList<String> salons() {
        return salons;
    }

    public StringProperty selectedSalonProperty() {
        return selectedSalon;
    }

    public void selectSalon(String salon) {
        if (salon == null || salon.isBlank() || !salons.contains(salon)) {
            selectedSalon.set(ALL_SALONS);
        } else {
            selectedSalon.set(salon);
        }
        applySalonFilter();
    }

    public void refresh() {
        List<TableSnapshot> snapshots = tableService.tables();
        boolean cleaned = cleanupOwnOrphanLocks(snapshots);
        if (cleaned) {
            snapshots = tableService.tables();
        }
        allTables.clear();
        snapshots.forEach(s -> allTables.add(toCard(s)));
        rebuildSalons();
        applySalonFilter();
    }

    private void applySalonFilter() {
        tables.clear();
        String selected = selectedSalon.get();
        if (ALL_SALONS.equals(selected)) {
            tables.addAll(allTables);
            return;
        }
        allTables.stream()
                .filter(card -> selected.equals(card.salonNameProperty().get()))
                .forEach(tables::add);
    }

    private void rebuildSalons() {
        List<String> nextSalons = new ArrayList<>();
        nextSalons.add(ALL_SALONS);
        for (TableCardViewModel table : allTables) {
            String salon = table.salonNameProperty().get();
            if (salon == null || salon.isBlank() || nextSalons.contains(salon)) {
                continue;
            }
            nextSalons.add(salon);
        }
        salons.setAll(nextSalons);
        if (!salons.contains(selectedSalon.get())) {
            selectedSalon.set(ALL_SALONS);
        }
    }

    public long openOrEnter(TableCardViewModel card) {
        if (card.statusProperty().get() == TableStatus.LOCKED_BY_OTHER) {
            throw new IllegalStateException("Bloqueada por otro terminal");
        }
        lockService.lock(card.getTableId());
        if (card.getOrderId() > 0) {
            return card.getOrderId();
        }
        return orderService.openOrGetByTable(card.getTableId()).getId();
    }

    private TableCardViewModel toCard(TableSnapshot s) {
        TableCardViewModel vm = new TableCardViewModel();
        vm.tableIdProperty().set(s.tableId());
        vm.orderIdProperty().set(s.orderId());
        vm.titleProperty().set(s.label());
        vm.salonNameProperty().set(extractSalonName(s.label()));
        vm.totalTextProperty().set(s.totalCents() > 0 ? money(s.totalCents()) : "-");
        vm.elapsedTextProperty().set(s.elapsedMinutes() > 0 ? s.elapsedMinutes() + " min" : "-");
        vm.statusProperty().set(s.status());
        vm.pendingWarnProperty().set(s.pendingCount() > 0);
        vm.billWarnProperty().set(s.billRequested());

        switch (s.status()) {
            case FREE -> {
                vm.statusTextProperty().set("Libre");
                vm.actionTextProperty().set("Abrir");
            }
            case OCCUPIED -> {
                vm.statusTextProperty().set("Ocupada");
                vm.actionTextProperty().set("Entrar");
            }
            case PENDING_SEND -> {
                vm.statusTextProperty().set("Pendiente enviar");
                vm.actionTextProperty().set("Entrar");
            }
            case BILL_REQUESTED -> {
                vm.statusTextProperty().set("Cuenta pedida");
                vm.actionTextProperty().set("Entrar");
            }
            case LOCKED_BY_ME -> {
                vm.statusTextProperty().set("Bloqueada (yo)");
                vm.actionTextProperty().set("Entrar");
                vm.lockTextProperty().set("En edicion por este terminal");
            }
            case LOCKED_BY_OTHER -> {
                vm.statusTextProperty().set("Bloqueada");
                vm.actionTextProperty().set("Bloqueada");
                vm.blockedProperty().set(true);
                vm.lockTextProperty().set("Bloqueada por " + s.lockTerminalId());
            }
        }
        return vm;
    }

    private static String money(int cents) {
        return String.format(java.util.Locale.US, "%.2f EUR", cents / 100.0);
    }

    private static String extractSalonName(String label) {
        if (label == null || label.isBlank()) {
            return "Salon";
        }
        String value = label.trim();
        String needle = " - mesa ";
        int idx = value.toLowerCase(Locale.ROOT).indexOf(needle);
        if (idx > 0) {
            return value.substring(0, idx).trim();
        }
        if (value.toLowerCase(Locale.ROOT).startsWith("mesa ")) {
            return "Salon";
        }
        int dash = value.indexOf('-');
        if (dash > 0) {
            return value.substring(0, dash).trim();
        }
        return "Salon";
    }

    private boolean cleanupOwnOrphanLocks(List<TableSnapshot> snapshots) {
        boolean cleanedAny = false;
        for (TableSnapshot snapshot : snapshots) {
            if (!isOwnOrphanLock(snapshot)) {
                continue;
            }
            try {
                lockService.unlock(snapshot.tableId());
                cleanedAny = true;
            } catch (Exception ignored) {
                // Defensive cleanup: if unlock fails, we keep current state.
            }
        }
        return cleanedAny;
    }

    private static boolean isOwnOrphanLock(TableSnapshot snapshot) {
        return snapshot.status() == TableStatus.LOCKED_BY_ME
                && snapshot.orderId() <= 0
                && snapshot.totalCents() <= 0
                && snapshot.pendingCount() <= 0;
    }
}


