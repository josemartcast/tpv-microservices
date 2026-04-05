package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.Category;
import com.tpv.pos_service.domain.Payment;
import com.tpv.pos_service.domain.PaymentMethod;
import com.tpv.pos_service.domain.Product;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketLine;
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
import static org.mockito.ArgumentMatchers.eq;
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

        when(ticketRepo.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
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

    @Test
    void cancelIfEmpty_cancelsOpenTicketWithoutLines() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession, 7);
        ReflectionTestUtils.setField(ticket, "id", 99L);

        when(ticketRepo.findByIdForUpdate(99L)).thenReturn(Optional.of(ticket));
        when(lineRepo.countByTicket_IdAndQtyGreaterThan(99L, 0)).thenReturn(0L);
        when(lineRepo.findAllByTicketIdOrderByIdAsc(99L)).thenReturn(List.of());

        TicketResponse response = service.cancelIfEmpty(99L);

        assertEquals(TicketStatus.CANCELLED, response.status());
        assertEquals(TicketStatus.CANCELLED, ticket.getStatus());
    }

    @Test
    void cancelIfEmpty_rejectsWhenTicketHasLines() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession, 7);
        ReflectionTestUtils.setField(ticket, "id", 100L);

        when(ticketRepo.findByIdForUpdate(100L)).thenReturn(Optional.of(ticket));
        when(lineRepo.countByTicket_IdAndQtyGreaterThan(100L, 0)).thenReturn(2L);

        assertThrows(ConflictException.class, () -> service.cancelIfEmpty(100L));
        verify(lineRepo, org.mockito.Mockito.never()).findAllByTicketIdOrderByIdAsc(eq(100L));
    }

    @Test
    void cancelIfEmpty_rejectsWhenTicketIsNotOpen() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession, 7);
        ticket.markPaid();
        ReflectionTestUtils.setField(ticket, "id", 101L);

        when(ticketRepo.findByIdForUpdate(101L)).thenReturn(Optional.of(ticket));

        assertThrows(ConflictException.class, () -> service.cancelIfEmpty(101L));
        verify(lineRepo, org.mockito.Mockito.never()).countByTicket_IdAndQtyGreaterThan(eq(101L), eq(0));
    }

    @Test
    void addCombinedLine_createsSingleChargedLineUsingOnlyBasePrice() {
        CashSession cashSession = new CashSession(0, "admin", null);
        Ticket ticket = new Ticket(cashSession, 5);
        ReflectionTestUtils.setField(ticket, "id", 200L);

        Category copas = new Category("COPAS", Category.DEST_BAR);
        ReflectionTestUtils.setField(copas, "id", 11L);
        Product base = new Product("JB", 600, copas, 2100);
        ReflectionTestUtils.setField(base, "id", 21L);

        Category refrescos = new Category("REFRESCOS", Category.DEST_BAR);
        ReflectionTestUtils.setField(refrescos, "id", 12L);
        Product mixer = new Product("COCA COLA", 290, refrescos, 2100);
        ReflectionTestUtils.setField(mixer, "id", 22L);

        when(ticketRepo.findByIdForUpdate(200L)).thenReturn(Optional.of(ticket));
        when(ticketRepo.findById(200L)).thenReturn(Optional.of(ticket));
        when(productRepo.findById(21L)).thenReturn(Optional.of(base));
        when(productRepo.findById(22L)).thenReturn(Optional.of(mixer));
        when(lineRepo.findFirstByTicketIdAndProduct_IdAndProductNameSnapshotAndSentFalseOrderByIdAsc(
                200L, 21L, "JB + COCA COLA")).thenReturn(Optional.empty());
        when(lineRepo.save(any(TicketLine.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(lineRepo.sumGrossByTicketId(200L)).thenReturn(1200); // 2 x 6.00
        when(lineRepo.sumNetByTicketId(200L)).thenReturn(991);
        TicketLine snapshotLine = new TicketLine(ticket, base, 2);
        ReflectionTestUtils.setField(snapshotLine, "id", 900L);
        snapshotLine.changeProductNameSnapshot("JB + COCA COLA");
        when(lineRepo.findAllByTicketIdOrderByIdAsc(200L)).thenReturn(List.of(snapshotLine));

        TicketResponse response = service.addCombinedLine(200L, 21L, 22L, 2);

        assertEquals(1, response.lines().size());
        assertEquals("JB + COCA COLA", response.lines().get(0).productName());
        assertEquals(2, response.lines().get(0).qty());
        assertEquals(600, response.lines().get(0).unitPriceCents());
        assertEquals(1200, response.totalCents());
    }
}
