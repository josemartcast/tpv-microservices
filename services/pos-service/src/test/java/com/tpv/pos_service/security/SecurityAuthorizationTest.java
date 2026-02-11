package com.tpv.pos_service.security;

import com.tpv.pos_service.config.SecurityConfig;
import com.tpv.pos_service.controller.PaymentController;
import com.tpv.pos_service.controller.TicketController;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.PaymentResponse;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.service.AuditService;
import com.tpv.pos_service.service.PaymentService;
import com.tpv.pos_service.service.TicketService;
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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {TicketController.class, PaymentController.class})
@Import(SecurityConfig.class)
@SuppressWarnings("null")
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TicketService ticketService;

    @MockitoBean
    private PaymentService paymentService;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void payTicket_deniesUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/pos/tickets/1/pay")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void payTicket_deniesAdminRoleBecauseDeprecated() throws Exception {
        mockMvc.perform(post("/api/v1/pos/tickets/1/pay")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void addLine_allowsUserRole() throws Exception {
        when(ticketService.addLine(anyLong(), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(sampleTicket());

        mockMvc.perform(post("/api/v1/pos/tickets/1/lines")
                .contentType("application/json")
                .content("{\"productId\":10,\"qty\":1}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isCreated());
    }

    @Test
    void addPayment_deniesUserRole() throws Exception {
        mockMvc.perform(post("/api/v1/pos/tickets/1/payments")
                .contentType("application/json")
                .content("{\"method\":\"CASH\",\"amountCents\":100}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void addPayment_allowsAdminRole() throws Exception {
        when(paymentService.addPayment(anyLong(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PaymentResponse(1L, com.tpv.pos_service.domain.PaymentMethod.CASH, 100, Instant.now()));

        mockMvc.perform(post("/api/v1/pos/tickets/1/payments")
                .contentType("application/json")
                .content("{\"method\":\"CASH\",\"amountCents\":100}")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isCreated());
    }

    private TicketResponse sampleTicket() {
        Instant now = Instant.now();
        return new TicketResponse(1L, 1, TicketStatus.OPEN, false, 100, 0, 100, now, now, List.of());
    }
}
