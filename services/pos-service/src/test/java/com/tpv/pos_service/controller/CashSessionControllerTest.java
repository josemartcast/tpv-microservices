package com.tpv.pos_service.controller;

import com.tpv.pos_service.config.SecurityConfig;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.dto.CashSessionResponse;
import com.tpv.pos_service.dto.CloseCashSessionRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.CashIncidentService;
import com.tpv.pos_service.service.CashSessionService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CashSessionController.class)
@Import(SecurityConfig.class)
@SuppressWarnings("null")
class CashSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CashSessionService cashSessionService;

    @MockitoBean
    private CashIncidentService cashIncidentService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void close_propagatesIdempotencyKeyAndActorFromJwt() throws Exception {
        when(cashSessionService.close(eq(1L), any(CloseCashSessionRequest.class), eq("ana"), eq("k-123")))
                .thenReturn(new CashSessionResponse(
                        1L,
                        CashSessionStatus.CLOSED,
                        10_000,
                        11_000,
                        11_000,
                        0,
                        Instant.now(),
                        Instant.now(),
                        "admin",
                        "ana",
                        "cierre"
                ));

        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/close")
                        .contentType("application/json")
                        .content("{\"closingCashCents\":11000,\"note\":\"cierre\"}")
                        .header("Idempotency-Key", "k-123")
                        .header("X-Terminal-Id", "T-A")
                        .with(jwt().jwt(jwt -> jwt.claim("username", "ana").subject("ana"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.closedBy").value("ana"));

        verify(cashSessionService).close(eq(1L), any(CloseCashSessionRequest.class), eq("ana"), eq("k-123"));
    }

    @Test
    void resolveOpenTickets_recordsAuditFailureWhenServiceThrows() throws Exception {
        when(cashSessionService.resolveOpenTickets(1L))
                .thenThrow(new ConflictException("Cannot resolve open tickets"));

        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/resolve-open-tickets")
                        .header("X-Terminal-Id", "T-A")
                        .with(jwt().jwt(jwt -> jwt.claim("username", "ana").subject("ana"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot resolve open tickets"));

        verify(auditService).recordFailure(
                eq("CASH_RESOLVE_OPEN_TICKETS"),
                eq("CASH_SESSION"),
                eq(1L),
                eq("ana"),
                eq("T-A"),
                isNull(),
                any(ConflictException.class)
        );
    }

    @Test
    void openTickets_returns404WhenServiceSessionNotFound() throws Exception {
        when(cashSessionService.openTickets(99L))
                .thenThrow(new NotFoundException("Cash session not found: 99"));

        mockMvc.perform(get("/api/v1/pos/cash-sessions/99/open-tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAJERO"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Cash session not found: 99"));
    }
}
