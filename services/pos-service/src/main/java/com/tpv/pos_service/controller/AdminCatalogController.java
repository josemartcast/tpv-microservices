package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.SeedCatalogResponse;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.CatalogSeedService;
import com.tpv.pos_service.util.ActorResolver;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
@RequestMapping("/api/v1/pos/admin")
public class AdminCatalogController {

    private final CatalogSeedService catalogSeedService;
    private final AuditService auditService;

    public AdminCatalogController(CatalogSeedService catalogSeedService, AuditService auditService) {
        this.catalogSeedService = catalogSeedService;
        this.auditService = auditService;
    }

    @PostMapping("/seed-catalog")
    @ResponseStatus(HttpStatus.OK)
    public SeedCatalogResponse seedCatalog(
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            SeedCatalogResponse response = catalogSeedService.seedDefaultCatalog();
            auditService.recordSuccess("CATALOG_SEED", "CATALOG", null, actor, term, null, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("CATALOG_SEED", "CATALOG", null, actor, term, null, e);
            throw e;
        }
    }
}
