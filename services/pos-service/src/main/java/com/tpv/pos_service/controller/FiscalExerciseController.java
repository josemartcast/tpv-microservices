package com.tpv.pos_service.controller;

import com.tpv.pos_service.dto.CloseFiscalExerciseRequest;
import com.tpv.pos_service.dto.FiscalExerciseResponse;
import com.tpv.pos_service.dto.OpenFiscalExerciseRequest;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.FiscalExerciseService;
import com.tpv.pos_service.util.ActorResolver;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pos/fiscal-exercises")
public class FiscalExerciseController {

    private final FiscalExerciseService service;
    private final AuditService auditService;

    public FiscalExerciseController(FiscalExerciseService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @GetMapping
    public List<FiscalExerciseResponse> list() {
        return service.list();
    }

    @GetMapping("/current")
    public FiscalExerciseResponse current() {
        return service.current();
    }

    @PostMapping("/open")
    @ResponseStatus(HttpStatus.CREATED)
    public FiscalExerciseResponse open(
            @Valid @RequestBody OpenFiscalExerciseRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            FiscalExerciseResponse response = service.open(req, actor);
            auditService.recordSuccess("FISCAL_EXERCISE_OPEN", "FISCAL_EXERCISE", response.id(), actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("FISCAL_EXERCISE_OPEN", "FISCAL_EXERCISE", null, actor, term, req, e);
            throw e;
        }
    }

    @PostMapping("/{id}/close")
    public FiscalExerciseResponse close(
            @PathVariable Long id,
            @RequestBody(required = false) CloseFiscalExerciseRequest req,
            Authentication auth,
            @RequestHeader(value = "X-Terminal-Id", required = false) String terminalId
    ) {
        String actor = ActorResolver.usernameFrom(auth);
        String term = ActorResolver.terminalFromHeader(terminalId);
        try {
            FiscalExerciseResponse response = service.close(id, req, actor);
            auditService.recordSuccess("FISCAL_EXERCISE_CLOSE", "FISCAL_EXERCISE", id, actor, term, req, response);
            return response;
        } catch (RuntimeException e) {
            auditService.recordFailure("FISCAL_EXERCISE_CLOSE", "FISCAL_EXERCISE", id, actor, term, req, e);
            throw e;
        }
    }
}

