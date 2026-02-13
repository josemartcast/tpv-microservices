package com.tpv.pos_service.service;

import com.tpv.pos_service.domain.CashSession;
import com.tpv.pos_service.domain.Ticket;
import com.tpv.pos_service.domain.TicketStatus;
import com.tpv.pos_service.dto.TicketResponse;
import com.tpv.pos_service.repository.CashSessionRepository;
import com.tpv.pos_service.repository.PaymentRepository;
import com.tpv.pos_service.repository.ProductRepository;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
    private TableLockService tableLockService;

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
}
