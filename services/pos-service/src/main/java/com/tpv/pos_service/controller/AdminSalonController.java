package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.CreateSalonAreaRequest;
import com.tpv.pos_service.dto.SalonAreaResponse;
import com.tpv.pos_service.dto.TableAliasResponse;
import com.tpv.pos_service.dto.UpdateSalonAreaRequest;
import com.tpv.pos_service.dto.UpdateTableAliasRequest;
import com.tpv.pos_service.service.SalonAreaService;
import com.tpv.pos_service.service.TableAliasService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/admin/salons")
public class AdminSalonController {

    private final SalonAreaService service;
    private final TableAliasService tableAliasService;

    public AdminSalonController(SalonAreaService service, TableAliasService tableAliasService) {
        this.service = service;
        this.tableAliasService = tableAliasService;
    }

    @GetMapping
    public List<SalonAreaResponse> list() {
        return service.listActive();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalonAreaResponse create(@Valid @RequestBody CreateSalonAreaRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public SalonAreaResponse rename(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSalonAreaRequest request
    ) {
        return service.rename(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/table-aliases")
    public List<TableAliasResponse> listTableAliases(@PathVariable Long id) {
        return tableAliasService.listAliasesBySalon(id);
    }

    @PutMapping("/{id}/tables/{tableNumber}/alias")
    public TableAliasResponse updateAlias(
            @PathVariable Long id,
            @PathVariable int tableNumber,
            @Valid @RequestBody UpdateTableAliasRequest request
    ) {
        return tableAliasService.upsertAlias(id, tableNumber, request.alias());
    }
}
