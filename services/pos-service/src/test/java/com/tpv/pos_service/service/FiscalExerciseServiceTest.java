package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.domain.FiscalExercise;
import com.tpv.pos_service.domain.FiscalExerciseStatus;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.CloseFiscalExerciseRequest;
import com.tpv.pos_service.dto.OpenFiscalExerciseRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.FiscalExerciseRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FiscalExerciseServiceTest {

    @Mock
    private FiscalExerciseRepository repo;
    @Mock
    private CashSessionRepository cashSessionRepo;
    @Mock
    private TicketRepository ticketRepo;

    @InjectMocks
    private FiscalExerciseService service;

    @Test
    void current_throwsWhenNoOpenExercise() {
        when(repo.findFirstByStatusOrderByFiscalYearDesc(FiscalExerciseStatus.OPEN)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.current());
    }

    @Test
    void open_rejectsWhenAlreadyOpen() {
        when(repo.existsByStatus(FiscalExerciseStatus.OPEN)).thenReturn(true);
        assertThrows(ConflictException.class, () -> service.open(new OpenFiscalExerciseRequest(2026, "x"), "admin"));
    }

    @Test
    void close_rejectsWhenCashSessionIsOpen() {
        FiscalExercise fx = new FiscalExercise(2026, "admin", null);
        ReflectionTestUtils.setField(fx, "id", 1L);
        when(repo.findById(1L)).thenReturn(Optional.of(fx));
        when(cashSessionRepo.existsByStatus(CashSessionStatus.OPEN)).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.close(1L, new CloseFiscalExerciseRequest("cierre"), "admin"));
    }

    @Test
    void close_happyPath_setsClosedStatus() {
        FiscalExercise fx = new FiscalExercise(2026, "admin", null);
        ReflectionTestUtils.setField(fx, "id", 1L);
        when(repo.findById(1L)).thenReturn(Optional.of(fx));
        when(cashSessionRepo.existsByStatus(CashSessionStatus.OPEN)).thenReturn(false);
        when(ticketRepo.existsByStatus(TicketStatus.OPEN)).thenReturn(false);

        var response = service.close(1L, new CloseFiscalExerciseRequest("cierre anual"), "admin");

        assertEquals(FiscalExerciseStatus.CLOSED, response.status());
        assertEquals("admin", response.closedBy());
    }
}
