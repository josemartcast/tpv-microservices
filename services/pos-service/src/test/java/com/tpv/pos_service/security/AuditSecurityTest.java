package com.tpv.pos_service.security;

import com.tpv.pos_service.config.SecurityConfig;
import com.tpv.pos_service.controller.AuditController;
import com.tpv.pos_service.service.AuditService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditController.class)
@Import(SecurityConfig.class)
@SuppressWarnings("null")
class AuditSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void auditEvents_deniesCamareroRole() throws Exception {
        mockMvc.perform(get("/api/v1/pos/audit/events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CAMARERO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditEvents_allowsAdminRole() throws Exception {
        when(auditService.list(null, null, null, null, null, null, null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/pos/audit/events")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
