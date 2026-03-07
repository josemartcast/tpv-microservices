package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.SalonArea;
import com.tpv.pos_service.domain.TableAlias;
import com.tpv.pos_service.dto.TableAliasResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.SalonAreaRepository;
import com.tpv.pos_service.repository.TableAliasRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TableAliasService {

    private final TableAliasRepository tableAliasRepo;
    private final SalonAreaRepository salonAreaRepo;

    public TableAliasService(TableAliasRepository tableAliasRepo, SalonAreaRepository salonAreaRepo) {
        this.tableAliasRepo = tableAliasRepo;
        this.salonAreaRepo = salonAreaRepo;
    }

    @Transactional(readOnly = true)
    public Map<Integer, String> aliasesByTables(List<Integer> tableNumbers) {
        if (tableNumbers == null || tableNumbers.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> map = new HashMap<>();
        for (TableAlias alias : tableAliasRepo.findByTableNumberIn(tableNumbers)) {
            map.put(alias.getTableNumber(), alias.getAliasValue());
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<TableAliasResponse> listAliasesBySalon(Long salonId) {
        SalonArea salon = salonAreaRepo.findByIdAndActiveTrue(salonId)
                .orElseThrow(() -> new NotFoundException("Salon not found: " + salonId));
        List<Integer> tableNumbers = tableNumbersOf(salon);
        Map<Integer, String> aliases = aliasesByTables(tableNumbers);
        List<TableAliasResponse> rows = new ArrayList<>();
        for (Integer table : tableNumbers) {
            String alias = aliases.getOrDefault(table, "");
            rows.add(new TableAliasResponse(table, alias));
        }
        return rows;
    }

    @Transactional
    public TableAliasResponse upsertAlias(Long salonId, int tableNumber, String aliasRaw) {
        SalonArea salon = salonAreaRepo.findByIdAndActiveTrue(salonId)
                .orElseThrow(() -> new NotFoundException("Salon not found: " + salonId));
        if (!salon.containsTable(tableNumber)) {
            throw new ConflictException("Table " + tableNumber + " does not belong to salon " + salon.getName());
        }

        String alias = normalize(aliasRaw);
        if (alias.isBlank()) {
            tableAliasRepo.deleteByTableNumber(tableNumber);
            return new TableAliasResponse(tableNumber, "");
        }

        TableAlias current = tableAliasRepo.findByTableNumber(tableNumber).orElse(null);
        if (current == null) {
            tableAliasRepo.save(new TableAlias(tableNumber, alias));
            return new TableAliasResponse(tableNumber, alias);
        }
        current.setAliasValue(alias);
        return new TableAliasResponse(tableNumber, current.getAliasValue());
    }

    @Transactional
    public TableAliasResponse upsertAliasByTableNumber(int tableNumber, String aliasRaw) {
        SalonArea salon = salonAreaRepo.findAllByActiveTrueOrderByFirstTableNumberAsc().stream()
                .filter(s -> s.containsTable(tableNumber))
                .findFirst()
                .orElseThrow(() -> new ConflictException("Table is not configured in any active salon: " + tableNumber));
        return upsertAlias(salon.getId(), tableNumber, aliasRaw);
    }

    @Transactional
    public void clearAliasesForTables(List<Integer> tableNumbers) {
        if (tableNumbers == null || tableNumbers.isEmpty()) {
            return;
        }
        for (Integer tableNumber : tableNumbers) {
            tableAliasRepo.deleteByTableNumber(tableNumber);
        }
    }

    private static List<Integer> tableNumbersOf(SalonArea salon) {
        List<Integer> numbers = new ArrayList<>();
        for (int i = salon.getFirstTableNumber(); i <= salon.getLastTableNumber(); i++) {
            numbers.add(i);
        }
        return numbers;
    }

    private static String normalize(String aliasRaw) {
        if (aliasRaw == null) {
            return "";
        }
        String alias = aliasRaw.trim();
        if (alias.length() > 80) {
            alias = alias.substring(0, 80);
        }
        return alias.toUpperCase(Locale.ROOT);
    }
}
