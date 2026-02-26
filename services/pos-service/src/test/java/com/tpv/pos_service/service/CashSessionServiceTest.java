package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.CashSessionStatus;
import com.tpv.pos_service.domain.FiscalExercise;
import com.tpv.pos_service.domain.FiscalExerciseStatus;
import com.tpv.pos_service.domain.PaymentMethod;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.CloseCashSessionRequest;
import com.tpv.pos_service.dto.FiscalSummaryResponse;
import com.tpv.pos_service.dto.OpenCashSessionRequest;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.exception.NotFoundException;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.FiscalExerciseRepository;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.TicketRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashSessionServiceTest {

    @Mock
    private CashSessionRepository cashSessionRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private FiscalExerciseRepository fiscalExerciseRepository;
    @Mock
    private CashIncidentService cashIncidentService;
    @Mock
    private FiscalService fiscalService;
    @Mock
    private IdempotencyService idempotencyService;

    @InjectMocks
    private CashSessionService cashSessionService;

    @Test
    void current_includesNetIncidentsInExpectedCash() {
        CashSession cashSession = new CashSession(10_000, "admin", null);
        ReflectionTestUtils.setField(cashSession, "id", 1L);

        when(cashSessionRepository.findFirstByStatusOrderByOpenedAtDesc(CashSessionStatus.OPEN))
                .thenReturn(Optional.of(cashSession));
        when(paymentRepository.sumByCashSessionAndMethod(any(), eq(PaymentMethod.CASH)))
                .thenReturn(5_000);
        when(cashIncidentService.sumNetIncidentsCents(any()))
                .thenReturn(-1_200);

        var response = cashSessionService.current();
        assertEquals(13_800, response.expectedCashCents());
    }

    @Test
    void close_rejectsWhenThereAreOpenTickets() {
        CashSession cashSession = new CashSession(5_000, "admin", null);
        when(cashSessionRepository.findById(1L)).thenReturn(Optional.of(cashSession));
        when(ticketRepository.existsByCashSession_IdAndStatus(1L, TicketStatus.OPEN)).thenReturn(true);
        when(idempotencyService.execute(anyString(), anyLong(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier =
                            (java.util.function.Supplier<Object>) invocation.getArgument(4);
                    return supplier.get();
                });

        assertThrows(
                ConflictException.class,
                () -> cashSessionService.close(1L, new CloseCashSessionRequest(6_000, "cierre"), "admin", "k-1")
        );
    }

    @Test
    void closeSummary_includesIncidentsAndComputedExpected() {
        CashSession cashSession = new CashSession(10_000, "admin", null);
        ReflectionTestUtils.setField(cashSession, "id", 1L);

        when(cashSessionRepository.findById(1L)).thenReturn(Optional.of(cashSession));
        when(paymentRepository.sumByCashSessionAndMethod(1L, PaymentMethod.CASH)).thenReturn(5_000);
        when(cashIncidentService.sumIncidentsInCents(1L)).thenReturn(1_200);
        when(cashIncidentService.sumIncidentsOutCents(1L)).thenReturn(300);
        when(fiscalService.summary(1L))
                .thenReturn(new FiscalSummaryResponse(1L, 2, 0, 8_000, 6_612, 1_388, 5_000, 3_000, 0));

        var response = cashSessionService.closeSummary(1L);

        assertEquals(10_000, response.openingCashCents());
        assertEquals(5_000, response.cashPaymentsNetCents());
        assertEquals(1_200, response.incidentsInCents());
        assertEquals(300, response.incidentsOutCents());
        assertEquals(900, response.incidentsNetCents());
        assertEquals(15_900, response.expectedCashCents());
    }

    @Test
    void resolveOpenTickets_cancelsOnlyZeroTotalTickets() {
        CashSession cashSession = new CashSession(10_000, "admin", null);
        ReflectionTestUtils.setField(cashSession, "id", 1L);

        Ticket emptyA = new Ticket(cashSession, 3);
        ReflectionTestUtils.setField(emptyA, "id", 101L);
        emptyA.setTotalCents(0);

        Ticket emptyB = new Ticket(cashSession, 7);
        ReflectionTestUtils.setField(emptyB, "id", 102L);
        emptyB.setTotalCents(0);

        Ticket withAmount = new Ticket(cashSession, 9);
        ReflectionTestUtils.setField(withAmount, "id", 103L);
        withAmount.setTotalCents(1_250);

        when(cashSessionRepository.findById(1L)).thenReturn(Optional.of(cashSession));
        when(ticketRepository.findAllByCashSession_IdAndStatus(1L, TicketStatus.OPEN))
                .thenReturn(List.of(emptyA, emptyB, withAmount));

        var response = cashSessionService.resolveOpenTickets(1L);

        assertEquals(3, response.openBefore());
        assertEquals(2, response.autoCancelled());
        assertEquals(1, response.openAfter());
        assertEquals(2, response.autoCancelledTicketIds().size());
        assertEquals(101L, response.autoCancelledTicketIds().get(0));
        assertEquals(102L, response.autoCancelledTicketIds().get(1));
        assertEquals(1, response.remainingOpenTickets().size());
        assertEquals(103L, response.remainingOpenTickets().get(0).ticketId());

        assertEquals(TicketStatus.CANCELLED, emptyA.getStatus());
        assertEquals(TicketStatus.CANCELLED, emptyB.getStatus());
        assertEquals(TicketStatus.OPEN, withAmount.getStatus());
    }

    @Test
    void close_happyPath_setsExpectedAndDifference_andClosesSession() {
        CashSession cashSession = new CashSession(10_000, "admin", "inicio");
        ReflectionTestUtils.setField(cashSession, "id", 1L);

        when(cashSessionRepository.findById(1L)).thenReturn(Optional.of(cashSession));
        when(ticketRepository.existsByCashSession_IdAndStatus(1L, TicketStatus.OPEN)).thenReturn(false);
        when(paymentRepository.sumByCashSessionAndMethod(1L, PaymentMethod.CASH)).thenReturn(3_000);
        when(cashIncidentService.sumNetIncidentsCents(1L)).thenReturn(-500);
        when(idempotencyService.execute(anyString(), anyLong(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier =
                            (java.util.function.Supplier<Object>) invocation.getArgument(4);
                    return supplier.get();
                });

        var response = cashSessionService.close(1L, new CloseCashSessionRequest(12_000, "cierre fin turno"), "admin", "k-2");

        assertEquals(CashSessionStatus.CLOSED, response.status());
        assertEquals(12_500, response.expectedCashCents());
        assertEquals(12_000, response.closingCashCents());
        assertEquals(-500, response.differenceCents());
        assertEquals("admin", response.closedBy());
        assertEquals("cierre fin turno", response.note());

        assertEquals(CashSessionStatus.CLOSED, cashSession.getStatus());
        assertEquals(12_500, cashSession.getExpectedCashCents());
        assertEquals(12_000, cashSession.getClosingCashCents());
        assertEquals(-500, cashSession.getCashDifferenceCents());
        assertTrue(cashSession.getClosedAt() != null);
    }

    @Test
    void close_rejectsWhenSessionAlreadyClosed() {
        CashSession cashSession = new CashSession(10_000, "admin", null);
        ReflectionTestUtils.setField(cashSession, "id", 1L);
        cashSession.setExpectedCashCents(10_000);
        cashSession.close(10_000, "admin", "cerrada");

        when(cashSessionRepository.findById(1L)).thenReturn(Optional.of(cashSession));
        when(idempotencyService.execute(anyString(), anyLong(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    java.util.function.Supplier<Object> supplier =
                            (java.util.function.Supplier<Object>) invocation.getArgument(4);
                    return supplier.get();
                });

        assertThrows(
                ConflictException.class,
                () -> cashSessionService.close(1L, new CloseCashSessionRequest(11_000, "reintento"), "admin", "k-3")
        );
    }

    @Test
    void resolveOpenTickets_throwsNotFoundWhenSessionDoesNotExist() {
        when(cashSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> cashSessionService.resolveOpenTickets(999L));
    }

    @Test
    void open_autoCreatesFiscalExerciseWhenMissing() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.OPEN)).thenReturn(false);
        when(fiscalExerciseRepository.findFirstByStatusOrderByFiscalYearDesc(FiscalExerciseStatus.OPEN))
                .thenReturn(Optional.empty());
        when(fiscalExerciseRepository.findByFiscalYear(anyInt()))
                .thenReturn(Optional.empty());
        when(fiscalExerciseRepository.save(any(FiscalExercise.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(cashSessionRepository.save(any(CashSession.class)))
                .thenAnswer(invocation -> {
                    CashSession cs = invocation.getArgument(0);
                    ReflectionTestUtils.setField(cs, "id", 1L);
                    return cs;
                });

        var response = cashSessionService.open(new OpenCashSessionRequest(10_000, "inicio"), "admin");

        assertEquals(CashSessionStatus.OPEN, response.status());
        assertEquals(10_000, response.openingCashCents());
    }

    @Test
    void open_usesCurrentFiscalExercise() {
        when(cashSessionRepository.existsByStatus(CashSessionStatus.OPEN)).thenReturn(false);
        FiscalExercise exercise = new FiscalExercise(2026, "admin", "ejercicio anual");
        ReflectionTestUtils.setField(exercise, "id", 10L);
        when(fiscalExerciseRepository.findFirstByStatusOrderByFiscalYearDesc(FiscalExerciseStatus.OPEN))
                .thenReturn(Optional.of(exercise));
        when(cashSessionRepository.save(any(CashSession.class)))
                .thenAnswer(invocation -> {
                    CashSession cs = invocation.getArgument(0);
                    ReflectionTestUtils.setField(cs, "id", 1L);
                    return cs;
                });

        var response = cashSessionService.open(new OpenCashSessionRequest(10_000, "inicio"), "admin");

        assertEquals(CashSessionStatus.OPEN, response.status());
        assertEquals(10_000, response.openingCashCents());
    }

    @Test
    void open_reopensClosedFiscalExerciseForCurrentYear() {
        int currentYear = java.time.Year.now().getValue();
        when(cashSessionRepository.existsByStatus(CashSessionStatus.OPEN)).thenReturn(false);
        when(fiscalExerciseRepository.findFirstByStatusOrderByFiscalYearDesc(FiscalExerciseStatus.OPEN))
                .thenReturn(Optional.empty());

        FiscalExercise closedCurrent = new FiscalExercise(currentYear, "admin", "cierre accidental");
        closedCurrent.close("admin", "manual close");
        when(fiscalExerciseRepository.findByFiscalYear(currentYear)).thenReturn(Optional.of(closedCurrent));
        when(cashSessionRepository.save(any(CashSession.class)))
                .thenAnswer(invocation -> {
                    CashSession cs = invocation.getArgument(0);
                    ReflectionTestUtils.setField(cs, "id", 1L);
                    return cs;
                });

        var response = cashSessionService.open(new OpenCashSessionRequest(10_000, "inicio"), "admin");

        assertEquals(CashSessionStatus.OPEN, response.status());
        assertEquals(FiscalExerciseStatus.OPEN, closedCurrent.getStatus());
    }

    @Test
    void open_rolloverRejectsWhenPastExerciseHasOpenTickets() {
        int currentYear = java.time.Year.now().getValue();
        when(cashSessionRepository.existsByStatus(CashSessionStatus.OPEN)).thenReturn(false);

        FiscalExercise pastOpen = new FiscalExercise(currentYear - 1, "admin", "old year open");
        when(fiscalExerciseRepository.findFirstByStatusOrderByFiscalYearDesc(FiscalExerciseStatus.OPEN))
                .thenReturn(Optional.of(pastOpen));
        when(ticketRepository.existsByStatus(TicketStatus.OPEN)).thenReturn(true);

        var ex = assertThrows(
                ConflictException.class,
                () -> cashSessionService.open(new OpenCashSessionRequest(10_000, "inicio"), "admin")
        );
        assertTrue(ex.getMessage().contains("OPEN tickets"));
        assertEquals(FiscalExerciseStatus.OPEN, pastOpen.getStatus());
    }
}
