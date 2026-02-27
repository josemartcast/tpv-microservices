package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.Payment;
import com.tpv.pos_service.domain.PaymentMethod;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.exception.ConflictException;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.CashIncidentRepository;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.ProductRepository;
import com.tpv.pos_service.repository.SalonAreaRepository;
import com.tpv.pos_service.repository.TicketLineRepository;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("null")
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepo;
    @Mock
    private TicketLineRepository lineRepo;
    @Mock
    private ProductRepository productRepo;
    @Mock
    private PaymentRepository paymentRepo;
    @Mock
    private CashSessionRepository cashSessionRepo;
    @Mock
    private CashIncidentRepository cashIncidentRepo;
    @Mock
    private TableLockService tableLockService;
    @Mock
    private SalonAreaRepository salonAreaRepo;

    @InjectMocks
    private TicketService service;

    @Test
    void pay_usesRecalculatedTotalAndMarksTicketPaid() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession);
        ReflectionTestUtils.setField(ticket, "id", 1L);

        when(ticketRepo.findById(1L)).thenReturn(Optional.of(ticket));
        when(lineRepo.sumGrossByTicketId(1L)).thenReturn(1_000);
        when(lineRepo.sumNetByTicketId(1L)).thenReturn(800);
        when(lineRepo.findAllByTicketIdOrderByIdAsc(1L)).thenReturn(List.of());

        TicketResponse response = service.pay(1L);

        assertEquals(TicketStatus.PAID, response.status());
        assertEquals(1_000, response.totalCents());
        assertEquals(1_000, ticket.getTotalGrossCents());
        assertEquals(800, ticket.getTotalNetCents());
        assertTrue(ticket.getTotalVatCents() > 0);
    }

    @Test
    void reopenPaid_revertsNetPaymentsAndReopensTicket() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession, 4);
        ticket.markPaid();
        ticket.setBillRequested(true);
        ReflectionTestUtils.setField(ticket, "id", 1L);

        when(ticketRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(paymentRepo.sumByTicketGroupedByMethod(1L))
                .thenReturn(List.of(
                        new Object[]{PaymentMethod.CASH, 1_000},
                        new Object[]{PaymentMethod.CARD, 500},
                        new Object[]{PaymentMethod.BIZUM, -50}
                ));
        when(paymentRepo.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepo.findAllByTicketIdOrderByIdAsc(1L)).thenReturn(List.of());

        TicketResponse response = service.reopenPaid(1L, "Correccion de cuenta cobrada");

        assertEquals(TicketStatus.OPEN, response.status());
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertEquals(-1_000, cashSession.getExpectedCashCents());
        assertTrue(!ticket.isBillRequested());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepo, org.mockito.Mockito.times(2)).save(captor.capture());
        List<Payment> generated = captor.getAllValues();
        assertEquals(-1_000, generated.get(0).getAmountCents());
        assertEquals(-500, generated.get(1).getAmountCents());
        verify(cashIncidentRepo).save(any());
    }

    @Test
    void reopenPaid_rejectsIfTicketIsNotPaid() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession, 4);
        ReflectionTestUtils.setField(ticket, "id", 1L);

        when(ticketRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service.reopenPaid(1L, "Correccion manual"));
    }

    @Test
    void listCurrentCashSessionAllStatuses_returnsTicketsFromCurrentCashSession() {
        CashSession cashSession = new CashSession(0, "admin", null);
        ReflectionTestUtils.setField(cashSession, "id", 55L);
        Ticket open = new Ticket(cashSession, 1);
        ReflectionTestUtils.setField(open, "id", 10L);
        Ticket paid = new Ticket(cashSession, 2);
        ReflectionTestUtils.setField(paid, "id", 11L);
        paid.markPaid();

        when(cashSessionRepo.findFirstByStatusOrderByOpenedAtDesc(com.tpv.pos_service.domain.CashSessionStatus.OPEN))
                .thenReturn(Optional.of(cashSession));
        when(ticketRepo.findAllByCashSession_IdOrderByCreatedAtDesc(55L)).thenReturn(List.of(open, paid));
        when(lineRepo.findAllByTicketIdOrderByIdAsc(10L)).thenReturn(List.of());
        when(lineRepo.findAllByTicketIdOrderByIdAsc(11L)).thenReturn(List.of());

        List<TicketResponse> result = service.listCurrentCashSessionAllStatuses();

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).id());
        assertEquals("OPEN", result.get(0).status().name());
        assertEquals("PAID", result.get(1).status().name());
    }
}
