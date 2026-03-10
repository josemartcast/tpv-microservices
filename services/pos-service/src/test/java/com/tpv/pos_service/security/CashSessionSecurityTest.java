package com.tpv.pos_service.security;

import com.tpv.pos_service.config.SecurityConfig;
import com.tpv.pos_service.controller.CashSessionController;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.dto.CashSessionOpenTicketResponse;
import com.tpv.pos_service.dto.CashSessionResponse;
import com.tpv.pos_service.dto.CloseCashSessionRequest;
import com.tpv.pos_service.dto.ResolveOpenTicketsResponse;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.CashIncidentService;
import com.tpv.pos_service.service.CashSessionService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
class CashSessionSecurityTest {

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
    void openTickets_allowsCajeroRole() throws Exception {
        when(cashSessionService.openTickets(1L))
                .thenReturn(List.of(new CashSessionOpenTicketResponse(10L, 4, 1_250, Instant.now())));

        mockMvc.perform(get("/api/v1/pos/cash-sessions/1/open-tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAJERO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(10))
                .andExpect(jsonPath("$[0].tableNumber").value(4))
                .andExpect(jsonPath("$[0].totalCents").value(1250));
    }

    @Test
    void resolveOpenTickets_deniesCamareroRole() throws Exception {
        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/resolve-open-tickets")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAMARERO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolveOpenTickets_allowsAdminRole() throws Exception {
        var remaining = new CashSessionOpenTicketResponse(11L, 7, 2_000, Instant.now());
        when(cashSessionService.resolveOpenTickets(1L))
                .thenReturn(new ResolveOpenTicketsResponse(
                        1L,
                        3,
                        2,
                        1,
                        List.of(21L, 22L),
                        List.of(remaining)
                ));

        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/resolve-open-tickets")
                        .header("X-Terminal-Id", "T-A")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashSessionId").value(1))
                .andExpect(jsonPath("$.autoCancelled").value(2))
                .andExpect(jsonPath("$.openAfter").value(1))
                .andExpect(jsonPath("$.remainingOpenTickets[0].ticketId").value(11));
    }

    @Test
    void close_deniesCamareroRole() throws Exception {
        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/close")
                        .contentType("application/json")
                        .content("{\"closingCashCents\":12000,\"note\":\"cierre\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAMARERO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void close_allowsAdminRole() throws Exception {
        when(cashSessionService.close(eq(1L), any(CloseCashSessionRequest.class), anyString(), any()))
                .thenReturn(new CashSessionResponse(
                        1L,
                        CashSessionStatus.CLOSED,
                        10_000,
                        12_500,
                        12_000,
                        -500,
                        Instant.now(),
                        Instant.now(),
                        "admin",
                        "admin",
                        "cierre"
                ));

        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/close")
                        .contentType("application/json")
                        .content("{\"closingCashCents\":12000,\"note\":\"cierre\"}")
                        .header("X-Terminal-Id", "T-A")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.expectedCashCents").value(12500))
                .andExpect(jsonPath("$.differenceCents").value(-500));
    }

    @Test
    void close_returnsConflictWhenServiceRejects() throws Exception {
        when(cashSessionService.close(anyLong(), any(CloseCashSessionRequest.class), anyString(), any()))
                .thenThrow(new ConflictException("Cannot close cash session with OPEN tickets"));

        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/close")
                        .contentType("application/json")
                        .content("{\"closingCashCents\":12000,\"note\":\"cierre\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Cannot close cash session with OPEN tickets"));
    }

    @Test
    void open_deniesPdaClientHeaderEvenForAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/pos/cash-sessions/open")
                        .contentType("application/json")
                        .content("{\"openingCashCents\":1000,\"note\":\"apertura\"}")
                        .header("X-Client-App", "PDA")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(cashSessionService, never()).open(any(), anyString());
    }

    @Test
    void close_deniesPdaClientHeaderEvenForAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/pos/cash-sessions/1/close")
                        .contentType("application/json")
                        .content("{\"closingCashCents\":12000,\"note\":\"cierre\"}")
                        .header("X-Client-App", "PDA")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(cashSessionService, never()).close(anyLong(), any(CloseCashSessionRequest.class), anyString(), any());
    }
}
