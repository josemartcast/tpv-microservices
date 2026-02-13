package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashIncident;
import com.tpv.pos_service.domain.CashIncidentDirection;
import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.dto.CreateCashIncidentRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.repository.CashIncidentRepository;
import com.tpv.pos_service.repository.CashSessionRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class CashIncidentServiceTest {

    @Mock
    private CashSessionRepository cashSessionRepository;
    @Mock
    private CashIncidentRepository cashIncidentRepository;

    @InjectMocks
    private CashIncidentService service;

    @Test
    void addIncident_withSameIdempotencyKey_returnsExistingWithoutSaving() {
        CashSession cashSession = new CashSession(10_000, "admin", null);
        ReflectionTestUtils.setField(cashSession, "id", 1L);

        CashIncident existing = new CashIncident(
                cashSession, CashIncidentDirection.IN, 500, "propina", "admin", "incident-1");
        ReflectionTestUtils.setField(existing, "id", 9L);

        when(cashIncidentRepository.findByCashSession_IdAndIdempotencyKey(1L, "incident-1"))
                .thenReturn(Optional.of(existing));

        var response = service.addIncident(
                1L,
                new CreateCashIncidentRequest(CashIncidentDirection.IN, 500, "propina"),
                "admin",
                "incident-1"
        );

        assertEquals(9L, response.id());
        verify(cashIncidentRepository, never()).save(any());
    }

    @Test
    void addIncident_rejectsWhenCashSessionIsClosed() {
        CashSession cashSession = new CashSession(10_000, "admin", null);
        cashSession.close(12_000, "admin", "fin turno");

        when(cashSessionRepository.findById(1L)).thenReturn(Optional.of(cashSession));

        assertThrows(
                ConflictException.class,
                () -> service.addIncident(
                        1L,
                        new CreateCashIncidentRequest(CashIncidentDirection.OUT, 100, "retiro"),
                        "admin",
                        "incident-2"
                )
        );
    }

    @Test
    void sumNetIncidents_returnsInMinusOut() {
        when(cashIncidentRepository.sumByCashSessionAndDirection(1L, CashIncidentDirection.IN)).thenReturn(1200);
        when(cashIncidentRepository.sumByCashSessionAndDirection(1L, CashIncidentDirection.OUT)).thenReturn(300);

        assertEquals(900, service.sumNetIncidentsCents(1L));
        verify(cashIncidentRepository).sumByCashSessionAndDirection(1L, CashIncidentDirection.IN);
        verify(cashIncidentRepository).sumByCashSessionAndDirection(1L, CashIncidentDirection.OUT);
    }

    @Test
    void sumIncidents_helpersDelegateToRepository() {
        when(cashIncidentRepository.sumByCashSessionAndDirection(1L, CashIncidentDirection.IN)).thenReturn(700);
        when(cashIncidentRepository.sumByCashSessionAndDirection(1L, CashIncidentDirection.OUT)).thenReturn(250);

        assertEquals(700, service.sumIncidentsInCents(1L));
        assertEquals(250, service.sumIncidentsOutCents(1L));
        verify(cashIncidentRepository).sumByCashSessionAndDirection(eq(1L), eq(CashIncidentDirection.IN));
        verify(cashIncidentRepository).sumByCashSessionAndDirection(eq(1L), eq(CashIncidentDirection.OUT));
    }
}
